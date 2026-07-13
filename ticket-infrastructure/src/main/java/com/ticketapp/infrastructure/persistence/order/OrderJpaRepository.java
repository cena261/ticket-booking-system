package com.ticketapp.infrastructure.persistence.order;

import com.ticketapp.domain.order.Order;
import com.ticketapp.domain.order.OrderRepository;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
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

    /**
     * Two steps on purpose. Applying a limit to a query that also fetches the items collection
     * makes Hibernate page in memory, so a large expiry backlog would be loaded in full. Selecting
     * the ids first keeps the limit in SQL and lets the (status, expires_at) index drive it.
     */
    @Override
    default List<Order> findExpiredPending(Instant now, int limit) {
        List<Long> ids = findExpiredPendingIds(now, Limit.of(limit));
        return ids.isEmpty() ? List.of() : findAllWithItemsByIdIn(ids);
    }

    @Query("SELECT o.id FROM Order o WHERE o.status = com.ticketapp.domain.order.OrderStatus.PENDING "
            + "AND o.expiresAt <= :now ORDER BY o.expiresAt ASC")
    List<Long> findExpiredPendingIds(@Param("now") Instant now, Limit limit);

    @EntityGraph(attributePaths = "items")
    List<Order> findAllWithItemsByIdIn(Collection<Long> ids);

    @Override
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Order o SET o.status = com.ticketapp.domain.order.OrderStatus.EXPIRED "
            + "WHERE o.id = :id AND o.status = com.ticketapp.domain.order.OrderStatus.PENDING")
    int markExpired(@Param("id") Long id);
}
