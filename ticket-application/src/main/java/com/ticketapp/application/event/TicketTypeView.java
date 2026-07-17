package com.ticketapp.application.event;

import com.ticketapp.domain.ticket.TicketTypeStatus;

import java.time.Instant;

public record TicketTypeView(
        Long id,
        String name,
        String description,
        long price,
        Instant saleStartTime,
        Instant saleEndTime,
        TicketTypeStatus status,
        int stockAvailable) {
}
