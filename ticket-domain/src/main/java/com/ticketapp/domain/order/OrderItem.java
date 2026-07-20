package com.ticketapp.domain.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "order_items")
@Getter
@NoArgsConstructor
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "ticket_type_id", nullable = false)
    private Long ticketTypeId;

    @Column(name = "unit_price", nullable = false)
    private long unitPrice;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private long subtotal;

    public static OrderItem create(Long ticketTypeId, long unitPrice, int quantity) {
        if (unitPrice < 0) {
            throw new IllegalArgumentException("unitPrice must be non-negative");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        OrderItem item = new OrderItem();
        item.ticketTypeId = ticketTypeId;
        item.unitPrice = unitPrice;
        item.quantity = quantity;
        item.subtotal = unitPrice * quantity;
        return item;
    }

    void assignTo(Order order) {
        this.order = order;
    }
}
