package com.ticketapp.persistence;

import com.ticketapp.domain.event.Event;
import com.ticketapp.domain.event.EventRepository;
import com.ticketapp.domain.user.User;
import com.ticketapp.domain.user.UserRepository;
import com.ticketapp.domain.user.UserRole;
import com.ticketapp.support.AbstractIntegrationTest;
import com.ticketapp.support.Fixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class EventRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    EventRepository eventRepository;

    @Autowired
    UserRepository userRepository;

    @Test
    void savesAndFindsById() {
        User organizer = userRepository.save(Fixtures.newUser(UserRole.ORGANIZER));

        Event saved = eventRepository.save(Fixtures.newEvent(organizer.getId()));

        assertThat(saved.getId()).isNotNull();
        assertThat(eventRepository.findById(saved.getId()))
                .get()
                .extracting(Event::getOrganizerId)
                .isEqualTo(organizer.getId());
    }

    @Test
    void rejectsEventWithUnknownOrganizer() {
        Event orphan = Fixtures.newEvent(999999L);

        assertThatThrownBy(() -> eventRepository.save(orphan))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
