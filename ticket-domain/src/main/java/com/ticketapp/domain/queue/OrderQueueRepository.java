package com.ticketapp.domain.queue;

import java.util.Optional;

public interface OrderQueueRepository {

    OrderQueue save(OrderQueue orderQueue);

    Optional<OrderQueue> findByToken(String token);

    int markSuccess(String token, String orderNumber);

    int markFailed(String token, String message);
}
