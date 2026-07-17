package com.ticketapp.application.order;

import com.ticketapp.domain.order.Order;
import com.ticketapp.domain.order.OrderItem;
import com.ticketapp.domain.order.OrderRepository;
import com.ticketapp.domain.ticket.TicketTypeRepository;
import com.ticketapp.infrastructure.lock.DistributedLockService;
import com.ticketapp.infrastructure.stock.RedisStockCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Component
public class ReservationExpirySweeper {

    private static final String LOCK_KEY_FORMAT = "LOCK:ORDER:%d";

    private final OrderRepository orderRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final RedisStockCacheService stockCache;
    private final DistributedLockService lockService;
    private final TransactionTemplate transactionTemplate;
    private final boolean enabled;
    private final int batchSize;
    private final Duration lockWait;
    private final Duration lockLease;

    public ReservationExpirySweeper(OrderRepository orderRepository,
                                    TicketTypeRepository ticketTypeRepository,
                                    RedisStockCacheService stockCache,
                                    DistributedLockService lockService,
                                    PlatformTransactionManager transactionManager,
                                    @Value("${order.expiry.enabled}") boolean enabled,
                                    @Value("${order.expiry.batch-size}") int batchSize,
                                    @Value("${order.expiry.lock-wait}") Duration lockWait,
                                    @Value("${order.expiry.lock-lease}") Duration lockLease) {
        this.orderRepository = orderRepository;
        this.ticketTypeRepository = ticketTypeRepository;
        this.stockCache = stockCache;
        this.lockService = lockService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.enabled = enabled;
        this.batchSize = batchSize;
        this.lockWait = lockWait;
        this.lockLease = lockLease;
    }

    @Scheduled(fixedDelayString = "${order.expiry.poll-interval-ms}")
    public void sweep() {
        if (!enabled) {
            return;
        }
        sweepOnce();
    }

    public int sweepOnce() {
        List<Order> candidates = orderRepository.findExpiredPending(Instant.now(), batchSize);
        int expired = 0;
        for (Order order : candidates) {
            try {
                if (expire(order)) {
                    expired++;
                }
            } catch (RuntimeException ex) {
                log.error("failed to expire order {}, leaving PENDING for the next pass",
                        order.getId(), ex);
            }
        }
        return expired;
    }

    private boolean expire(Order order) {
        Long orderId = order.getId();
        if (order.getItems().isEmpty()) {
            log.error("order {} is PENDING past expiry with no items, skipping restock", orderId);
            return false;
        }
        OrderItem item = order.getItems().getFirst();
        Long ticketTypeId = item.getTicketTypeId();
        int quantity = item.getQuantity();

        boolean[] claimed = {false};
        boolean ran = lockService.tryRun(LOCK_KEY_FORMAT.formatted(orderId), lockWait, lockLease, () ->
                claimed[0] = Boolean.TRUE.equals(transactionTemplate.execute(status -> {
                    if (orderRepository.markExpired(orderId) == 0) {
                        return false;
                    }
                    if (ticketTypeRepository.increaseStock(ticketTypeId, quantity) == 0) {
                        throw new IllegalStateException("restock of " + quantity + " for ticket type "
                                + ticketTypeId + " matched no row, rolling back expiry of order " + orderId);
                    }
                    return true;
                })));

        if (!ran) {
            return false;
        }
        if (!claimed[0]) {
            return false;
        }

        stockCache.restore(ticketTypeId, quantity);
        log.info("expired order {} and restocked {} of ticket type {}", orderId, quantity, ticketTypeId);
        return true;
    }
}
