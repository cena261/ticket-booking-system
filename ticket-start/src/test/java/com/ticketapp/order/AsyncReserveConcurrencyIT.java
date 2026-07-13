package com.ticketapp.order;

import com.ticketapp.application.exception.ErrorCode;
import com.ticketapp.application.order.AsyncReserveResult;
import com.ticketapp.application.order.AsyncReserveService;
import com.ticketapp.domain.event.Event;
import com.ticketapp.domain.event.EventRepository;
import com.ticketapp.domain.order.OrderRepository;
import com.ticketapp.domain.order.OrderStatus;
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

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
class AsyncReserveConcurrencyIT extends AbstractIntegrationTest {

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

    @Test
    void neverOversellsAndSettlesEveryAcceptedToken() throws Exception {
        int stock = 100;
        int buyers = 500;

        User buyer = userRepository.save(Fixtures.newUser(UserRole.USER));
        User organizer = userRepository.save(Fixtures.newUser(UserRole.ORGANIZER));
        Event event = eventRepository.save(Fixtures.newEvent(organizer.getId()));
        TicketType ticketType = ticketTypeRepository.save(Fixtures.newTicketType(event.getId(), 500000, stock));

        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger outOfStock = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(100);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(buyers);

        for (int i = 0; i < buyers; i++) {
            pool.submit(() -> {
                try {
                    startGate.await();
                    AsyncReserveResult result = asyncReserveService.reserveAsync(buyer.getId(), ticketType.getId(), 1);
                    if (result.success()) {
                        accepted.incrementAndGet();
                    } else if (result.errorCode() == ErrorCode.OUT_OF_STOCK) {
                        outOfStock.incrementAndGet();
                    }
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

        assertThat(accepted.get()).isEqualTo(stock);
        assertThat(outOfStock.get()).isEqualTo(buyers - stock);
        assertThat(stockCache.currentStock(ticketType.getId())).isZero();

        await().atMost(Duration.ofSeconds(120)).untilAsserted(() -> {
            assertThat(orderRepository.countByEventIdAndStatus(event.getId(), OrderStatus.PENDING)).isEqualTo(stock);
            assertThat(ticketTypeRepository.findById(ticketType.getId()).orElseThrow().getStockAvailable()).isZero();
        });
    }
}
