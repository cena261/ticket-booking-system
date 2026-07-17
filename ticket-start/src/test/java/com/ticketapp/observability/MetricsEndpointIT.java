package com.ticketapp.observability;

import com.ticketapp.application.order.ReserveOrderService;
import com.ticketapp.application.order.ReserveResult;
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
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the observability contract Phase 12 and Grafana depend on: after a reserve, the custom buy-path
 * meters must be present in the Prometheus scrape (the exact text /actuator/prometheus serves), carry the
 * instance tag, and expose the reserve timer. Asserting on the registry scrape avoids a web-client dependency
 * while covering the same output.
 */
@SpringBootTest
class MetricsEndpointIT extends AbstractIntegrationTest {

    @Autowired
    ReserveOrderService reserveOrderService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    EventRepository eventRepository;

    @Autowired
    TicketTypeRepository ticketTypeRepository;

    @Autowired
    StockWarmupService stockWarmup;

    @Autowired
    PrometheusMeterRegistry prometheusRegistry;

    @Test
    void prometheusEndpointExposesCustomBuyPathMeters() {
        User buyer = userRepository.save(Fixtures.newUser(UserRole.USER));
        User organizer = userRepository.save(Fixtures.newUser(UserRole.ORGANIZER));
        Event event = eventRepository.save(Fixtures.newEvent(organizer.getId()));
        TicketType ticketType = ticketTypeRepository.save(Fixtures.newTicketType(event.getId(), 500000, 10));
        stockWarmup.warm(ticketType.getId());

        ReserveResult result = reserveOrderService.reserve(buyer.getId(), ticketType.getId(), 1);
        assertThat(result.success()).isTrue();

        String body = prometheusRegistry.scrape();

        // Custom counters/gauges pre-registered by BuyPathMetrics exist regardless of traffic.
        assertThat(body).contains("ticket_oversell_prevented_total");
        assertThat(body).contains("ticket_payment_confirmed_total");
        assertThat(body).contains("ticket_ratelimiter_rejections_total");
        assertThat(body).contains("ticket_cache_requests_total");
        assertThat(body).contains("ticket_outbox_pending");
        // The reserve timer appears after the first reserve, tagged by mode and outcome.
        assertThat(body).contains("ticket_reserve_seconds_count");
        assertThat(body).contains("outcome=\"success\"");
        assertThat(body).contains("mode=\"sync\"");
        // Instance common tag from application.yml (default app-1) must be on every series.
        assertThat(body).contains("instance=\"app-1\"");
    }
}
