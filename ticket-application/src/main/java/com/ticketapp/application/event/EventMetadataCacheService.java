package com.ticketapp.application.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.benmanes.caffeine.cache.Cache;
import com.ticketapp.application.observability.BuyPathMetrics;
import com.ticketapp.domain.event.Event;
import com.ticketapp.domain.event.EventRepository;
import com.ticketapp.domain.ticket.TicketType;
import com.ticketapp.domain.ticket.TicketTypeRepository;
import com.ticketapp.infrastructure.lock.DistributedLockService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class EventMetadataCacheService {

    private static final String DATA_KEY_FORMAT = "event:%d:data";
    private static final String VERSION_KEY_FORMAT = "event:%d:ver";
    private static final String LOCK_KEY_FORMAT = "lock:event:%d:meta";

    private static final String STORE_LUA = """
            redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[3]);
            redis.call('SET', KEYS[2], ARGV[2], 'PX', ARGV[3]);
            return 1;
            """;

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    private final Cache<Long, CachedEventMetadata> l1Cache;
    private final StringRedisTemplate redisTemplate;
    private final DistributedLockService lockService;
    private final EventRepository eventRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final BuyPathMetrics metrics;
    private final EventCacheProperties properties;
    private final DefaultRedisScript<Long> storeScript;

    public EventMetadataCacheService(Cache<Long, CachedEventMetadata> l1Cache, StringRedisTemplate redisTemplate,
                                     DistributedLockService lockService, EventRepository eventRepository,
                                     TicketTypeRepository ticketTypeRepository, BuyPathMetrics metrics,
                                     EventCacheProperties properties) {
        this.l1Cache = l1Cache;
        this.redisTemplate = redisTemplate;
        this.lockService = lockService;
        this.eventRepository = eventRepository;
        this.ticketTypeRepository = ticketTypeRepository;
        this.metrics = metrics;
        this.properties = properties;
        this.storeScript = new DefaultRedisScript<>(STORE_LUA, Long.class);
    }

    public EventMetadata get(Long eventId) {
        String version = redisTemplate.opsForValue().get(versionKey(eventId));
        if (version == null) {
            metrics.cacheMiss("l1");
            return rebuild(eventId);
        }

        CachedEventMetadata local = l1Cache.getIfPresent(eventId);
        if (local != null && version.equals(local.version())) {
            metrics.cacheHit("l1");
            return local.metadata();
        }
        metrics.cacheMiss("l1");

        EventMetadata fromL2 = readL2(eventId);
        if (fromL2 != null) {
            metrics.cacheHit("l2");
            l1Cache.put(eventId, new CachedEventMetadata(version, fromL2));
            return fromL2;
        }
        metrics.cacheMiss("l2");
        return rebuild(eventId);
    }

    public void invalidate(Long eventId) {
        boolean acquired = lockService.tryRun(lockKey(eventId), properties.getInvalidateLockWait(),
                properties.getLockLease(), () -> evict(eventId));
        if (!acquired) {
            log.warn("event metadata cache invalidate could not take the rebuild lock, evicting anyway eventId={}",
                    eventId);
            evict(eventId);
        }
    }

    private void evict(Long eventId) {
        redisTemplate.delete(List.of(dataKey(eventId), versionKey(eventId)));
        l1Cache.invalidate(eventId);
        log.debug("event metadata cache invalidated eventId={}", eventId);
    }

    private EventMetadata rebuild(Long eventId) {
        for (int attempt = 0; attempt <= properties.getRebuildRetries(); attempt++) {
            AtomicReference<EventMetadata> result = new AtomicReference<>();
            boolean acquired = lockService.tryRun(lockKey(eventId), properties.getLockWait(),
                    properties.getLockLease(), () -> {
                        EventMetadata alreadyBuilt = readFromRedis(eventId);
                        if (alreadyBuilt != null) {
                            result.set(alreadyBuilt);
                            return;
                        }
                        EventMetadata loaded = loadFromDb(eventId);
                        store(eventId, loaded);
                        result.set(loaded);
                    });

            if (acquired) {
                return result.get();
            }

            sleepBackoff();
            EventMetadata builtByWinner = readFromRedis(eventId);
            if (builtByWinner != null) {
                return builtByWinner;
            }
        }

        log.warn("event metadata cache rebuild lock unavailable after {} attempts, serving direct read eventId={}",
                properties.getRebuildRetries() + 1, eventId);
        return loadFromDb(eventId);
    }

    private EventMetadata readFromRedis(Long eventId) {
        String version = redisTemplate.opsForValue().get(versionKey(eventId));
        if (version == null) {
            return null;
        }
        EventMetadata data = readL2(eventId);
        if (data == null) {
            return null;
        }
        metrics.cacheHit("l2");
        l1Cache.put(eventId, new CachedEventMetadata(version, data));
        return data;
    }

    private EventMetadata readL2(Long eventId) {
        String json = redisTemplate.opsForValue().get(dataKey(eventId));
        if (json == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(json, EventMetadata.class);
        } catch (JsonProcessingException ex) {
            log.warn("unreadable cached event metadata, treating as miss eventId={}", eventId, ex);
            return null;
        }
    }

    private void store(Long eventId, EventMetadata metadata) {
        String version = UUID.randomUUID().toString();
        Duration base = metadata.exists() ? properties.getMetadataTtl() : properties.getNullTtl();
        Duration ttl = CacheTtlJitter.apply(base, properties.getJitterRatio());
        String json;
        try {
            json = OBJECT_MAPPER.writeValueAsString(metadata);
        } catch (JsonProcessingException ex) {
            log.warn("could not serialize event metadata, skipping cache write eventId={}", eventId, ex);
            return;
        }
        redisTemplate.execute(storeScript, List.of(dataKey(eventId), versionKey(eventId)), json, version,
                String.valueOf(ttl.toMillis()));
        l1Cache.put(eventId, new CachedEventMetadata(version, metadata));
        log.debug("event metadata cached eventId={} exists={} ttlMs={}", eventId, metadata.exists(), ttl.toMillis());
    }

    private EventMetadata loadFromDb(Long eventId) {
        return eventRepository.findById(eventId)
                .map(this::toMetadata)
                .orElseGet(EventMetadata::notFound);
    }

    private EventMetadata toMetadata(Event event) {
        List<TicketTypeMetadata> ticketTypes = ticketTypeRepository.findByEventId(event.getId()).stream()
                .map(this::toMetadata)
                .toList();
        return new EventMetadata(true, event.getId(), event.getTitle(), event.getDescription(), event.getVenue(),
                event.getCity(), event.getStartTime(), event.getEndTime(), event.getStatus(), event.getBannerUrl(),
                ticketTypes);
    }

    private TicketTypeMetadata toMetadata(TicketType ticketType) {
        return new TicketTypeMetadata(ticketType.getId(), ticketType.getName(), ticketType.getDescription(),
                ticketType.getPrice(), ticketType.getSaleStartTime(), ticketType.getSaleEndTime(),
                ticketType.getStatus());
    }

    private void sleepBackoff() {
        try {
            Thread.sleep(properties.getRebuildBackoff().toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private String dataKey(Long eventId) {
        return DATA_KEY_FORMAT.formatted(eventId);
    }

    private String versionKey(Long eventId) {
        return VERSION_KEY_FORMAT.formatted(eventId);
    }

    private String lockKey(Long eventId) {
        return LOCK_KEY_FORMAT.formatted(eventId);
    }
}
