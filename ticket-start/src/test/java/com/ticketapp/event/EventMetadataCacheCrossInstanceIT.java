package com.ticketapp.event;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ticketapp.application.event.CachedEventMetadata;
import com.ticketapp.application.event.EventCacheProperties;
import com.ticketapp.application.event.EventMetadataCacheService;
import com.ticketapp.application.observability.BuyPathMetrics;
import com.ticketapp.domain.event.Event;
import com.ticketapp.domain.event.EventRepository;
import com.ticketapp.domain.ticket.TicketType;
import com.ticketapp.domain.ticket.TicketTypeRepository;
import com.ticketapp.domain.user.User;
import com.ticketapp.domain.user.UserRepository;
import com.ticketapp.domain.user.UserRole;
import com.ticketapp.infrastructure.lock.DistributedLockService;
import com.ticketapp.support.AbstractIntegrationTest;
import com.ticketapp.support.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class EventMetadataCacheCrossInstanceIT extends AbstractIntegrationTest {

    @Autowired
    EventMetadataCacheService instanceA;

    @Autowired
    StringRedisTemplate redis;

    @Autowired
    DistributedLockService lockService;

    @Autowired
    EventRepository eventRepository;

    @Autowired
    TicketTypeRepository ticketTypeRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    BuyPathMetrics metrics;

    @Autowired
    EventCacheProperties properties;

    private EventMetadataCacheService instanceB;

    @BeforeEach
    void createSecondInstance() {
        Cache<Long, CachedEventMetadata> separateL1 = Caffeine.newBuilder()
                .maximumSize(properties.getMaximumSize())
                .expireAfterWrite(Duration.ofMinutes(10))
                .build();
        instanceB = new EventMetadataCacheService(separateL1, redis, lockService, eventRepository,
                ticketTypeRepository, metrics, properties);
    }

    @Test
    void instanceBServesFreshMetadataAfterInstanceAChangesIt() {
        User organizer = userRepository.save(Fixtures.newUser(UserRole.ORGANIZER));
        Event event = eventRepository.save(Fixtures.newEvent(organizer.getId()));
        TicketType ticketType = ticketTypeRepository.save(Fixtures.newTicketType(event.getId(), 500_000L, 1000));
        instanceA.invalidate(event.getId());

        assertThat(instanceA.get(event.getId()).ticketTypes().getFirst().price()).isEqualTo(500_000L);
        assertThat(instanceB.get(event.getId()).ticketTypes().getFirst().price())
                .as("instance B warms its own L1 from the shared L2")
                .isEqualTo(500_000L);

        ticketType.setPrice(750_000L);
        ticketTypeRepository.save(ticketType);
        instanceA.invalidate(event.getId());

        assertThat(instanceA.get(event.getId()).ticketTypes().getFirst().price()).isEqualTo(750_000L);
        assertThat(instanceB.get(event.getId()).ticketTypes().getFirst().price())
                .as("instance B must not serve its stale L1 copy after instance A invalidated the event")
                .isEqualTo(750_000L);
    }

    @Test
    void instanceBDetectsStalenessByVersionRatherThanWaitingForItsLocalTtlToLapse() {
        User organizer = userRepository.save(Fixtures.newUser(UserRole.ORGANIZER));
        Event event = eventRepository.save(Fixtures.newEvent(organizer.getId()));
        TicketType ticketType = ticketTypeRepository.save(Fixtures.newTicketType(event.getId(), 500_000L, 1000));
        instanceA.invalidate(event.getId());

        instanceB.get(event.getId());
        String versionSeenByB = redis.opsForValue().get("event:%d:ver".formatted(event.getId()));

        ticketType.setPrice(900_000L);
        ticketTypeRepository.save(ticketType);
        instanceA.invalidate(event.getId());
        instanceA.get(event.getId());

        String versionAfterRebuild = redis.opsForValue().get("event:%d:ver".formatted(event.getId()));
        assertThat(versionAfterRebuild)
                .as("a rebuild must mint a new version, which is what makes instance B's copy detectably stale")
                .isNotEqualTo(versionSeenByB);

        assertThat(instanceB.get(event.getId()).ticketTypes().getFirst().price())
                .as("B's L1 entry is still within its 10m TTL, so only the version check can catch the staleness")
                .isEqualTo(900_000L);
    }
}
