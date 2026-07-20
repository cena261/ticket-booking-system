package com.ticketapp.application.order;

public record OrderPlacedMessage(
        String token,
        Long userId,
        Long ticketTypeId,
        Long eventId,
        int quantity,
        long unitPrice,
        long timestamp) {
}
