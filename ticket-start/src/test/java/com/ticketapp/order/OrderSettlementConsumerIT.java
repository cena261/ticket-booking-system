package com.ticketapp.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketapp.application.order.OrderPlacedMessage;
import com.ticketapp.application.order.OrderSettlementConsumer;
import com.ticketapp.domain.event.Event;
import com.ticketapp.domain.event.EventRepository;
import com.ticketapp.domain.order.OrderRepository;
import com.ticketapp.domain.order.OrderStatus;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OrderSettlementConsumerIT extends AbstractIntegrationTest {

    @Autowired
    OrderSettlementConsumer consumer;

    @Autowired
    UserRepository userRepository;

    @Autowired
    EventRepository eventRepository;

    @Autowired
    TicketTypeRepository ticketTypeRepository;

    @Autowired
    OrderRepository orderRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String payload(Long userId, Long eventId, Long ticketTypeId, int quantity) throws Exception {
        OrderPlacedMessage message = new OrderPlacedMessage("MQ-" + UUID.randomUUID(), userId, ticketTypeId,
                eventId, quantity, 500000, Instant.now().toEpochMilli());
        return objectMapper.writeValueAsString(message);
    }

    @Test
    void settlesBatchWithOneStockUpdateAndCreatesEveryOrder() throws Exception {
        User buyer = userRepository.save(Fixtures.newUser(UserRole.USER));
        User organizer = userRepository.save(Fixtures.newUser(UserRole.ORGANIZER));
        Event event = eventRepository.save(Fixtures.newEvent(organizer.getId()));
        TicketType ticketType = ticketTypeRepository.save(Fixtures.newTicketType(event.getId(), 500000, 20));

        List<String> batch = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            batch.add(payload(buyer.getId(), event.getId(), ticketType.getId(), 2));
        }

        consumer.onMessages(batch);

        assertThat(orderRepository.countByEventIdAndStatus(event.getId(), OrderStatus.PENDING)).isEqualTo(5);
        assertThat(ticketTypeRepository.findById(ticketType.getId()).orElseThrow().getStockAvailable()).isEqualTo(10);
    }

    @Test
    void duplicateDeliverySettlesOnlyOnce() throws Exception {
        User buyer = userRepository.save(Fixtures.newUser(UserRole.USER));
        User organizer = userRepository.save(Fixtures.newUser(UserRole.ORGANIZER));
        Event event = eventRepository.save(Fixtures.newEvent(organizer.getId()));
        TicketType ticketType = ticketTypeRepository.save(Fixtures.newTicketType(event.getId(), 500000, 10));

        String duplicated = payload(buyer.getId(), event.getId(), ticketType.getId(), 3);

        consumer.onMessages(List.of(duplicated));
        consumer.onMessages(List.of(duplicated));

        assertThat(orderRepository.countByEventIdAndStatus(event.getId(), OrderStatus.PENDING)).isEqualTo(1);
        assertThat(ticketTypeRepository.findById(ticketType.getId()).orElseThrow().getStockAvailable()).isEqualTo(7);
    }
}
