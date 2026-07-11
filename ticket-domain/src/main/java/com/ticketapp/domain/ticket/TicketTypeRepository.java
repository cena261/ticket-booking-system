package com.ticketapp.domain.ticket;

import java.util.List;
import java.util.Optional;

public interface TicketTypeRepository {

    TicketType save(TicketType ticketType);

    Optional<TicketType> findById(Long id);

    List<TicketType> findByEventId(Long eventId);
}
