package com.cena.ddd.application.service.ticket.impl;

import com.cena.ddd.application.service.ticket.TicketDetailAppService;
import com.cena.ddd.application.service.ticket.cache.TicketDetailCacheService;
import com.cena.ddd.domain.model.entity.TicketDetail;
import com.cena.ddd.domain.service.TicketDetailDomainService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class TicketDetailAppServiceImpl  implements TicketDetailAppService {
    @Autowired
    private TicketDetailDomainService ticketDetailDomainService;

    // CALL CACHE
    @Autowired
    private TicketDetailCacheService ticketDetailCacheService;

    @Override
    public TicketDetail getTicketDetailById(Long ticketId) {
        log.info("Implement Application : {}", ticketId);
//        return ticketDetailDomainService.getTicketDetailById(ticketId);
//        return ticketDetailCacheService.getTicketDefaultCacheNormal(ticketId, System.currentTimeMillis());
        return ticketDetailCacheService.getTicketDefaultCacheVip(ticketId, System.currentTimeMillis());
    }
}
