package com.vendor.integration.order;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

/**
 * Tracks the highest {@link OrderEvent} version applied so far for a given
 * order, so that {@link OrderEventService} can detect and discard stale,
 * duplicate or out-of-order deliveries of the same order.
 */
@Table("order_event_state")
public class OrderEventState {

    @Id
    private Long id;

    private String orderId;

    private long version;

    private OrderStatus status;

    private Instant updatedAt;

    public OrderEventState() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
