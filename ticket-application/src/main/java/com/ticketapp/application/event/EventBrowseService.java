package com.ticketapp.application.event;

import com.ticketapp.application.exception.AppException;
import com.ticketapp.application.exception.ErrorCode;
import com.ticketapp.domain.event.Event;
import com.ticketapp.domain.event.EventRepository;
import com.ticketapp.domain.ticket.TicketType;
import com.ticketapp.domain.ticket.TicketTypeRepository;
import com.ticketapp.infrastructure.stock.RedisStockCacheService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EventBrowseService {

    private final EventRepository eventRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final RedisStockCacheService stockCache;

    public EventBrowseService(EventRepository eventRepository, TicketTypeRepository ticketTypeRepository,
                              RedisStockCacheService stockCache) {
        this.eventRepository = eventRepository;
        this.ticketTypeRepository = ticketTypeRepository;
        this.stockCache = stockCache;
    }

    public EventDetailView browse(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new AppException(ErrorCode.EVENT_NOT_FOUND));

        List<TicketTypeView> ticketTypes = ticketTypeRepository.findByEventId(eventId).stream()
                .map(this::toView)
                .toList();

        return new EventDetailView(event.getId(), event.getTitle(), event.getDescription(), event.getVenue(),
                event.getCity(), event.getStartTime(), event.getEndTime(), event.getStatus(), event.getBannerUrl(),
                ticketTypes);
    }

    private TicketTypeView toView(TicketType ticketType) {
        return new TicketTypeView(ticketType.getId(), ticketType.getName(), ticketType.getDescription(),
                ticketType.getPrice(), ticketType.getSaleStartTime(), ticketType.getSaleEndTime(),
                ticketType.getStatus(), liveStock(ticketType.getId()));
    }

    /**
     * Redis is the source of truth for availability; ticket_types.stock_available always over-counts
     * while reserves are in flight, so it must never reach a buyer. A missing counter fails closed
     * rather than reseeding from the DB, which would invent stock that does not exist.
     */
    private int liveStock(Long ticketTypeId) {
        Long stock = stockCache.currentStock(ticketTypeId);
        if (stock == null || stock < 0) {
            return 0;
        }
        return Math.toIntExact(stock);
    }
}
