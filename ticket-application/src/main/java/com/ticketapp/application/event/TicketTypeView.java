package com.ticketapp.application.event;

import com.ticketapp.domain.ticket.TicketTypeStatus;

import java.time.Instant;

/**
 * Composed response: metadata plus a live stock reading. Never cache this record as a whole —
 * stockAvailable mutates thousands of times per second during a flash sale. Phase 8c caches only
 * the metadata fields, in a separate DTO that carries no stock at all.
 */
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
