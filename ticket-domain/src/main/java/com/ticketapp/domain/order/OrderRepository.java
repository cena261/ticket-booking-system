package com.ticketapp.domain.order;

import java.util.Optional;

public interface OrderRepository {

    Order save(Order order);

    Optional<Order> findById(Long id);

    Optional<Order> findByOrderNumber(String orderNumber);

    Optional<Order> findByPaymentRef(String paymentRef);

    long countByEventIdAndStatus(Long eventId, OrderStatus status);
}
