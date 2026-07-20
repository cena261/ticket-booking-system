package com.ticketapp.domain.queue;

import com.ticketapp.domain.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "order_queue")
@Getter
@Setter
@NoArgsConstructor
public class OrderQueue extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String token;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "ticket_type_id", nullable = false)
    private Long ticketTypeId;

    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.ORDINAL)
    @Column(nullable = false)
    private OrderQueueStatus status;

    @Column(name = "order_number", length = 50)
    private String orderNumber;

    @Column(length = 255)
    private String message;

    public static OrderQueue pending(String token, Long userId, Long ticketTypeId, int quantity) {
        OrderQueue queued = new OrderQueue();
        queued.token = token;
        queued.userId = userId;
        queued.ticketTypeId = ticketTypeId;
        queued.quantity = quantity;
        queued.status = OrderQueueStatus.PENDING;
        return queued;
    }
}
