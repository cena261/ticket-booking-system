package com.ticketapp.infrastructure.persistence.ticket;

import com.ticketapp.domain.ticket.TicketType;
import com.ticketapp.domain.ticket.TicketTypeRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface TicketTypeJpaRepository extends TicketTypeRepository, JpaRepository<TicketType, Long> {

    @Override
    @Transactional
    @Modifying
    @Query("UPDATE TicketType t SET t.stockAvailable = t.stockAvailable - :quantity "
            + "WHERE t.id = :id AND t.stockAvailable >= :quantity")
    int decreaseStock(@Param("id") Long id, @Param("quantity") int quantity);

    @Override
    @Transactional
    @Modifying
    @Query("UPDATE TicketType t SET t.stockAvailable = t.stockAvailable + :quantity WHERE t.id = :id")
    int increaseStock(@Param("id") Long id, @Param("quantity") int quantity);
}
