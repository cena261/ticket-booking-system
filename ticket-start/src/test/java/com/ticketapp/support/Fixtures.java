package com.ticketapp.support;

import com.ticketapp.domain.event.Event;
import com.ticketapp.domain.event.EventStatus;
import com.ticketapp.domain.order.Order;
import com.ticketapp.domain.order.OrderItem;
import com.ticketapp.domain.order.OrderStatus;
import com.ticketapp.domain.ticket.TicketType;
import com.ticketapp.domain.ticket.TicketTypeStatus;
import com.ticketapp.domain.user.User;
import com.ticketapp.domain.user.UserRole;
import com.ticketapp.domain.user.UserStatus;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

public final class Fixtures {

    private Fixtures() {
    }

    private static String unique() {
        return UUID.randomUUID().toString().substring(0, 12);
    }

    public static User newUser(UserRole role) {
        User user = new User();
        user.setEmail(unique() + "@demo.local");
        user.setPasswordHash("hash");
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    public static Event newEvent(Long organizerId) {
        Event event = new Event();
        event.setOrganizerId(organizerId);
        event.setTitle("Test Event");
        event.setStartTime(Instant.now().plus(30, ChronoUnit.DAYS));
        event.setEndTime(Instant.now().plus(31, ChronoUnit.DAYS));
        event.setStatus(EventStatus.PUBLISHED);
        return event;
    }

    public static TicketType newTicketType(Long eventId, long price, int stock) {
        TicketType ticketType = new TicketType();
        ticketType.setEventId(eventId);
        ticketType.setName("Standard");
        ticketType.setPrice(price);
        ticketType.setStockInitial(stock);
        ticketType.setStockAvailable(stock);
        ticketType.setSaleStartTime(Instant.now());
        ticketType.setSaleEndTime(Instant.now().plus(29, ChronoUnit.DAYS));
        ticketType.setStatus(TicketTypeStatus.ON_SALE);
        return ticketType;
    }

    public static Order newOrder(Long userId, Long eventId, Long ticketTypeId, long unitPrice, int quantity) {
        Order order = new Order();
        order.setOrderNumber("ORD-" + unique());
        order.setUserId(userId);
        order.setEventId(eventId);
        order.setStatus(OrderStatus.PENDING);
        order.setPaymentRef("PAY-" + unique());
        order.setReservedAt(Instant.now());
        order.setExpiresAt(Instant.now().plus(15, ChronoUnit.MINUTES));
        OrderItem item = OrderItem.create(ticketTypeId, unitPrice, quantity);
        order.setTotalAmount(item.getSubtotal());
        order.addItem(item);
        return order;
    }
}
