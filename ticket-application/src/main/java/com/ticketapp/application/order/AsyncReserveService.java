package com.ticketapp.application.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketapp.application.exception.AppException;
import com.ticketapp.application.exception.ErrorCode;
import com.ticketapp.application.observability.BuyPathMetrics;
import com.ticketapp.domain.outbox.OutboxEvent;
import com.ticketapp.domain.outbox.OutboxEventRepository;
import com.ticketapp.domain.queue.OrderQueue;
import com.ticketapp.domain.queue.OrderQueueRepository;
import com.ticketapp.domain.ticket.TicketType;
import com.ticketapp.domain.ticket.TicketTypeRepository;
import com.ticketapp.infrastructure.stock.RedisStockCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
public class AsyncReserveService {

    public static final String EVENT_TYPE_ORDER_PLACED = "ORDER_PLACED";

    private static final String MODE = "async";

    private final TicketTypeRepository ticketTypeRepository;
    private final RedisStockCacheService stockCache;
    private final OrderQueueRepository orderQueueRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final TransactionTemplate transactionTemplate;
    private final BuyPathMetrics metrics;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AsyncReserveService(TicketTypeRepository ticketTypeRepository, RedisStockCacheService stockCache,
                               OrderQueueRepository orderQueueRepository, OutboxEventRepository outboxEventRepository,
                               PlatformTransactionManager transactionManager, BuyPathMetrics metrics) {
        this.ticketTypeRepository = ticketTypeRepository;
        this.stockCache = stockCache;
        this.orderQueueRepository = orderQueueRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.metrics = metrics;
    }

    public AsyncReserveResult reserveAsync(Long userId, Long ticketTypeId, int quantity) {
        long startNanos = System.nanoTime();
        String outcome = "error";
        try {
            long gate = stockCache.deduct(ticketTypeId, quantity);
            if (gate == RedisStockCacheService.MISS) {
                outcome = "not_on_sale";
                return AsyncReserveResult.failed(ErrorCode.TICKET_TYPE_NOT_ON_SALE);
            }
            if (gate != RedisStockCacheService.OK) {
                outcome = "out_of_stock";
                metrics.oversellPrevented();
                return AsyncReserveResult.failed(ErrorCode.OUT_OF_STOCK);
            }

            TicketType ticketType = ticketTypeRepository.findById(ticketTypeId).orElse(null);
            if (ticketType == null) {
                stockCache.restore(ticketTypeId, quantity);
                outcome = "not_found";
                return AsyncReserveResult.failed(ErrorCode.TICKET_TYPE_NOT_FOUND);
            }

            String token = "MQ-" + UUID.randomUUID();
            OrderPlacedMessage message = new OrderPlacedMessage(token, userId, ticketTypeId,
                    ticketType.getEventId(), quantity, ticketType.getPrice(), Instant.now().toEpochMilli());
            String payload = objectMapper.writeValueAsString(message);

            transactionTemplate.executeWithoutResult(status -> {
                orderQueueRepository.save(OrderQueue.pending(token, userId, ticketTypeId, quantity));
                outboxEventRepository.save(OutboxEvent.pending(token, EVENT_TYPE_ORDER_PLACED, payload));
            });

            outcome = "success";
            return AsyncReserveResult.ok(token);
        } catch (Exception ex) {
            stockCache.restore(ticketTypeId, quantity);
            outcome = "error";
            log.error("async reserve failed for userId={} ticketTypeId={} quantity={}, stock restored",
                    userId, ticketTypeId, quantity, ex);
            return AsyncReserveResult.failed(ErrorCode.RESERVE_FAILED);
        } finally {
            metrics.recordReserve(MODE, outcome, startNanos);
            log.info("reserve mode={} outcome={} userId={} ticketTypeId={} quantity={}",
                    MODE, outcome, userId, ticketTypeId, quantity);
        }
    }

    public OrderStatusView status(String token) {
        return orderQueueRepository.findByToken(token)
                .map(queued -> new OrderStatusView(queued.getToken(), queued.getStatus(),
                        queued.getOrderNumber(), queued.getMessage()))
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_TOKEN_NOT_FOUND));
    }
}
