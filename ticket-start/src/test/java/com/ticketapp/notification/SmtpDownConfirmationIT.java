package com.ticketapp.notification;

import com.ticketapp.application.payment.PaymentAppService;
import com.ticketapp.application.payment.SepayWebhookRequest;
import com.ticketapp.domain.eticket.ETicketRepository;
import com.ticketapp.domain.event.Event;
import com.ticketapp.domain.event.EventRepository;
import com.ticketapp.domain.order.Order;
import com.ticketapp.domain.order.OrderItem;
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
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SMTP unreachable (nothing listens on the configured port). The webhook confirm path must still
 * take the order to CONFIRMED and issue e-tickets: email is best-effort, async and after-commit,
 * so its failure can never break or block confirmation.
 */
@SpringBootTest
@TestPropertySource(properties = {"spring.mail.host=localhost", "spring.mail.port=3999"})
class SmtpDownConfirmationIT extends AbstractIntegrationTest {

    private static final long AMOUNT = 500000;

    @Autowired
    UserRepository userRepository;

    @Autowired
    EventRepository eventRepository;

    @Autowired
    TicketTypeRepository ticketTypeRepository;

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    ETicketRepository eTicketRepository;

    @Autowired
    PaymentAppService paymentAppService;

    @Test
    void confirmationSucceedsWhenSmtpUnreachable() {
        User organizer = userRepository.save(Fixtures.newUser(UserRole.ORGANIZER));
        Event event = eventRepository.save(Fixtures.newEvent(organizer.getId()));
        TicketType ticketType = ticketTypeRepository.save(Fixtures.newTicketType(event.getId(), AMOUNT, 100));
        User buyer = userRepository.save(Fixtures.newUser(UserRole.USER));

        Order order = new Order();
        order.setOrderNumber("ORD-" + UUID.randomUUID().toString().substring(0, 12));
        order.setUserId(buyer.getId());
        order.setEventId(event.getId());
        order.setStatus(OrderStatus.PENDING);
        order.setPaymentRef("TKT" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
        order.setReservedAt(Instant.now());
        order.setExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES));
        OrderItem item = OrderItem.create(ticketType.getId(), AMOUNT, 1);
        order.setTotalAmount(item.getSubtotal());
        order.addItem(item);
        order = orderRepository.save(order);

        paymentAppService.handleWebhook(new SepayWebhookRequest(randomTxnId(), "MBBank",
                "2026-07-19 10:00:00", "0123456789", "", null, order.getPaymentRef(), "in", "test", AMOUNT, 0, "FT123"));

        Order reloaded = orderRepository.findByOrderNumber(order.getOrderNumber()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(eTicketRepository.countByOrderId(order.getId())).isEqualTo(1);
    }

    private static long randomTxnId() {
        return Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000_000L);
    }
}
