package com.ticketapp.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketapp.application.payment.PaymentAppService;
import com.ticketapp.application.payment.SepayWebhookRequest;
import com.ticketapp.domain.event.Event;
import com.ticketapp.domain.event.EventRepository;
import com.ticketapp.domain.order.Order;
import com.ticketapp.domain.order.OrderItem;
import com.ticketapp.domain.order.OrderRepository;
import com.ticketapp.domain.order.OrderStatus;
import com.ticketapp.domain.payment.PaymentTransactionRepository;
import com.ticketapp.domain.ticket.TicketType;
import com.ticketapp.domain.ticket.TicketTypeRepository;
import com.ticketapp.domain.user.User;
import com.ticketapp.domain.user.UserRepository;
import com.ticketapp.domain.user.UserRole;
import com.ticketapp.support.AbstractIntegrationTest;
import com.ticketapp.support.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SepayWebhookIT extends AbstractIntegrationTest {

    private static final String SECRET = "test-webhook-secret";
    private static final long AMOUNT = 500000;

    @Autowired
    MockMvc mvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    EventRepository eventRepository;

    @Autowired
    TicketTypeRepository ticketTypeRepository;

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    PaymentTransactionRepository paymentTransactionRepository;

    @Autowired
    PaymentAppService paymentAppService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Long ticketTypeId;
    private Long eventId;

    @BeforeEach
    void seed() {
        User organizer = userRepository.save(Fixtures.newUser(UserRole.ORGANIZER));
        Event event = eventRepository.save(Fixtures.newEvent(organizer.getId()));
        TicketType ticketType = ticketTypeRepository.save(Fixtures.newTicketType(event.getId(), AMOUNT, 100));
        this.eventId = event.getId();
        this.ticketTypeId = ticketType.getId();
    }

    @Test
    void validPayloadConfirmsOrder() throws Exception {
        Order order = pendingOrder();
        long txnId = randomTxnId();

        mvc.perform(webhook(signedRequest(txnId, order.getPaymentRef(), AMOUNT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        Order reloaded = orderRepository.findByOrderNumber(order.getOrderNumber()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(reloaded.getPaidAt()).isNotNull();
        assertThat(paymentTransactionRepository.countByOrderId(order.getId())).isEqualTo(1);
    }

    @Test
    void duplicateWebhookConfirmsExactlyOnce() throws Exception {
        Order order = pendingOrder();
        long txnId = randomTxnId();
        byte[] request = signedBody(txnId, order.getPaymentRef(), AMOUNT);

        mvc.perform(webhookRaw(request)).andExpect(status().isOk());
        mvc.perform(webhookRaw(request)).andExpect(status().isOk());
        mvc.perform(webhookRaw(request)).andExpect(status().isOk());

        Order reloaded = orderRepository.findByOrderNumber(order.getOrderNumber()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(paymentTransactionRepository.countByOrderId(order.getId())).isEqualTo(1);
    }

    @Test
    void concurrentDuplicatesConfirmExactlyOnce() throws Exception {
        Order order = pendingOrder();
        long txnId = randomTxnId();
        SepayWebhookRequest request = new SepayWebhookRequest(txnId, "MBBank", "2026-07-19 10:00:00",
                "0123456789", "", null, order.getPaymentRef(), "in", "test", AMOUNT, 0, "FT123");

        int threads = 32;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger errors = new AtomicInteger();
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    paymentAppService.handleWebhook(request);
                } catch (Exception ex) {
                    errors.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await(20, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(errors.get()).isZero();
        Order reloaded = orderRepository.findByOrderNumber(order.getOrderNumber()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(paymentTransactionRepository.countByOrderId(order.getId())).isEqualTo(1);
    }

    @Test
    void secondDistinctTransferOnPaidOrderFlagsRefund() throws Exception {
        Order order = pendingOrder();

        mvc.perform(webhook(signedRequest(randomTxnId(), order.getPaymentRef(), AMOUNT)))
                .andExpect(status().isOk());
        mvc.perform(webhook(signedRequest(randomTxnId(), order.getPaymentRef(), AMOUNT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        Order reloaded = orderRepository.findByOrderNumber(order.getOrderNumber()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(reloaded.isRefundRequired()).isTrue();
        assertThat(paymentTransactionRepository.countByOrderId(order.getId())).isEqualTo(2);
    }

    @Test
    void amountMismatchDoesNotConfirm() throws Exception {
        Order order = pendingOrder();
        long txnId = randomTxnId();

        mvc.perform(webhook(signedRequest(txnId, order.getPaymentRef(), AMOUNT - 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        Order reloaded = orderRepository.findByOrderNumber(order.getOrderNumber()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(reloaded.getPaidAt()).isNull();
        assertThat(paymentTransactionRepository.countByOrderId(order.getId())).isEqualTo(1);
    }

    @Test
    void badSignatureIsRejected() throws Exception {
        Order order = pendingOrder();
        byte[] body = rawBody(randomTxnId(), order.getPaymentRef(), AMOUNT);

        mvc.perform(post("/api/webhooks/sepay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-SePay-Signature", "sha256=deadbeef")
                        .header("X-SePay-Timestamp", String.valueOf(Instant.now().getEpochSecond()))
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        Order reloaded = orderRepository.findByOrderNumber(order.getOrderNumber()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void lateWebhookOnExpiredOrderFlagsRefund() throws Exception {
        Order order = expiredOrder();
        long txnId = randomTxnId();

        mvc.perform(webhook(signedRequest(txnId, order.getPaymentRef(), AMOUNT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        Order reloaded = orderRepository.findByOrderNumber(order.getOrderNumber()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.EXPIRED);
        assertThat(reloaded.isRefundRequired()).isTrue();
        assertThat(paymentTransactionRepository.countByOrderId(order.getId())).isEqualTo(1);
    }

    private Order pendingOrder() {
        return orderRepository.save(buildOrder(OrderStatus.PENDING));
    }

    private Order expiredOrder() {
        return orderRepository.save(buildOrder(OrderStatus.EXPIRED));
    }

    private Order buildOrder(OrderStatus statusValue) {
        Order order = new Order();
        order.setOrderNumber("ORD-" + UUID.randomUUID().toString().substring(0, 12));
        order.setUserId(userRepository.save(Fixtures.newUser(UserRole.USER)).getId());
        order.setEventId(eventId);
        order.setStatus(statusValue);
        order.setPaymentRef("TKT" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
        order.setReservedAt(Instant.now());
        order.setExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES));
        OrderItem item = OrderItem.create(ticketTypeId, AMOUNT, 1);
        order.setTotalAmount(item.getSubtotal());
        order.addItem(item);
        return order;
    }

    private org.springframework.test.web.servlet.RequestBuilder webhook(SignedPayload payload) {
        return post("/api/webhooks/sepay")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-SePay-Signature", "sha256=" + payload.signature())
                .header("X-SePay-Timestamp", String.valueOf(payload.timestamp()))
                .content(payload.body());
    }

    private org.springframework.test.web.servlet.RequestBuilder webhookRaw(byte[] signedBody) {
        return webhook(resign(signedBody));
    }

    private SignedPayload signedRequest(long txnId, String memo, long amount) {
        return resign(rawBody(txnId, memo, amount));
    }

    private byte[] signedBody(long txnId, String memo, long amount) {
        return rawBody(txnId, memo, amount);
    }

    private SignedPayload resign(byte[] body) {
        long ts = Instant.now().getEpochSecond();
        return new SignedPayload(body, ts, hmac(ts, body));
    }

    private byte[] rawBody(long txnId, String memo, long amount) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", txnId);
        payload.put("gateway", "MBBank");
        payload.put("transactionDate", "2026-07-19 10:00:00");
        payload.put("accountNumber", "0123456789");
        payload.put("content", memo + " thanh toan don hang");
        payload.put("transferType", "in");
        payload.put("transferAmount", amount);
        payload.put("accumulated", 0);
        payload.put("referenceCode", "FT123");
        try {
            return objectMapper.writeValueAsBytes(payload);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static long randomTxnId() {
        return Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000_000L);
    }

    private static String hmac(long timestamp, byte[] body) {
        try {
            byte[] prefix = (timestamp + ".").getBytes(StandardCharsets.UTF_8);
            byte[] message = new byte[prefix.length + body.length];
            System.arraycopy(prefix, 0, message, 0, prefix.length);
            System.arraycopy(body, 0, message, prefix.length, body.length);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(message));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private record SignedPayload(byte[] body, long timestamp, String signature) {
    }
}
