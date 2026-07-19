package com.ticketapp.domain.eticket;

import java.util.List;

public interface ETicketRepository {

    ETicket save(ETicket ticket);

    List<ETicket> findByOrderId(Long orderId);

    long countByOrderId(Long orderId);
}
