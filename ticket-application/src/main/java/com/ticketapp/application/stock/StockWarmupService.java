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

/**
 * Seeds the Redis stock counters from MySQL. This is the only place allowed to do that, and it may
 * only run while the ticket type is quiescent.
 *
 * Redis is the source of truth for availability during a sale; MySQL is the durable ledger that
 * catches up behind it. stock_available always over-counts while requests are in flight:
 *
 *   sync reserve    - Redis is decremented before the DB decrement commits
 *   async reserve   - Redis is decremented at reserve, the DB not until the consumer settles
 *   expiry sweeper  - the DB restock commits before the Redis restore
 *
 * So a seed taken from a live DB read is always too high, and a Redis counter rebuilt from one
 * would admit buyers for tickets that do not exist. Seeding happens once, before the sale opens.
 * A missing key mid-sale is an operational fault, not a cue to re-read the database: the buy path
 * fails closed instead. Refusing to sell beats inventing stock.
 */
@Slf4j
@Service
public class StockWarmupService {

    private final TicketTypeRepository ticketTypeRepository;
    private final RedisStockCacheService stockCache;

    public StockWarmupService(TicketTypeRepository ticketTypeRepository, RedisStockCacheService stockCache) {
        this.ticketTypeRepository = ticketTypeRepository;
        this.stockCache = stockCache;
    }

    /**
     * Warms every ticket type currently on sale. Runs once the application is up, before it serves
     * traffic. SETNX-based, so an already-warm counter is never overwritten.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void warmOnSaleTicketTypes() {
        List<TicketType> onSale = ticketTypeRepository.findByStatus(TicketTypeStatus.ON_SALE);
        for (TicketType ticketType : onSale) {
            warm(ticketType.getId(), ticketType.getStockAvailable());
        }
        log.info("warmed {} on-sale ticket types into the stock cache", onSale.size());
    }

    /**
     * Warms one ticket type. Call when a ticket type is put on sale, or from a demo or load-test
     * fixture, while no reservations are in flight against it.
     */
    public void warm(Long ticketTypeId, int stockAvailable) {
        stockCache.warmUp(ticketTypeId, stockAvailable);
    }

    public void warm(Long ticketTypeId) {
        ticketTypeRepository.findById(ticketTypeId)
                .ifPresent(ticketType -> warm(ticketType.getId(), ticketType.getStockAvailable()));
    }
}
