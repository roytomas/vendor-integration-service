package com.vendor.integration.order;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Externalized configuration for the order event Kafka consumer. Bound from
 * the {@code order.kafka.consumer.*} properties in application.yml.
 */
@Validated
@ConfigurationProperties(prefix = "order.kafka.consumer")
public class OrderEventConsumerProperties {

    private boolean enabled = true;

    @NotBlank
    private String topic = "order-events";

    @NotBlank
    private String groupId = "vendor-integration-service-order-events";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }
}
