package com.ticketapp.application.order;

import com.ticketapp.application.exception.ErrorCode;
import com.ticketapp.domain.order.Order;
import com.ticketapp.domain.ticket.TicketType;
import com.ticketapp.domain.ticket.TicketTypeRepository;
import com.ticketapp.infrastructure.stock.RedisStockCacheService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ReserveOrderService {

    private final TicketTypeRepository ticketTypeRepository;
    private final RedisStockCacheService stockCache;
    private final OrderCreationService orderCreationService;
    private final TransactionTemplate transactionTemplate;

    public ReserveOrderService(TicketTypeRepository ticketTypeRepository, RedisStockCacheService stockCache,
                               OrderCreationService orderCreationService,
                               PlatformTransactionManager transactionManager) {
        this.ticketTypeRepository = ticketTypeRepository;
        this.stockCache = stockCache;
        this.orderCreationService = orderCreationService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public ReserveResult reserve(Long userId, Long ticketTypeId, int quantity) {
        long gate = stockCache.deduct(ticketTypeId, quantity);
        if (gate == RedisStockCacheService.MISS) {
            // Fail closed. Reseeding the counter from stock_available here would over-count, because
            // the DB always lags the reservations already in flight. Warming is StockWarmupService's
            // job and only happens while the ticket type is quiescent.
            return ReserveResult.failed(ErrorCode.TICKET_TYPE_NOT_ON_SALE);
        }
        if (gate != RedisStockCacheService.OK) {
            return ReserveResult.failed(ErrorCode.OUT_OF_STOCK);
        }

        try {
            TicketType ticketType = ticketTypeRepository.findById(ticketTypeId).orElse(null);
            if (ticketType == null) {
                stockCache.restore(ticketTypeId, quantity);
                return ReserveResult.failed(ErrorCode.TICKET_TYPE_NOT_FOUND);
            }

            TicketType reserved = ticketType;
            Order order = transactionTemplate.execute(status -> {
                if (ticketTypeRepository.decreaseStock(ticketTypeId, quantity) == 0) {
                    return null;
                }
                return orderCreationService.createPendingOrder(
                        userId, reserved.getEventId(), ticketTypeId, reserved.getPrice(), quantity);
            });

            if (order == null) {
                stockCache.restore(ticketTypeId, quantity);
                return ReserveResult.failed(ErrorCode.STOCK_CONFLICT);
            }
            return ReserveResult.ok(order.getOrderNumber(), order.getExpiresAt());
        } catch (RuntimeException ex) {
            stockCache.restore(ticketTypeId, quantity);
            return ReserveResult.failed(ErrorCode.RESERVE_FAILED);
        }
    }
}
