package com.vendor.integration.order;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverRecord;
import reactor.util.retry.Retry;

import java.time.Duration;

/**
 * Consumes {@link OrderEvent}s from the {@code order-events} topic and
 * ensures that, per {@code orderId}, only the current/highest version is
 * applied - older, duplicate or out-of-order deliveries are detected by
 * {@link OrderEventService} and discarded. Records are processed one at a
 * time per subscription (preserving per-partition ordering) and the offset
 * is only acknowledged once an event has been accepted or recognized as
 * stale, so a processing failure causes the record to be redelivered rather
 * than silently lost.
 */
@Component
@ConditionalOnProperty(name = "order.kafka.consumer.enabled", havingValue = "true", matchIfMissing = true)
public class OrderEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);

    private final KafkaReceiver<String, OrderEvent> kafkaReceiver;
    private final OrderEventService orderEventService;

    private Disposable subscription;

    public OrderEventConsumer(KafkaReceiver<String, OrderEvent> kafkaReceiver,
                               OrderEventService orderEventService) {
        this.kafkaReceiver = kafkaReceiver;
        this.orderEventService = orderEventService;
    }

    @PostConstruct
    public void start() {
        subscription = kafkaReceiver.receive()
                .concatMap(this::process)
                .retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(1))
                        .maxBackoff(Duration.ofSeconds(30))
                        .doBeforeRetry(signal -> log.error("Order event consumer failed, restarting subscription", signal.failure())))
                .subscribe(
                        record -> { },
                        error -> log.error("Order event consumer terminated unexpectedly", error)
                );
    }

    @PreDestroy
    public void stop() {
        if (subscription != null && !subscription.isDisposed()) {
            subscription.dispose();
        }
    }

    private Mono<Void> process(ReceiverRecord<String, OrderEvent> record) {
        OrderEvent event = record.value();
        return orderEventService.applyIfCurrentVersion(event)
                .doOnNext(applied -> {
                    if (applied) {
                        log.info("Consumed order event: orderId={}, version={}, status={}",
                                event.orderId(), event.version(), event.status());
                    } else {
                        log.info("Skipped stale/duplicate order event: orderId={}, version={}",
                                event.orderId(), event.version());
                    }
                    // Only acknowledge once the event has been durably accepted or
                    // recognized as stale; a failure below leaves the offset
                    // uncommitted so the record is redelivered after resubscription.
                    record.receiverOffset().acknowledge();
                })
                .doOnError(e -> log.error("Failed to process order event for order {}: {}",
                        event.orderId(), e.toString()))
                .then();
    }
}
