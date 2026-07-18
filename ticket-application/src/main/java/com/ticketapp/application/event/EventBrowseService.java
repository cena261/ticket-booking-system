package com.ticketapp.application.event;

import com.ticketapp.application.exception.AppException;
import com.ticketapp.application.exception.ErrorCode;
import com.ticketapp.infrastructure.stock.RedisStockCacheService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EventBrowseService {

    private final EventMetadataCacheService metadataCache;
    private final RedisStockCacheService stockCache;

    public EventBrowseService(EventMetadataCacheService metadataCache, RedisStockCacheService stockCache) {
        this.metadataCache = metadataCache;
        this.stockCache = stockCache;
    }

    public EventDetailView browse(Long eventId) {
        EventMetadata metadata = metadataCache.get(eventId);
        if (!metadata.exists()) {
            throw new AppException(ErrorCode.EVENT_NOT_FOUND);
        }

        List<TicketTypeView> ticketTypes = metadata.ticketTypes().stream()
                .map(this::toView)
                .toList();

        return new EventDetailView(metadata.id(), metadata.title(), metadata.description(), metadata.venue(),
                metadata.city(), metadata.startTime(), metadata.endTime(), metadata.status(), metadata.bannerUrl(),
                ticketTypes);
    }

    private TicketTypeView toView(TicketTypeMetadata ticketType) {
        return new TicketTypeView(ticketType.id(), ticketType.name(), ticketType.description(), ticketType.price(),
                ticketType.saleStartTime(), ticketType.saleEndTime(), ticketType.status(), liveStock(ticketType.id()));
    }

    private int liveStock(Long ticketTypeId) {
        Long stock = stockCache.currentStock(ticketTypeId);
        if (stock == null || stock < 0) {
            return 0;
        }
        return Math.toIntExact(stock);
    }
}
