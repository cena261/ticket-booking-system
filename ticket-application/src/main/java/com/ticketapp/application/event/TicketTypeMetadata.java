package com.ticketapp.application.event;

import com.ticketapp.domain.ticket.TicketTypeStatus;

import java.time.Instant;

public record TicketTypeMetadata(
        Long id,
        String name,
        String description,
        long price,
        Instant saleStartTime,
        Instant saleEndTime,
        TicketTypeStatus status) {
}
