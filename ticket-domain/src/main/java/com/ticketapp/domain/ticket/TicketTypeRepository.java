package com.ticketapp.domain.ticket;

import java.util.List;
import java.util.Optional;

public interface TicketTypeRepository {

    TicketType save(TicketType ticketType);

    Optional<TicketType> findById(Long id);

    List<TicketType> findByEventId(Long eventId);

    List<TicketType> findByStatus(TicketTypeStatus status);

    int decreaseStock(Long id, int quantity);

    int increaseStock(Long id, int quantity);
}
