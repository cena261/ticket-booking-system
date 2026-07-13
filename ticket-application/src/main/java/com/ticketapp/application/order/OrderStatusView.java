package com.ticketapp.application.order;

import com.ticketapp.domain.queue.OrderQueueStatus;

public record OrderStatusView(String token, OrderQueueStatus status, String orderNumber, String message) {
}
