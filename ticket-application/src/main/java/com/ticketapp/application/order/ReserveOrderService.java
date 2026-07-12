package com.ticketapp.application.order;

import com.ticketapp.application.exception.ErrorCode;
import com.ticketapp.domain.order.Order;
import com.ticketapp.domain.ticket.TicketType;
import com.ticketapp.domain.ticket.TicketTypeRepository;
import com.ticketapp.infrastructure.stock.RedisStockCacheService;
import org.springframework.stereotype.Service;

@Service
public class ReserveOrderService {

    private final TicketTypeRepository ticketTypeRepository;
    private final RedisStockCacheService stockCache;
    private final OrderCreationService orderCreationService;

    public ReserveOrderService(TicketTypeRepository ticketTypeRepository, RedisStockCacheService stockCache,
                               OrderCreationService orderCreationService) {
        this.ticketTypeRepository = ticketTypeRepository;
        this.stockCache = stockCache;
        this.orderCreationService = orderCreationService;
    }

    public ReserveResult reserve(Long userId, Long ticketTypeId, int quantity) {
        TicketType ticketType = ticketTypeRepository.findById(ticketTypeId).orElse(null);
        if (ticketType == null) {
            return ReserveResult.failed(ErrorCode.TICKET_TYPE_NOT_FOUND);
        }

        long gate = stockCache.deduct(ticketTypeId, quantity);
        if (gate == RedisStockCacheService.MISS) {
            stockCache.warmUp(ticketTypeId, ticketType.getStockAvailable());
            gate = stockCache.deduct(ticketTypeId, quantity);
        }
        if (gate != RedisStockCacheService.OK) {
            return ReserveResult.failed(ErrorCode.OUT_OF_STOCK);
        }

        boolean dbDecremented = false;
        try {
            if (ticketTypeRepository.decreaseStock(ticketTypeId, quantity) == 0) {
                stockCache.restore(ticketTypeId, quantity);
                return ReserveResult.failed(ErrorCode.STOCK_CONFLICT);
            }
            dbDecremented = true;

            Order order = orderCreationService.createPendingOrder(
                    userId, ticketType.getEventId(), ticketTypeId, ticketType.getPrice(), quantity);
            return ReserveResult.ok(order.getOrderNumber(), order.getExpiresAt());
        } catch (RuntimeException ex) {
            if (dbDecremented) {
                ticketTypeRepository.increaseStock(ticketTypeId, quantity);
            }
            stockCache.restore(ticketTypeId, quantity);
            return ReserveResult.failed(ErrorCode.RESERVE_FAILED);
        }
    }
}
