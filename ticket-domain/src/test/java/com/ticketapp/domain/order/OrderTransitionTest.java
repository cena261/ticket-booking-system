package com.ticketapp.domain.order;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTransitionTest {

    private static Order pendingOrder() {
        Order order = new Order();
        order.setStatus(OrderStatus.PENDING);
        return order;
    }

    private static Order orderWith(OrderStatus status) {
        Order order = new Order();
        order.setStatus(status);
        return order;
    }

    @Test
    void appliesLegalTransition() {
        Order order = pendingOrder();

        order.transitionTo(OrderStatus.EXPIRED);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.EXPIRED);
    }

    @Test
    void rejectsIllegalTransition() {
        Order order = pendingOrder();

        assertThatThrownBy(() -> order.transitionTo(OrderStatus.CONFIRMED))
                .isInstanceOf(IllegalOrderTransitionException.class)
                .hasMessageContaining("PENDING")
                .hasMessageContaining("CONFIRMED");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void rejectsTransitionOutOfTerminalState() {
        for (OrderStatus terminal : new OrderStatus[]{OrderStatus.CANCELLED, OrderStatus.EXPIRED}) {
            Order order = orderWith(terminal);

            assertThatThrownBy(() -> order.transitionTo(OrderStatus.PAID))
                    .isInstanceOf(IllegalOrderTransitionException.class);

            assertThat(order.getStatus()).isEqualTo(terminal);
        }
    }

    @Test
    void rejectsExpiryOfPaidOrder() {
        Order order = orderWith(OrderStatus.PAID);

        assertThatThrownBy(() -> order.transitionTo(OrderStatus.EXPIRED))
                .isInstanceOf(IllegalOrderTransitionException.class);
    }
}
