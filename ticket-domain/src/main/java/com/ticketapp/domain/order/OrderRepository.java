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

    /**
     * Atomically claims a PENDING order for expiry. Returns 1 for the single caller that won the
     * claim, 0 if the order already left PENDING (paid, or claimed by another sweeper). The caller
     * that receives 1 owns the restock exactly once.
     */
    int markExpired(Long id);
}
