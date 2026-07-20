package com.ticketapp.domain.order;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OrderRepository {

    Order save(Order order);

    Optional<Order> findById(Long id);

    Optional<Order> findByOrderNumber(String orderNumber);

    Optional<Order> findByPaymentRef(String paymentRef);

    long countByEventIdAndStatus(Long eventId, OrderStatus status);

    List<Order> findExpiredPending(Instant now, int limit);

    int markExpired(Long id);
}
