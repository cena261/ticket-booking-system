package com.ticketapp.application.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketapp.domain.idempotency.IdempotencyKeyRepository;
import com.ticketapp.domain.order.Order;
import com.ticketapp.domain.queue.OrderQueueRepository;
import com.ticketapp.domain.ticket.TicketTypeRepository;
import com.ticketapp.infrastructure.stock.RedisStockCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class OrderSettlementConsumer {

    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final OrderQueueRepository orderQueueRepository;
    private final OrderCreationService orderCreationService;
    private final RedisStockCacheService stockCache;
    private final TransactionTemplate transactionTemplate;
    private final Duration idempotencyTtl;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OrderSettlementConsumer(IdempotencyKeyRepository idempotencyKeyRepository,
                                   TicketTypeRepository ticketTypeRepository,
                                   OrderQueueRepository orderQueueRepository,
                                   OrderCreationService orderCreationService,
                                   RedisStockCacheService stockCache,
                                   PlatformTransactionManager transactionManager,
                                   @Value("${outbox.idempotency-ttl}") Duration idempotencyTtl) {
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.ticketTypeRepository = ticketTypeRepository;
        this.orderQueueRepository = orderQueueRepository;
        this.orderCreationService = orderCreationService;
        this.stockCache = stockCache;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.idempotencyTtl = idempotencyTtl;
    }

    @KafkaListener(topics = "${kafka.topic.order-place}", groupId = "${spring.kafka.consumer.group-id}")
    public void onMessages(List<String> payloads) {
        List<OrderPlacedMessage> fresh = new ArrayList<>();
        for (String payload : payloads) {
            OrderPlacedMessage message = parse(payload);
            if (message == null) {
                continue;
            }
            if (idempotencyKeyRepository.tryInsert(message.token(), Instant.now().plus(idempotencyTtl))) {
                fresh.add(message);
            }
        }
        if (fresh.isEmpty()) {
            return;
        }

        Map<Long, List<OrderPlacedMessage>> byTicketType = fresh.stream()
                .collect(Collectors.groupingBy(OrderPlacedMessage::ticketTypeId));

        byTicketType.forEach(this::settleGroup);
    }

    private void settleGroup(Long ticketTypeId, List<OrderPlacedMessage> group) {
        int totalQuantity = group.stream().mapToInt(OrderPlacedMessage::quantity).sum();
        try {
            transactionTemplate.executeWithoutResult(status -> {
                if (ticketTypeRepository.decreaseStock(ticketTypeId, totalQuantity) == 0) {
                    throw new IllegalStateException(
                            "stock guard rejected batch of " + totalQuantity + " for ticket type " + ticketTypeId);
                }
                for (OrderPlacedMessage message : group) {
                    Order order = orderCreationService.createPendingOrder(message.userId(), message.eventId(),
                            message.ticketTypeId(), message.unitPrice(), message.quantity());
                    orderQueueRepository.markSuccess(message.token(), order.getOrderNumber());
                }
            });
        } catch (RuntimeException ex) {
            log.error("settlement failed for ticket type {} batch of {}", ticketTypeId, group.size(), ex);
            for (OrderPlacedMessage message : group) {
                orderQueueRepository.markFailed(message.token(), "settlement failed");
                stockCache.restore(message.ticketTypeId(), message.quantity());
            }
        }
    }

    private OrderPlacedMessage parse(String payload) {
        try {
            return objectMapper.readValue(payload, OrderPlacedMessage.class);
        } catch (Exception ex) {
            log.error("skipping unparseable outbox payload", ex);
            return null;
        }
    }
}
