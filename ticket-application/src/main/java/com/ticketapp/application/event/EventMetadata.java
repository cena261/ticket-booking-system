package com.ticketapp.application.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ticketapp.domain.event.EventStatus;

import java.time.Instant;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EventMetadata(
        boolean exists,
        Long id,
        String title,
        String description,
        String venue,
        String city,
        Instant startTime,
        Instant endTime,
        EventStatus status,
        String bannerUrl,
        List<TicketTypeMetadata> ticketTypes) {

    public static EventMetadata notFound() {
        return new EventMetadata(false, null, null, null, null, null, null, null, null, null, List.of());
    }
}
