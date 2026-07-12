package com.ticketapp.order;

import com.ticketapp.application.exception.ErrorCode;
import com.ticketapp.application.order.ReserveOrderService;
import com.ticketapp.application.order.ReserveResult;
import com.ticketapp.domain.event.Event;
import com.ticketapp.domain.event.EventRepository;
import com.ticketapp.domain.order.Order;
import com.ticketapp.domain.order.OrderRepository;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
class ReserveOrderPersistFailureIT extends AbstractIntegrationTest {

    @MockitoBean
    OrderRepository orderRepository;

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
    void restoresCacheAndDatabaseWhenOrderPersistFails() {
        when(orderRepository.save(any(Order.class))).thenThrow(new DataIntegrityViolationException("boom"));

        User organizer = userRepository.save(Fixtures.newUser(UserRole.ORGANIZER));
        Event event = eventRepository.save(Fixtures.newEvent(organizer.getId()));
        TicketType ticketType = ticketTypeRepository.save(Fixtures.newTicketType(event.getId(), 500000, 5));

        ReserveResult result = reserveOrderService.reserve(organizer.getId(), ticketType.getId(), 2);

        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo(ErrorCode.RESERVE_FAILED);
        assertThat(ticketTypeRepository.findById(ticketType.getId()).orElseThrow().getStockAvailable()).isEqualTo(5);
        assertThat(stockCache.currentStock(ticketType.getId())).isEqualTo(5);
    }
}
