package com.ticketapp.infrastructure.persistence.event;

import com.ticketapp.domain.event.Event;
import com.ticketapp.domain.event.EventRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventJpaRepository extends EventRepository, JpaRepository<Event, Long> {
}
