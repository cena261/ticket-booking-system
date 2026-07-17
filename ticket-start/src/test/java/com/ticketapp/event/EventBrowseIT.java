package com.ticketapp.event;

import com.ticketapp.domain.event.Event;
import com.ticketapp.domain.event.EventRepository;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class EventBrowseIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    EventRepository eventRepository;

    @Autowired
    TicketTypeRepository ticketTypeRepository;

    @Autowired
    RedisStockCacheService stockCache;

    @Autowired
    UserRepository userRepository;

    private TicketType seed(int dbStock, Integer redisStock) {
        User organizer = userRepository.save(Fixtures.newUser(UserRole.ORGANIZER));
        Event event = eventRepository.save(Fixtures.newEvent(organizer.getId()));
        TicketType ticketType = ticketTypeRepository.save(Fixtures.newTicketType(event.getId(), 500_000L, dbStock));
        stockCache.evict(ticketType.getId());
        if (redisStock != null) {
            stockCache.warmUp(ticketType.getId(), redisStock);
        }
        return ticketType;
    }

    @Test
    void browseIsPublicAndServesStockFromRedis() throws Exception {
        TicketType ticketType = seed(1000, 4);

        mvc.perform(get("/api/events/{id}", ticketType.getEventId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.result.id").value(ticketType.getEventId()))
                .andExpect(jsonPath("$.result.ticketTypes[0].id").value(ticketType.getId()))
                .andExpect(jsonPath("$.result.ticketTypes[0].name").value("Standard"))
                .andExpect(jsonPath("$.result.ticketTypes[0].price").value(500_000))
                .andExpect(jsonPath("$.result.ticketTypes[0].stockAvailable").value(4));
    }

    @Test
    void missingStockCounterFailsClosedInsteadOfServingTheDatabaseColumn() throws Exception {
        TicketType ticketType = seed(1000, null);

        mvc.perform(get("/api/events/{id}", ticketType.getEventId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.ticketTypes[0].stockAvailable").value(0));
    }

    @Test
    void unknownEventReturnsNotFound() throws Exception {
        mvc.perform(get("/api/events/{id}", 9_999_999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(2008));
    }
}
