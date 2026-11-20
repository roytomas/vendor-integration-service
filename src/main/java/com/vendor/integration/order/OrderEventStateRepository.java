package com.vendor.integration.order;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

/**
 * Reactive persistence gateway for the last-applied-version marker of each
 * order, used by {@link OrderEventService} to gate consumption of
 * {@link OrderEvent}s to the current/highest version per order.
 */
public interface OrderEventStateRepository extends ReactiveCrudRepository<OrderEventState, Long> {

    Mono<OrderEventState> findByOrderId(String orderId);
}
