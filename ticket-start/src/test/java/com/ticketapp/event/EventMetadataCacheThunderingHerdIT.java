package com.ticketapp.event;

import com.ticketapp.application.event.EventMetadata;
import com.ticketapp.application.event.EventMetadataCacheService;
import com.ticketapp.domain.event.Event;
import com.ticketapp.domain.event.EventRepository;
import com.ticketapp.domain.ticket.TicketTypeRepository;
import com.ticketapp.domain.user.User;
import com.ticketapp.domain.user.UserRepository;
import com.ticketapp.domain.user.UserRole;
import com.ticketapp.support.AbstractIntegrationTest;
import com.ticketapp.support.Fixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
class EventMetadataCacheThunderingHerdIT extends AbstractIntegrationTest {

    private static final int READERS = 50;

    @Autowired
    EventMetadataCacheService cache;

    @Autowired
    TicketTypeRepository ticketTypeRepository;

    @Autowired
    UserRepository userRepository;

    @MockitoSpyBean
    EventRepository eventRepository;

    @Test
    void concurrentReadersOnAColdKeyProduceOneDatabaseReadAndNoErrors() throws Exception {
        User organizer = userRepository.save(Fixtures.newUser(UserRole.ORGANIZER));
        Event event = eventRepository.save(Fixtures.newEvent(organizer.getId()));
        ticketTypeRepository.save(Fixtures.newTicketType(event.getId(), 500_000L, 1000));
        cache.invalidate(event.getId());

        clearInvocations(eventRepository);

        CountDownLatch start = new CountDownLatch(1);
        List<Future<EventMetadata>> futures = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(READERS)) {
            for (int i = 0; i < READERS; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return cache.get(event.getId());
                }));
            }
            start.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }

        for (Future<EventMetadata> future : futures) {
            EventMetadata metadata = future.get();
            assertThat(metadata.exists())
                    .as("a reader that lost the rebuild lock must re-read, never fail")
                    .isTrue();
            assertThat(metadata.ticketTypes()).singleElement()
                    .satisfies(t -> assertThat(t.price()).isEqualTo(500_000L));
        }

        verify(eventRepository, times(1)).findById(event.getId());
    }
}
