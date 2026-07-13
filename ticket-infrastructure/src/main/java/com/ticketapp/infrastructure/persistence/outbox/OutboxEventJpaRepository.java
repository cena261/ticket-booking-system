package com.ticketapp.infrastructure.persistence.outbox;

import com.ticketapp.domain.outbox.OutboxEvent;
import com.ticketapp.domain.outbox.OutboxEventRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface OutboxEventJpaRepository extends OutboxEventRepository, JpaRepository<OutboxEvent, Long> {

    @Override
    @Query(value = "SELECT * FROM outbox_event WHERE status = 0 ORDER BY created_at LIMIT :limit",
            nativeQuery = true)
    List<OutboxEvent> findPending(@Param("limit") int limit);

    @Override
    @Transactional
    @Modifying
    @Query(value = "UPDATE outbox_event SET status = 1, published_at = NOW(6) WHERE id IN (:ids) AND status = 0",
            nativeQuery = true)
    int markPublished(@Param("ids") List<Long> ids);
}
