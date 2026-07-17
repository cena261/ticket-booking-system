package com.ticketapp.application.event;

import com.ticketapp.domain.event.EventStatus;

import java.time.Instant;
import java.util.List;

public record EventDetailView(
        Long id,
        String title,
        String description,
        String venue,
        String city,
        Instant startTime,
        Instant endTime,
        EventStatus status,
        String bannerUrl,
        List<TicketTypeView> ticketTypes) {
}
