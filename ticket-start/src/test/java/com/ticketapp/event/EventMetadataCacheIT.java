package com.ticketapp.event;

import com.ticketapp.application.event.EventMetadata;
import com.ticketapp.application.event.EventMetadataCacheService;
import com.ticketapp.domain.event.Event;
import com.ticketapp.domain.event.EventRepository;
import com.ticketapp.domain.ticket.TicketType;
import com.ticketapp.domain.ticket.TicketTypeRepository;
import com.ticketapp.domain.user.User;
import com.ticketapp.domain.user.UserRepository;
import com.ticketapp.domain.user.UserRole;
import com.ticketapp.support.AbstractIntegrationTest;
import com.ticketapp.support.Fixtures;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.MeterNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest
class EventMetadataCacheIT extends AbstractIntegrationTest {

    @Autowired
    EventMetadataCacheService cache;

    @Autowired
    StringRedisTemplate redis;

    @Autowired
    MeterRegistry meterRegistry;

    @Autowired
    TicketTypeRepository ticketTypeRepository;

    @Autowired
    UserRepository userRepository;

    @MockitoSpyBean
    EventRepository eventRepository;

    private Event seedEvent() {
        User organizer = userRepository.save(Fixtures.newUser(UserRole.ORGANIZER));
        Event event = eventRepository.save(Fixtures.newEvent(organizer.getId()));
        ticketTypeRepository.save(Fixtures.newTicketType(event.getId(), 500_000L, 1000));
        cache.invalidate(event.getId());
        clearInvocations(eventRepository);
        return event;
    }

    private double cacheCount(String level, String result) {
        try {
            return meterRegistry.get("ticket.cache.requests").tag("level", level).tag("result", result)
                    .counter().count();
        } catch (MeterNotFoundException ex) {
            return 0;
        }
    }

    @Test
    void coldKeyRebuildsFromTheDatabaseAndPopulatesBothLevels() {
        Event event = seedEvent();

        EventMetadata metadata = cache.get(event.getId());

        assertThat(metadata.exists()).isTrue();
        assertThat(metadata.title()).isEqualTo("Test Event");
        assertThat(metadata.ticketTypes()).singleElement()
                .satisfies(t -> assertThat(t.price()).isEqualTo(500_000L));
        assertThat(redis.opsForValue().get("event:%d:data".formatted(event.getId()))).isNotNull();
        assertThat(redis.opsForValue().get("event:%d:ver".formatted(event.getId()))).isNotNull();
    }

    @Test
    void warmKeyServesFromL1WithoutTouchingTheDatabase() {
        Event event = seedEvent();
        cache.get(event.getId());
        clearInvocations(eventRepository);
        double hitsBefore = cacheCount("l1", "hit");

        cache.get(event.getId());

        assertThat(cacheCount("l1", "hit")).isEqualTo(hitsBefore + 1);
        verify(eventRepository, never()).findById(event.getId());
    }

    @Test
    void versionMismatchRefetchesTheObjectFromL2RatherThanServingStaleLocalData() {
        Event event = seedEvent();
        cache.get(event.getId());
        double l2HitsBefore = cacheCount("l2", "hit");

        redis.opsForValue().set("event:%d:ver".formatted(event.getId()), "a-different-version");

        EventMetadata metadata = cache.get(event.getId());

        assertThat(metadata.exists()).isTrue();
        assertThat(cacheCount("l2", "hit")).isEqualTo(l2HitsBefore + 1);
    }

    @Test
    void cachedObjectCarriesNoStockValue() {
        Event event = seedEvent();
        cache.get(event.getId());

        String json = redis.opsForValue().get("event:%d:data".formatted(event.getId()));

        assertThat(json).isNotNull();
        assertThat(json.toLowerCase()).doesNotContain("stock");
    }

    @Test
    void nullSentinelStopsRepeatedDatabaseHitsForAnEventThatDoesNotExist() {
        long unknownId = 9_876_543L;
        cache.invalidate(unknownId);
        clearInvocations(eventRepository);

        assertThat(cache.get(unknownId).exists()).isFalse();
        verify(eventRepository, atLeastOnce()).findById(unknownId);

        clearInvocations(eventRepository);
        assertThat(cache.get(unknownId).exists()).isFalse();
        verify(eventRepository, never()).findById(unknownId);
    }

    @Test
    void invalidateClearsBothRedisKeysSoTheNextReadRebuilds() {
        Event event = seedEvent();
        cache.get(event.getId());

        cache.invalidate(event.getId());

        assertThat(redis.opsForValue().get("event:%d:data".formatted(event.getId()))).isNull();
        assertThat(redis.opsForValue().get("event:%d:ver".formatted(event.getId()))).isNull();

        clearInvocations(eventRepository);
        assertThat(cache.get(event.getId()).exists()).isTrue();
        verify(eventRepository, atLeastOnce()).findById(event.getId());
    }
}
