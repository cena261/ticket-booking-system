package com.cena.ddd.application.service.ticket;

import com.cena.ddd.domain.model.entity.TicketDetail;

public interface TicketDetailAppService {
    TicketDetail getTicketDetailById(Long ticketId);
}
