package com.ticketapp.persistence;

import com.ticketapp.domain.event.Event;
import com.ticketapp.domain.event.EventRepository;
import com.ticketapp.domain.ticket.TicketType;
import com.ticketapp.domain.ticket.TicketTypeRepository;
import com.ticketapp.domain.user.User;
import com.ticketapp.domain.user.UserRepository;
import com.ticketapp.domain.user.UserRole;
import com.ticketapp.support.AbstractIntegrationTest;
import com.ticketapp.support.Fixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TicketTypeRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    TicketTypeRepository ticketTypeRepository;

    @Autowired
    EventRepository eventRepository;

    @Autowired
    UserRepository userRepository;

    @Test
    void savesAndFindsByEventId() {
        User organizer = userRepository.save(Fixtures.newUser(UserRole.ORGANIZER));
        Event event = eventRepository.save(Fixtures.newEvent(organizer.getId()));

        TicketType saved = ticketTypeRepository.save(Fixtures.newTicketType(event.getId(), 500000, 1000));

        assertThat(saved.getId()).isNotNull();
        assertThat(ticketTypeRepository.findByEventId(event.getId()))
                .extracting(TicketType::getId)
                .containsExactly(saved.getId());
    }
}
