package com.ticketapp.persistence;

import com.ticketapp.domain.event.Event;
import com.ticketapp.domain.event.EventRepository;
import com.ticketapp.domain.order.Order;
import com.ticketapp.domain.order.OrderRepository;
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
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class OrderRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    TicketTypeRepository ticketTypeRepository;

    @Autowired
    EventRepository eventRepository;

    @Autowired
    UserRepository userRepository;

    private Order persistedOrder() {
        User buyer = userRepository.save(Fixtures.newUser(UserRole.USER));
        User organizer = userRepository.save(Fixtures.newUser(UserRole.ORGANIZER));
        Event event = eventRepository.save(Fixtures.newEvent(organizer.getId()));
        TicketType ticketType = ticketTypeRepository.save(Fixtures.newTicketType(event.getId(), 500000, 1000));
        return Fixtures.newOrder(buyer.getId(), event.getId(), ticketType.getId(), 500000, 2);
    }

    @Test
    void savesOrderWithItemAndFindsByOrderNumber() {
        Order saved = orderRepository.save(persistedOrder());

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getTotalAmount()).isEqualTo(1000000);

        Order found = orderRepository.findByOrderNumber(saved.getOrderNumber()).orElseThrow();
        assertThat(found.getItems()).hasSize(1);
        assertThat(found.getItems().getFirst().getSubtotal()).isEqualTo(1000000);
    }

    @Test
    void findsByPaymentRef() {
        Order saved = orderRepository.save(persistedOrder());

        assertThat(orderRepository.findByPaymentRef(saved.getPaymentRef()))
                .get()
                .extracting(Order::getId)
                .isEqualTo(saved.getId());
    }

    @Test
    void rejectsDuplicateOrderNumber() {
        Order first = orderRepository.save(persistedOrder());

        Order duplicate = persistedOrder();
        duplicate.setOrderNumber(first.getOrderNumber());

        assertThatThrownBy(() -> orderRepository.save(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsDuplicatePaymentRef() {
        Order first = orderRepository.save(persistedOrder());

        Order duplicate = persistedOrder();
        duplicate.setPaymentRef(first.getPaymentRef());

        assertThatThrownBy(() -> orderRepository.save(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
