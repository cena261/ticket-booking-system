package com.ticketapp.application.order;

import com.ticketapp.domain.order.Order;
import com.ticketapp.domain.order.OrderItem;
import com.ticketapp.domain.order.OrderRepository;
import com.ticketapp.domain.order.OrderStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;

@Service
public class OrderCreationService {

    private static final char[] ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OrderRepository orderRepository;
    private final Duration reservationTtl;

    public OrderCreationService(OrderRepository orderRepository,
                                @Value("${order.reservation-ttl}") Duration reservationTtl) {
        this.orderRepository = orderRepository;
        this.reservationTtl = reservationTtl;
    }

    public Order createPendingOrder(Long userId, Long eventId, Long ticketTypeId, long unitPrice, int quantity) {
        Instant now = Instant.now();
        OrderItem item = OrderItem.create(ticketTypeId, unitPrice, quantity);

        Order order = new Order();
        order.setOrderNumber("ORD" + token(16));
        order.setUserId(userId);
        order.setEventId(eventId);
        order.setStatus(OrderStatus.PENDING);
        order.setTotalAmount(item.getSubtotal());
        order.setPaymentRef("TKT" + token(12));
        order.setReservedAt(now);
        order.setExpiresAt(now.plus(reservationTtl));
        order.addItem(item);

        return orderRepository.save(order);
    }

    private static String token(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(ALPHANUMERIC[RANDOM.nextInt(ALPHANUMERIC.length)]);
        }
        return builder.toString();
    }
}
