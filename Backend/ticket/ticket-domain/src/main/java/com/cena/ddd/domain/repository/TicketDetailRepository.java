package com.cena.ddd.domain.repository;

import com.cena.ddd.domain.model.entity.TicketDetail;

import java.util.Optional;

public interface TicketDetailRepository {
    Optional<TicketDetail> findById(Long id);
}
