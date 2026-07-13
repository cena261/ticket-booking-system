package com.ticketapp.order;

import com.ticketapp.application.exception.ErrorCode;
import com.ticketapp.application.order.AsyncReserveResult;
import com.ticketapp.application.order.AsyncReserveService;
import com.ticketapp.application.order.ReservationExpirySweeper;
import com.ticketapp.application.order.ReserveOrderService;
import com.ticketapp.application.order.ReserveResult;
import com.ticketapp.application.stock.StockWarmupService;
import com.ticketapp.domain.event.Event;
import com.ticketapp.domain.event.EventRepository;
import com.ticketapp.domain.order.Order;
import com.ticketapp.domain.order.OrderRepository;
import com.ticketapp.domain.order.OrderStatus;
import com.ticketapp.domain.queue.OrderQueueStatus;
import com.ticketapp.domain.ticket.TicketType;
import com.ticketapp.domain.ticket.TicketTypeRepository;
import com.ticketapp.domain.user.User;
import com.ticketapp.domain.user.UserRepository;
import com.ticketapp.domain.user.UserRole;
import com.ticketapp.infrastructure.stock.RedisStockCacheService;
import com.ticketapp.support.AbstractIntegrationTest;
import com.ticketapp.support.Fixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * A zero reservation TTL makes every reserved order expire the instant it is created, so the
 * sweeper has work to do without the test waiting out a real timeout.
 */
@SpringBootTest
@TestPropertySource(properties = "order.reservation-ttl=0s")
class ReservationExpirySweeperIT extends AbstractIntegrationTest {

    @Autowired
    ReservationExpirySweeper sweeper;

    @Autowired
    ReserveOrderService reserveOrderService;

    @Autowired
    AsyncReserveService asyncReserveService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    EventRepository eventRepository;

    @Autowired
    TicketTypeRepository ticketTypeRepository;

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    RedisStockCacheService stockCache;

    @Autowired
    StockWarmupService stockWarmup;

    private TicketType ticketType;
    private Event event;
    private User buyer;

    private TicketType seed(int stock) {
        buyer = userRepository.save(Fixtures.newUser(UserRole.USER));
        User organizer = userRepository.save(Fixtures.newUser(UserRole.ORGANIZER));
        event = eventRepository.save(Fixtures.newEvent(organizer.getId()));
        ticketType = ticketTypeRepository.save(Fixtures.newTicketType(event.getId(), 500000, stock));
        stockWarmup.warm(ticketType.getId());
        return ticketType;
    }

    private int dbStock() {
        return ticketTypeRepository.findById(ticketType.getId()).orElseThrow().getStockAvailable();
    }

    private Long redisStock() {
        return stockCache.currentStock(ticketType.getId());
    }

    @Test
    void expiresPendingOrderAndRestocksExactlyOnce() {
        seed(10);

        ReserveResult reserved = reserveOrderService.reserve(buyer.getId(), ticketType.getId(), 3);
        assertThat(reserved.success()).isTrue();
        assertThat(dbStock()).isEqualTo(7);
        assertThat(redisStock()).isEqualTo(7);

        assertThat(sweeper.sweepOnce()).isEqualTo(1);

        Order order = orderRepository.findByOrderNumber(reserved.orderNumber()).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.EXPIRED);
        assertThat(dbStock()).isEqualTo(10);
        assertThat(redisStock()).isEqualTo(10);

        // A second pass must find nothing to do. A double restock here would be an oversell.
        assertThat(sweeper.sweepOnce()).isZero();
        assertThat(dbStock()).isEqualTo(10);
        assertThat(redisStock()).isEqualTo(10);
    }

    @Test
    void neverTouchesPaidOrder() {
        seed(10);

        ReserveResult reserved = reserveOrderService.reserve(buyer.getId(), ticketType.getId(), 3);
        Order order = orderRepository.findByOrderNumber(reserved.orderNumber()).orElseThrow();
        order.transitionTo(OrderStatus.PAID);
        orderRepository.save(order);

        assertThat(sweeper.sweepOnce()).isZero();

        Order after = orderRepository.findByOrderNumber(reserved.orderNumber()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(dbStock()).isEqualTo(7);
        assertThat(redisStock()).isEqualTo(7);
    }

    @Test
    void concurrentSweepsRestockOnce() throws Exception {
        seed(10);
        reserveOrderService.reserve(buyer.getId(), ticketType.getId(), 3);

        int sweepers = 20;
        AtomicInteger totalExpired = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(sweepers);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(sweepers);

        for (int i = 0; i < sweepers; i++) {
            pool.submit(() -> {
                try {
                    startGate.await();
                    totalExpired.addAndGet(sweeper.sweepOnce());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    finished.countDown();
                }
            });
        }

        startGate.countDown();
        assertThat(finished.await(60, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        assertThat(totalExpired.get()).isEqualTo(1);
        assertThat(dbStock()).isEqualTo(10);
        assertThat(redisStock()).isEqualTo(10);
    }

    /**
     * In async mode the DB decrement is deferred to the settlement consumer, but the consumer
     * applies it in the same transaction that creates the order row. An order row therefore exists
     * only once its DB decrement has landed, so the sweeper can always restore both stores. An
     * unsettled reserve has no order row at all and is invisible here.
     */
    @Test
    void restocksSettledAsyncOrderInBothStoresExactlyOnce() {
        seed(10);

        AsyncReserveResult result = asyncReserveService.reserveAsync(buyer.getId(), ticketType.getId(), 4);
        assertThat(result.success()).isTrue();

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(asyncReserveService.status(result.token()).status())
                        .isEqualTo(OrderQueueStatus.SUCCESS));

        assertThat(dbStock()).isEqualTo(6);
        assertThat(redisStock()).isEqualTo(6);

        assertThat(sweeper.sweepOnce()).isEqualTo(1);

        // 10, not 14: the DB decrement is returned exactly once, never re-applied.
        assertThat(dbStock()).isEqualTo(10);
        assertThat(redisStock()).isEqualTo(10);
    }

    /**
     * The buy path must never rebuild a lost stock counter from stock_available. The sweeper commits
     * its DB restock before it restores Redis, so a reserve that reseeded from the DB inside that
     * window would seed a value that already contains the returned tickets, and the sweeper's restore
     * would then add them a second time. Reserve fails closed instead, and Redis never exceeds the DB.
     */
    @Test
    void reserveFailsClosedOnMissingKeyRatherThanReseedingFromDatabase() {
        seed(10);
        ReserveResult reserved = reserveOrderService.reserve(buyer.getId(), ticketType.getId(), 3);
        assertThat(reserved.success()).isTrue();
        assertThat(dbStock()).isEqualTo(7);

        // The counter is lost mid-sale: Redis restart, eviction, flush.
        stockCache.evict(ticketType.getId());

        // A reserve landing now must not reseed from the DB, even though a value is sitting there.
        ReserveResult afterLoss = reserveOrderService.reserve(buyer.getId(), ticketType.getId(), 1);
        assertThat(afterLoss.success()).isFalse();
        assertThat(afterLoss.errorCode()).isEqualTo(ErrorCode.TICKET_TYPE_NOT_ON_SALE);
        assertThat(redisStock()).isNull();
        assertThat(dbStock()).isEqualTo(7);

        // The sweeper still returns the DB stock. Redis stays absent rather than being over-credited.
        assertThat(sweeper.sweepOnce()).isEqualTo(1);
        assertThat(dbStock()).isEqualTo(10);
        assertThat(redisStock()).isNull();

        // A warm while quiescent now reads a DB value that is finally the truth.
        stockWarmup.warm(ticketType.getId());
        assertThat(redisStock()).isEqualTo(10);
    }

    /**
     * Sweeps run concurrently with a live reserve storm. The DB CHECK constraint
     * (stock_available <= stock_initial) means a double restock surfaces as a failed UPDATE rather
     * than silent corruption, but the final equality is the real assertion.
     */
    @Test
    void holdsStockInvariantUnderConcurrentSweepAndReserve() throws Exception {
        int stock = 50;
        int buyers = 300;
        seed(stock);

        AtomicInteger reserved = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(60);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(buyers);

        for (int i = 0; i < buyers; i++) {
            pool.submit(() -> {
                try {
                    startGate.await();
                    if (reserveOrderService.reserve(buyer.getId(), ticketType.getId(), 1).success()) {
                        reserved.incrementAndGet();
                    }
                    // Every buyer thread also sweeps, so expiry races reservation continuously.
                    sweeper.sweepOnce();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    finished.countDown();
                }
            });
        }

        startGate.countDown();
        assertThat(finished.await(120, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        // Drain whatever the racing sweeps left behind.
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() ->
                assertThat(sweeper.sweepOnce()).isZero());

        long expired = orderRepository.countByEventIdAndStatus(event.getId(), OrderStatus.EXPIRED);
        long pending = orderRepository.countByEventIdAndStatus(event.getId(), OrderStatus.PENDING);

        // Every order that was reserved was expired exactly once, and every ticket came back.
        assertThat(pending).isZero();
        assertThat(expired).isEqualTo(reserved.get());
        assertThat(dbStock()).isEqualTo(stock);
        assertThat(redisStock()).isEqualTo((long) stock);
    }
}
