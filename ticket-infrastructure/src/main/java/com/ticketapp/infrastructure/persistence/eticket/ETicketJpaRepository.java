package com.ticketapp.infrastructure.persistence.eticket;

import com.ticketapp.domain.eticket.ETicket;
import com.ticketapp.domain.eticket.ETicketRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ETicketJpaRepository extends ETicketRepository, JpaRepository<ETicket, Long> {
}
