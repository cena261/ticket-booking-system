package com.ticketapp.domain.order;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderStatusTest {

    @Test
    void pendingCanMoveToPaidCancelledOrExpired() {
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.PAID)).isTrue();
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.EXPIRED)).isTrue();
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.CONFIRMED)).isFalse();
    }

    @Test
    void paidCanMoveToConfirmedOrCancelledOnly() {
        assertThat(OrderStatus.PAID.canTransitionTo(OrderStatus.CONFIRMED)).isTrue();
        assertThat(OrderStatus.PAID.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
        assertThat(OrderStatus.PAID.canTransitionTo(OrderStatus.EXPIRED)).isFalse();
    }

    @Test
    void terminalStatesCannotTransition() {
        assertThat(OrderStatus.CANCELLED.canTransitionTo(OrderStatus.PAID)).isFalse();
        assertThat(OrderStatus.EXPIRED.canTransitionTo(OrderStatus.PENDING)).isFalse();
    }
}
