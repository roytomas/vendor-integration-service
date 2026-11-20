package com.vendor.integration.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Instant;
import java.util.HashMap;
import java.time.Duration;

/**
 * Applies the "current version wins" rule for incoming {@link OrderEvent}s:
 * for a given {@code orderId}, only the event carrying the highest
 * {@code version} seen so far is applied; events with an older or already
 * applied version are recognized as stale/duplicate/out-of-order and
 * skipped. The last-applied version is persisted via
 * {@link OrderEventStateRepository} so the rule holds across restarts and
 * consumer instances.
 */
@Service
public class OrderEventService {

    private static final Logger log = LoggerFactory.getLogger(OrderEventService.class);

    private final OrderEventStateRepository repository;

    public OrderEventService(OrderEventStateRepository repository) {
        this.repository = repository;
    }

    /**
     * Applies {@code event} if and only if it carries a newer version than
     * whatever is currently recorded for its order.
     *
     * @return a {@link Mono} of {@code true} if the event was applied,
     * {@code false} if it was discarded as stale/duplicate.
     */
    public Mono<Boolean> applyIfCurrentVersion(OrderEvent event) {
        return repository.findByOrderId(event.orderId())
                .flatMap(existing -> applyIfNewer(event, existing))
                .switchIfEmpty(Mono.defer(() -> insert(event)))
                .onErrorResume(DataIntegrityViolationException.class,
                        e -> repository.findByOrderId(event.orderId())
                                .flatMap(existing -> applyIfNewer(event, existing)))
                .retryWhen(Retry.backoff(2, Duration.ofMillis(20))
                        .filter(DataIntegrityViolationException.class::isInstance));
    }

    private Mono<Boolean> applyIfNewer(OrderEvent event, OrderEventState existing) {
        if (event.version() <= existing.getVersion()) {
            log.debug("Discarding order event for order {} with version {}, current version is {}",
                    event.orderId(), event.version(), existing.getVersion());
            return Mono.just(false);
        }
        existing.setVersion(event.version());
        existing.setStatus(event.status());
        existing.setUpdatedAt(Instant.now());
        return repository.save(existing)
                .doOnNext(saved -> log.info("Applied order event for order {}: version {} -> status {}",
                        saved.getOrderId(), saved.getVersion(), saved.getStatus()))
                .thenReturn(true);
    }

    private Mono<Boolean> insert(OrderEvent event) {
        OrderEventState state = new OrderEventState();
        state.setOrderId(event.orderId());
        state.setVersion(event.version());
        state.setStatus(event.status());
        state.setUpdatedAt(Instant.now());
        return repository.save(state)
                .doOnNext(saved -> log.info("Applied first order event for order {}: version {} -> status {}",
                        saved.getOrderId(), saved.getVersion(), saved.getStatus()))
                .thenReturn(true);
    }
}
