package com.ticketapp.infrastructure.persistence.ticket;

import com.ticketapp.domain.ticket.TicketType;
import com.ticketapp.domain.ticket.TicketTypeRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketTypeJpaRepository extends TicketTypeRepository, JpaRepository<TicketType, Long> {
}
