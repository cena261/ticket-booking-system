package com.ticketapp.notification;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.ticketapp.application.order.ReservationExpirySweeper;
import com.ticketapp.application.payment.PaymentAppService;
import com.ticketapp.application.payment.SepayWebhookRequest;
import com.ticketapp.application.stock.StockWarmupService;
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
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
class NotificationEmailIT extends AbstractIntegrationTest {

    private static final long AMOUNT = 500000;

    @RegisterExtension
    static final GreenMailExtension GREEN_MAIL = new GreenMailExtension(ServerSetupTest.SMTP);

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

    @Autowired
    ReservationExpirySweeper expirySweeper;

    @Autowired
    StockWarmupService stockWarmup;

    private Long eventId;
    private Long ticketTypeId;

    @BeforeEach
    void seed() {
        User organizer = userRepository.save(Fixtures.newUser(UserRole.ORGANIZER));
        Event event = eventRepository.save(Fixtures.newEvent(organizer.getId()));
        // One ticket already sold (available < initial) so an expiry restock stays within the CHECK.
        TicketType ticketType = Fixtures.newTicketType(event.getId(), AMOUNT, 100);
        ticketType.setStockAvailable(99);
        ticketType = ticketTypeRepository.save(ticketType);
        stockWarmup.warm(ticketType.getId());
        this.eventId = event.getId();
        this.ticketTypeId = ticketType.getId();
    }

    @Test
    void confirmationIssuesETicketAndSendsEmail() {
        User buyer = userRepository.save(Fixtures.newUser(UserRole.USER));
        Order order = orderRepository.save(order(buyer.getId(), OrderStatus.PENDING,
                Instant.now().plus(10, ChronoUnit.MINUTES)));

        paymentAppService.handleWebhook(confirmRequest(order.getPaymentRef()));

        Order reloaded = orderRepository.findByOrderNumber(order.getOrderNumber()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        String ticketCode = eTicketRepository.findByOrderId(order.getId()).getFirst().getTicketCode();

        MimeMessage message = awaitMessageTo(buyer.getEmail());
        assertThat(GreenMailUtil.getBody(message)).contains(ticketCode);
    }

    @Test
    void expirySendsCancellationEmail() {
        User buyer = userRepository.save(Fixtures.newUser(UserRole.USER));
        Order order = orderRepository.save(order(buyer.getId(), OrderStatus.PENDING,
                Instant.now().minus(1, ChronoUnit.MINUTES)));

        expirySweeper.sweepOnce();
        Order reloaded = orderRepository.findByOrderNumber(order.getOrderNumber()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.EXPIRED);

        MimeMessage message = awaitMessageTo(buyer.getEmail());
        assertThat(GreenMailUtil.getBody(message).toLowerCase()).contains("expired");
    }

    private MimeMessage awaitMessageTo(String email) {
        AtomicReference<MimeMessage> found = new AtomicReference<>();
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            MimeMessage match = Arrays.stream(GREEN_MAIL.getReceivedMessages())
                    .filter(m -> addressedTo(m, email))
                    .findFirst()
                    .orElse(null);
            found.set(match);
            assertThat(match).isNotNull();
        });
        return found.get();
    }

    private static boolean addressedTo(MimeMessage message, String email) {
        try {
            return message.getAllRecipients() != null
                    && Arrays.stream(message.getAllRecipients()).anyMatch(a -> a.toString().equals(email));
        } catch (Exception ex) {
            return false;
        }
    }

    private SepayWebhookRequest confirmRequest(String paymentRef) {
        return new SepayWebhookRequest(randomTxnId(), "MBBank", "2026-07-19 10:00:00", "0123456789",
                "", null, paymentRef, "in", "test", AMOUNT, 0, "FT123");
    }

    private Order order(Long userId, OrderStatus statusValue, Instant expiresAt) {
        Order order = new Order();
        order.setOrderNumber("ORD-" + UUID.randomUUID().toString().substring(0, 12));
        order.setUserId(userId);
        order.setEventId(eventId);
        order.setStatus(statusValue);
        order.setPaymentRef("TKT" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
        order.setReservedAt(Instant.now());
        order.setExpiresAt(expiresAt);
        OrderItem item = OrderItem.create(ticketTypeId, AMOUNT, 1);
        order.setTotalAmount(item.getSubtotal());
        order.addItem(item);
        return order;
    }

    private static long randomTxnId() {
        return Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000_000L);
    }
}
