package com.ticketapp.order;

import com.ticketapp.application.exception.ErrorCode;
import com.ticketapp.application.order.ReserveOrderService;
import com.ticketapp.application.order.ReserveResult;
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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ReserveOrderCompensationIT extends AbstractIntegrationTest {

    @Autowired
    ReserveOrderService reserveOrderService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    EventRepository eventRepository;

    @Autowired
    TicketTypeRepository ticketTypeRepository;

    @Autowired
    RedisStockCacheService stockCache;

    @Test
    void restoresCacheWhenDatabaseGuardRejectsDeduction() {
        User organizer = userRepository.save(Fixtures.newUser(UserRole.ORGANIZER));
        Event event = eventRepository.save(Fixtures.newEvent(organizer.getId()));
        TicketType ticketType = ticketTypeRepository.save(Fixtures.newTicketType(event.getId(), 500000, 0));
        stockCache.warmUp(ticketType.getId(), 5);

        ReserveResult result = reserveOrderService.reserve(organizer.getId(), ticketType.getId(), 1);

        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo(ErrorCode.STOCK_CONFLICT);
        assertThat(stockCache.currentStock(ticketType.getId())).isEqualTo(5);
        assertThat(ticketTypeRepository.findById(ticketType.getId()).orElseThrow().getStockAvailable()).isZero();
    }
}
