package com.ticketapp.application.event;

import com.ticketapp.application.exception.AppException;
import com.ticketapp.application.exception.ErrorCode;
import com.ticketapp.domain.event.EventStatus;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventBrowseServiceTest {

    private static final Long EVENT_ID = 7L;
    private static final Long TICKET_TYPE_ID = 42L;

    @Mock
    private EventMetadataCacheService metadataCache;

    @Mock
    private RedisStockCacheService stockCache;

    private EventBrowseService service;

    @BeforeEach
    void setUp() {
        service = new EventBrowseService(metadataCache, stockCache);
    }

    private EventMetadata givenCachedEvent() {
        TicketTypeMetadata ticketType = new TicketTypeMetadata(TICKET_TYPE_ID, "Standard", "General admission.",
                500_000L, Instant.now(), Instant.now().plus(29, ChronoUnit.DAYS), TicketTypeStatus.ON_SALE);
        EventMetadata metadata = new EventMetadata(true, EVENT_ID, "Neon Nights", "An open-air festival.",
                "Riverside Park", "Da Nang", Instant.now().plus(30, ChronoUnit.DAYS),
                Instant.now().plus(31, ChronoUnit.DAYS), EventStatus.PUBLISHED, null, List.of(ticketType));
        when(metadataCache.get(EVENT_ID)).thenReturn(metadata);
        return metadata;
    }

    @Test
    void stockIsComposedLiveFromRedisOntoCachedMetadata() {
        givenCachedEvent();
        when(stockCache.currentStock(TICKET_TYPE_ID)).thenReturn(3L);

        EventDetailView view = service.browse(EVENT_ID);

        assertThat(view.ticketTypes()).singleElement()
                .satisfies(t -> assertThat(t.stockAvailable()).isEqualTo(3));
    }

    @Test
    void missingRedisKeyFailsClosedWithZeroStock() {
        givenCachedEvent();
        when(stockCache.currentStock(TICKET_TYPE_ID)).thenReturn(null);

        EventDetailView view = service.browse(EVENT_ID);

        assertThat(view.ticketTypes()).singleElement()
                .satisfies(t -> assertThat(t.stockAvailable()).isZero());
    }

    @Test
    void negativeCounterIsClampedToZero() {
        givenCachedEvent();
        when(stockCache.currentStock(TICKET_TYPE_ID)).thenReturn(-2L);

        EventDetailView view = service.browse(EVENT_ID);

        assertThat(view.ticketTypes()).singleElement()
                .satisfies(t -> assertThat(t.stockAvailable()).isZero());
    }

    @Test
    void exposesEventAndTicketTypeMetadataFromTheCache() {
        EventMetadata cached = givenCachedEvent();
        when(stockCache.currentStock(TICKET_TYPE_ID)).thenReturn(10L);

        EventDetailView view = service.browse(EVENT_ID);

        assertThat(view.id()).isEqualTo(EVENT_ID);
        assertThat(view.title()).isEqualTo("Neon Nights");
        assertThat(view.status()).isEqualTo(EventStatus.PUBLISHED);
        assertThat(view.ticketTypes()).singleElement().satisfies(t -> {
            assertThat(t.id()).isEqualTo(TICKET_TYPE_ID);
            assertThat(t.name()).isEqualTo("Standard");
            assertThat(t.price()).isEqualTo(500_000L);
            assertThat(t.status()).isEqualTo(TicketTypeStatus.ON_SALE);
            assertThat(t.saleStartTime()).isEqualTo(cached.ticketTypes().getFirst().saleStartTime());
        });
    }

    @Test
    void nullSentinelFromTheCacheSurfacesAsEventNotFound() {
        when(metadataCache.get(EVENT_ID)).thenReturn(EventMetadata.notFound());

        assertThatThrownBy(() -> service.browse(EVENT_ID))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EVENT_NOT_FOUND);
    }
}
