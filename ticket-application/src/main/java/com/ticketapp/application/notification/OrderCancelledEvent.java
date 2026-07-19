package com.ticketapp.application.notification;

public record OrderCancelledEvent(String orderNumber, String recipientEmail, String reason) {
}
