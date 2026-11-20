package com.vendor.integration.order;

/**
 * Wire format of an order event as published on the {@code order-events}
 * Kafka topic. Each order can be published multiple times as it progresses
 * through its lifecycle, with {@code version} monotonically increasing per
 * {@code orderId}; consumers must apply only the highest version seen for
 * a given order and discard older/duplicate/out-of-order deliveries.
 */
public record OrderEvent(String orderId, long version, OrderStatus status) {
}
