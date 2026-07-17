package com.ticketapp.application.event;

import com.ticketapp.application.exception.AppException;
import com.ticketapp.application.exception.ErrorCode;
import com.ticketapp.domain.event.Event;
import com.ticketapp.domain.event.EventRepository;
import com.ticketapp.domain.event.EventStatus;
import com.ticketapp.domain.ticket.TicketType;
import com.ticketapp.domain.ticket.TicketTypeRepository;
import com.ticketapp.domain.ticket.TicketTypeStatus;
import com.ticketapp.infrastructure.stock.RedisStockCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventBrowseServiceTest {

    private static final Long EVENT_ID = 7L;
    private static final Long TICKET_TYPE_ID = 42L;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private TicketTypeRepository ticketTypeRepository;

    @Mock
    private RedisStockCacheService stockCache;

    private EventBrowseService service;

    @BeforeEach
    void setUp() {
        service = new EventBrowseService(eventRepository, ticketTypeRepository, stockCache);
    }

    @Test
    void stockComesFromRedisNotFromTheDatabaseColumn() {
        givenEventWithTicketType(dbStockAvailable(900));
        when(stockCache.currentStock(TICKET_TYPE_ID)).thenReturn(3L);

        EventDetailView view = service.browse(EVENT_ID);

        assertThat(view.ticketTypes()).singleElement()
                .satisfies(t -> assertThat(t.stockAvailable()).isEqualTo(3));
    }

    @Test
    void missingRedisKeyFailsClosedWithZeroStock() {
        givenEventWithTicketType(dbStockAvailable(900));
        when(stockCache.currentStock(TICKET_TYPE_ID)).thenReturn(null);

        EventDetailView view = service.browse(EVENT_ID);

        assertThat(view.ticketTypes()).singleElement()
                .satisfies(t -> assertThat(t.stockAvailable()).isZero());
    }

    @Test
    void negativeCounterIsClampedToZero() {
        givenEventWithTicketType(dbStockAvailable(0));
        when(stockCache.currentStock(TICKET_TYPE_ID)).thenReturn(-2L);

        EventDetailView view = service.browse(EVENT_ID);

        assertThat(view.ticketTypes()).singleElement()
                .satisfies(t -> assertThat(t.stockAvailable()).isZero());
    }

    @Test
    void exposesEventAndTicketTypeMetadata() {
        TicketType ticketType = givenEventWithTicketType(dbStockAvailable(10));
        when(stockCache.currentStock(TICKET_TYPE_ID)).thenReturn(10L);

        EventDetailView view = service.browse(EVENT_ID);

        assertThat(view.id()).isEqualTo(EVENT_ID);
        assertThat(view.title()).isEqualTo("Neon Nights");
        assertThat(view.status()).isEqualTo(EventStatus.PUBLISHED);
        assertThat(view.ticketTypes()).singleElement().satisfies(t -> {
            assertThat(t.id()).isEqualTo(TICKET_TYPE_ID);
            assertThat(t.name()).isEqualTo(ticketType.getName());
            assertThat(t.price()).isEqualTo(500_000L);
            assertThat(t.status()).isEqualTo(TicketTypeStatus.ON_SALE);
            assertThat(t.saleStartTime()).isEqualTo(ticketType.getSaleStartTime());
        });
    }

    @Test
    void unknownEventIsNotFound() {
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.browse(EVENT_ID))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EVENT_NOT_FOUND);
    }

    private static int dbStockAvailable(int value) {
        return value;
    }

    private TicketType givenEventWithTicketType(int dbStockAvailable) {
        Event event = new Event();
        event.setId(EVENT_ID);
        event.setOrganizerId(1L);
        event.setTitle("Neon Nights");
        event.setDescription("An open-air electronic music festival.");
        event.setVenue("Riverside Park");
        event.setCity("Da Nang");
        event.setStartTime(Instant.now().plus(30, ChronoUnit.DAYS));
        event.setEndTime(Instant.now().plus(31, ChronoUnit.DAYS));
        event.setStatus(EventStatus.PUBLISHED);

        TicketType ticketType = new TicketType();
        ticketType.setId(TICKET_TYPE_ID);
        ticketType.setEventId(EVENT_ID);
        ticketType.setName("Standard");
        ticketType.setDescription("General admission.");
        ticketType.setPrice(500_000L);
        ticketType.setStockInitial(1000);
        ticketType.setStockAvailable(dbStockAvailable);
        ticketType.setSaleStartTime(Instant.now());
        ticketType.setSaleEndTime(Instant.now().plus(29, ChronoUnit.DAYS));
        ticketType.setStatus(TicketTypeStatus.ON_SALE);

        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(ticketTypeRepository.findByEventId(EVENT_ID)).thenReturn(List.of(ticketType));
        return ticketType;
    }
}
