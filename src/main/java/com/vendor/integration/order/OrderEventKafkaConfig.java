package com.vendor.integration.order;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.reactive.ReactiveKafkaConsumerTemplate;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import reactor.kafka.receiver.ReceiverOptions;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Wires up the reactive Kafka consumer for {@link OrderEvent}s, in keeping
 * with the rest of this service being built on WebFlux/R2DBC: records are
 * received and acknowledged through Reactor Kafka rather than a blocking
 * {@code @KafkaListener} container.
 */
@Configuration
@EnableConfigurationProperties(OrderEventConsumerProperties.class)
public class OrderEventKafkaConfig {

    @Bean
    public ReceiverOptions<String, OrderEvent> orderEventReceiverOptions(KafkaProperties kafkaProperties,
                                                                          OrderEventConsumerProperties properties) {
        Map<String, Object> consumerProps = kafkaProperties.buildConsumerProperties(null);
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, properties.getGroupId());
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        consumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, OrderEvent.class.getPackageName());
        consumerProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, OrderEvent.class.getName());
        consumerProps.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);

        return ReceiverOptions.<String, OrderEvent>create(consumerProps)
                .subscription(List.of(properties.getTopic()))
                .commitInterval(Duration.ofSeconds(5))
                .commitBatchSize(50);
    }

    @Bean
    public ReactiveKafkaConsumerTemplate<String, OrderEvent> orderEventConsumerTemplate(
            ReceiverOptions<String, OrderEvent> orderEventReceiverOptions) {
        return new ReactiveKafkaConsumerTemplate<>(orderEventReceiverOptions);
    }
}
