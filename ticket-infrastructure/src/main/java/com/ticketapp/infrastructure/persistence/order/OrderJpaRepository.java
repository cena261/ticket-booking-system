package com.ticketapp.infrastructure.persistence.order;

import com.ticketapp.domain.order.Order;
import com.ticketapp.domain.order.OrderRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderJpaRepository extends OrderRepository, JpaRepository<Order, Long> {

    @Override
    @EntityGraph(attributePaths = "items")
    Optional<Order> findById(Long id);

    @Override
    @EntityGraph(attributePaths = "items")
    Optional<Order> findByOrderNumber(String orderNumber);

    @Override
    @EntityGraph(attributePaths = "items")
    Optional<Order> findByPaymentRef(String paymentRef);
}
