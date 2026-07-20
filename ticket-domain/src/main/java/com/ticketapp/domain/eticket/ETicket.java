package com.ticketapp.domain.eticket;

import com.ticketapp.domain.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "e_tickets")
@Getter
@NoArgsConstructor
public class ETicket extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "ticket_type_id", nullable = false)
    private Long ticketTypeId;

    @Column(name = "ticket_code", nullable = false, unique = true, length = 40)
    private String ticketCode;

    public static ETicket issue(Long orderId, Long ticketTypeId, String ticketCode) {
        ETicket ticket = new ETicket();
        ticket.orderId = orderId;
        ticket.ticketTypeId = ticketTypeId;
        ticket.ticketCode = ticketCode;
        return ticket;
    }
}
