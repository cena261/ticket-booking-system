package com.ticketapp.order;

import com.ticketapp.application.order.AsyncReserveResult;
import com.ticketapp.application.order.AsyncReserveService;
import com.ticketapp.domain.event.Event;
import com.ticketapp.domain.event.EventRepository;
import com.ticketapp.domain.order.OrderRepository;
import com.ticketapp.domain.order.OrderStatus;
import com.ticketapp.domain.outbox.OutboxStatus;
import com.ticketapp.domain.outbox.OutboxEventRepository;
import com.ticketapp.domain.queue.OrderQueueStatus;
import com.ticketapp.domain.ticket.TicketType;
import com.ticketapp.domain.ticket.TicketTypeRepository;
import com.ticketapp.domain.user.User;
import com.ticketapp.domain.user.UserRepository;
import com.ticketapp.domain.user.UserRole;
import com.ticketapp.support.AbstractIntegrationTest;
import com.ticketapp.support.Fixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
class AsyncReserveFlowIT extends AbstractIntegrationTest {

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
    OutboxEventRepository outboxEventRepository;

    @Test
    void reserveAsyncSettlesThroughOutboxAndKafka() {
        User buyer = userRepository.save(Fixtures.newUser(UserRole.USER));
        User organizer = userRepository.save(Fixtures.newUser(UserRole.ORGANIZER));
        Event event = eventRepository.save(Fixtures.newEvent(organizer.getId()));
        TicketType ticketType = ticketTypeRepository.save(Fixtures.newTicketType(event.getId(), 500000, 10));

        AsyncReserveResult result = asyncReserveService.reserveAsync(buyer.getId(), ticketType.getId(), 2);

        assertThat(result.success()).isTrue();
        assertThat(result.token()).startsWith("MQ-");
        assertThat(asyncReserveService.status(result.token()).status()).isIn(
                OrderQueueStatus.PENDING, OrderQueueStatus.SUCCESS);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(asyncReserveService.status(result.token()).status()).isEqualTo(OrderQueueStatus.SUCCESS);
        });

        String orderNumber = asyncReserveService.status(result.token()).orderNumber();
        assertThat(orderNumber).isNotBlank();
        assertThat(orderRepository.findByOrderNumber(orderNumber)).isPresent();
        assertThat(orderRepository.countByEventIdAndStatus(event.getId(), OrderStatus.PENDING)).isEqualTo(1);
        assertThat(ticketTypeRepository.findById(ticketType.getId()).orElseThrow().getStockAvailable()).isEqualTo(8);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(outboxEventRepository.countByStatus(OutboxStatus.PENDING)).isZero());
    }
}
