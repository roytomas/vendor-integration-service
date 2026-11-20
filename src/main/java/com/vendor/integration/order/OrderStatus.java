package com.vendor.integration.order;

/**
 * Lifecycle status carried by an {@link OrderEvent}. Values should be kept
 * in sync with whatever the upstream order system actually publishes.
 */
public enum OrderStatus {
    CREATED,
    CONFIRMED,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    CANCELLED,
    UNKNOWN
}
