package com.vendor.integration.order;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the "current version wins" rule applied by {@link OrderEventService}:
 * an order event is only applied when its version is strictly newer than the
 * last one recorded for that order; older, duplicate or out-of-order
 * deliveries are discarded.
 */
class OrderEventServiceTest {

    @Mock
    private OrderEventStateRepository repository;

    private OrderEventService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new OrderEventService(repository);
    }

    @Test
    void appliesFirstEventSeenForAnOrder() {
        OrderEvent event = new OrderEvent("ORD-1", 1, OrderStatus.CREATED);

        when(repository.findByOrderId("ORD-1")).thenReturn(Mono.empty());
        when(repository.save(any(OrderEventState.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(service.applyIfCurrentVersion(event))
                .expectNext(true)
                .verifyComplete();

        verify(repository).save(any(OrderEventState.class));
    }

    @Test
    void appliesEventWithNewerVersionThanCurrent() {
        OrderEvent event = new OrderEvent("ORD-1", 3, OrderStatus.SHIPPED);

        OrderEventState existing = new OrderEventState();
        existing.setOrderId("ORD-1");
        existing.setVersion(2);
        existing.setStatus(OrderStatus.PROCESSING);

        when(repository.findByOrderId("ORD-1")).thenReturn(Mono.just(existing));
        when(repository.save(any(OrderEventState.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(service.applyIfCurrentVersion(event))
                .expectNext(true)
                .verifyComplete();

        verify(repository).save(existing);
        assertThat(existing.getVersion()).isEqualTo(3);
        assertThat(existing.getStatus()).isEqualTo(OrderStatus.SHIPPED);
    }

    @Test
    void discardsEventWithOlderVersionThanCurrent() {
        OrderEvent staleEvent = new OrderEvent("ORD-1", 1, OrderStatus.CREATED);

        OrderEventState existing = new OrderEventState();
        existing.setOrderId("ORD-1");
        existing.setVersion(5);
        existing.setStatus(OrderStatus.DELIVERED);

        when(repository.findByOrderId("ORD-1")).thenReturn(Mono.just(existing));

        StepVerifier.create(service.applyIfCurrentVersion(staleEvent))
                .expectNext(false)
                .verifyComplete();

        verify(repository, never()).save(any(OrderEventState.class));
        assertThat(existing.getVersion()).isEqualTo(5);
    }

    @Test
    void discardsDuplicateEventWithSameVersionAsCurrent() {
        OrderEvent duplicateEvent = new OrderEvent("ORD-1", 2, OrderStatus.PROCESSING);

        OrderEventState existing = new OrderEventState();
        existing.setOrderId("ORD-1");
        existing.setVersion(2);
        existing.setStatus(OrderStatus.PROCESSING);

        when(repository.findByOrderId("ORD-1")).thenReturn(Mono.just(existing));

        StepVerifier.create(service.applyIfCurrentVersion(duplicateEvent))
                .expectNext(false)
                .verifyComplete();

        verify(repository, never()).save(any(OrderEventState.class));
    }
}
