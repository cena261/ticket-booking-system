package com.ticketapp.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketapp.application.stock.StockWarmupService;
import com.ticketapp.domain.event.Event;
import com.ticketapp.domain.event.EventRepository;
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

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentControllerIT extends AbstractIntegrationTest {

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
    StockWarmupService stockWarmup;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Long ticketTypeId;

    @BeforeEach
    void seed() {
        User organizer = userRepository.save(Fixtures.newUser(UserRole.ORGANIZER));
        Event event = eventRepository.save(Fixtures.newEvent(organizer.getId()));
        TicketType ticketType = ticketTypeRepository.save(Fixtures.newTicketType(event.getId(), AMOUNT, 100));
        stockWarmup.warm(ticketType.getId());
        this.ticketTypeId = ticketType.getId();
    }

    @Test
    void payReturnsQrMemoAndExpiry() throws Exception {
        String token = registerUser();
        String orderNumber = reserve(token);

        mvc.perform(post("/api/orders/" + orderNumber + "/pay").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.result.qrUrl").value(org.hamcrest.Matchers.containsString("qr.sepay.vn")))
                .andExpect(jsonPath("$.result.memo").value(org.hamcrest.Matchers.startsWith("TKT")))
                .andExpect(jsonPath("$.result.amount").value((int) AMOUNT))
                .andExpect(jsonPath("$.result.expiresAt").isNotEmpty());
    }

    @Test
    void payRequiresAuthentication() throws Exception {
        String token = registerUser();
        String orderNumber = reserve(token);

        mvc.perform(post("/api/orders/" + orderNumber + "/pay"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void payForeignOrderIsForbidden() throws Exception {
        String owner = registerUser();
        String orderNumber = reserve(owner);
        String intruder = registerUser();

        mvc.perform(post("/api/orders/" + orderNumber + "/pay").header("Authorization", "Bearer " + intruder))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(2011));
    }

    private String reserve(String token) throws Exception {
        String body = objectMapper.writeValueAsString(new ReserveBody(ticketTypeId, 1));
        String response = mvc.perform(post("/api/orders/reserve")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("result").get("orderNumber").asText();
    }

    private String registerUser() throws Exception {
        String body = objectMapper.writeValueAsString(
                new Credentials(UUID.randomUUID() + "@demo.local", "password123"));
        String response = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("result").get("accessToken").asText();
    }

    private record ReserveBody(Long ticketTypeId, int quantity) {
    }

    private record Credentials(String email, String password) {
    }
}
