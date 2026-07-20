package com.ticketapp.infrastructure.persistence.queue;

import com.ticketapp.domain.queue.OrderQueue;
import com.ticketapp.domain.queue.OrderQueueRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface OrderQueueJpaRepository extends OrderQueueRepository, JpaRepository<OrderQueue, Long> {

    @Override
    @Transactional
    @Modifying
    @Query(value = "UPDATE order_queue SET status = 1, order_number = :orderNumber, updated_at = NOW(6) "
            + "WHERE token = :token AND status = 0", nativeQuery = true)
    int markSuccess(@Param("token") String token, @Param("orderNumber") String orderNumber);

    @Override
    @Transactional
    @Modifying
    @Query(value = "UPDATE order_queue SET status = 2, message = :message, updated_at = NOW(6) "
            + "WHERE token = :token AND status = 0", nativeQuery = true)
    int markFailed(@Param("token") String token, @Param("message") String message);
}
