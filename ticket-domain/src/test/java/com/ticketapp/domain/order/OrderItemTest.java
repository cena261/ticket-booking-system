package com.ticketapp.domain.order;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderItemTest {

    @Test
    void createComputesSubtotal() {
        OrderItem item = OrderItem.create(10L, 500000, 3);

        assertThat(item.getSubtotal()).isEqualTo(1500000);
        assertThat(item.getQuantity()).isEqualTo(3);
        assertThat(item.getUnitPrice()).isEqualTo(500000);
    }

    @Test
    void createRejectsNegativeUnitPrice() {
        assertThatThrownBy(() -> OrderItem.create(10L, -1, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createRejectsNonPositiveQuantity() {
        assertThatThrownBy(() -> OrderItem.create(10L, 100, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
