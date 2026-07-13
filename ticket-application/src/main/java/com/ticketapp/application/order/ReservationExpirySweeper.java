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

/**
 * Auto-cancels reservations that were never paid and returns their stock.
 *
 * This is the return leg of the zero-oversell guarantee: the buy path proves we never sell more
 * than stock on the way down, this sweeper proves we never hand back more than we took. A single
 * double-restock is an oversell.
 *
 * Exactly-once restock rests on the conditional UPDATE in {@link OrderRepository#markExpired},
 * not on the lock. Only the caller whose UPDATE matches a still-PENDING row restocks; a payment
 * that lands first, or a second sweeper, matches zero rows and does nothing. The Redisson lock
 * sits on top purely to stop instances duplicating the work.
 *
 * Restock order is DB then Redis, and it is deliberate. Both writes cannot be one transaction, so
 * a crash can land between them. Restocking the DB first leaves Redis lower than the DB, and since
 * Redis is the admission gate a low Redis admits fewer buyers than we have tickets: the tickets go
 * unsold. The failure leans to undersell, never oversell.
 */
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

    /**
     * One sweep pass. Returns the number of orders this call actually expired and restocked.
     * Ignores the enabled flag so tests can drive a pass directly.
     */
    public int sweepOnce() {
        List<Order> candidates = orderRepository.findExpiredPending(Instant.now(), batchSize);
        int expired = 0;
        for (Order order : candidates) {
            // One order must never stall the queue. Candidates are ordered oldest-first, so an
            // order that throws every pass would otherwise sit at the head of every batch and
            // block every order behind it forever.
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
        // Single ticket type per order in v1, so exactly one item.
        OrderItem item = order.getItems().getFirst();
        Long ticketTypeId = item.getTicketTypeId();
        int quantity = item.getQuantity();

        boolean[] claimed = {false};
        boolean ran = lockService.tryRun(LOCK_KEY_FORMAT.formatted(orderId), lockWait, lockLease, () ->
                claimed[0] = Boolean.TRUE.equals(transactionTemplate.execute(status -> {
                    // The order row is claimed and the DB stock returned in one transaction, so an
                    // order can never be marked EXPIRED without its stock coming back.
                    if (orderRepository.markExpired(orderId) == 0) {
                        return false;
                    }
                    if (ticketTypeRepository.increaseStock(ticketTypeId, quantity) == 0) {
                        // Nothing was returned to the DB, so the claim must not stand. Rolling back
                        // keeps the order PENDING rather than leaving it EXPIRED with its stock
                        // stranded, and stops the Redis restore below from lifting Redis above DB.
                        throw new IllegalStateException("restock of " + quantity + " for ticket type "
                                + ticketTypeId + " matched no row, rolling back expiry of order " + orderId);
                    }
                    return true;
                })));

        if (!ran) {
            // Another instance holds the lock and is expiring this order. It will be picked up on a
            // later pass if that instance failed.
            return false;
        }
        if (!claimed[0]) {
            // Order left PENDING before we claimed it: paid, or already swept. Not ours to restock.
            return false;
        }

        stockCache.restore(ticketTypeId, quantity);
        log.info("expired order {} and restocked {} of ticket type {}", orderId, quantity, ticketTypeId);
        return true;
    }
}
