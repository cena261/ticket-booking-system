package com.cena.ddd.domain.service;

import com.cena.ddd.domain.model.entity.TicketDetail;

public interface TicketDetailDomainService {
    TicketDetail getTicketDetailById(Long ticketId);
}
