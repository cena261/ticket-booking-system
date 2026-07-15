package com.ticketapp.application.stock;

import com.ticketapp.domain.ticket.TicketType;
import com.ticketapp.domain.ticket.TicketTypeRepository;
import com.ticketapp.domain.ticket.TicketTypeStatus;
import com.ticketapp.infrastructure.stock.RedisStockCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class StockWarmupService {

    private final TicketTypeRepository ticketTypeRepository;
    private final RedisStockCacheService stockCache;

    public StockWarmupService(TicketTypeRepository ticketTypeRepository, RedisStockCacheService stockCache) {
        this.ticketTypeRepository = ticketTypeRepository;
        this.stockCache = stockCache;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmOnSaleTicketTypes() {
        List<TicketType> onSale = ticketTypeRepository.findByStatus(TicketTypeStatus.ON_SALE);
        for (TicketType ticketType : onSale) {
            warm(ticketType.getId(), ticketType.getStockAvailable());
        }
        log.info("warmed {} on-sale ticket types into the stock cache", onSale.size());
    }

    public void warm(Long ticketTypeId, int stockAvailable) {
        stockCache.warmUp(ticketTypeId, stockAvailable);
    }

    public void warm(Long ticketTypeId) {
        ticketTypeRepository.findById(ticketTypeId)
                .ifPresent(ticketType -> warm(ticketType.getId(), ticketType.getStockAvailable()));
    }
}
