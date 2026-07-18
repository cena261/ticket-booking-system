package com.ticketapp.order;

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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "reserve.rate-limit.limit-for-period=3",
        "reserve.rate-limit.limit-refresh-period=1m"
})
class ReserveRateLimiterIT extends AbstractIntegrationTest {

    private static final int LIMIT = 3;

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
    void seedTicketType() {
        User organizer = userRepository.save(Fixtures.newUser(UserRole.ORGANIZER));
        Event event = eventRepository.save(Fixtures.newEvent(organizer.getId()));
        TicketType ticketType = ticketTypeRepository.save(Fixtures.newTicketType(event.getId(), 500000, 100_000));
        stockWarmup.warm(ticketType.getId());
        this.ticketTypeId = ticketType.getId();
    }

    @Test
    void singleUserFloodIsThrottledAfterLimit() throws Exception {
        String token = registerUser();

        for (int i = 0; i < LIMIT; i++) {
            mvc.perform(reserve(token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(1000));
        }

        mvc.perform(reserve(token))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(2009));
    }

    @Test
    void distinctUsersAtSameAggregateRateAllPass() throws Exception {
        int users = 4;
        for (int u = 0; u < users; u++) {
            String token = registerUser();
            for (int i = 0; i < LIMIT; i++) {
                mvc.perform(reserve(token))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.code").value(1000));
            }
        }
    }

    private org.springframework.test.web.servlet.RequestBuilder reserve(String token) throws Exception {
        String body = objectMapper.writeValueAsString(new ReserveBody(ticketTypeId, 1));
        return post("/api/orders/reserve")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
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
