package com.ticketapp.domain.outbox;

import java.util.List;

public interface OutboxEventRepository {

    OutboxEvent save(OutboxEvent event);

    List<OutboxEvent> findPending(int limit);

    int markPublished(List<Long> ids);

    long countByStatus(OutboxStatus status);
}
