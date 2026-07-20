package com.ticketapp.application.order;

import com.ticketapp.application.eticket.ETicketService;
import com.ticketapp.application.notification.OrderConfirmedEvent;
import com.ticketapp.domain.eticket.ETicket;
import com.ticketapp.domain.order.Order;
import com.ticketapp.domain.order.OrderRepository;
import com.ticketapp.domain.order.OrderStatus;
import com.ticketapp.domain.user.User;
import com.ticketapp.domain.user.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class OrderFinalizationService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final ETicketService eTicketService;
    private final ApplicationEventPublisher eventPublisher;

    public OrderFinalizationService(OrderRepository orderRepository,
                                    UserRepository userRepository,
                                    ETicketService eTicketService,
                                    ApplicationEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.eTicketService = eTicketService;
        this.eventPublisher = eventPublisher;
    }

    public void finalizeConfirmed(Order order, Instant paidAt) {
        order.transitionTo(OrderStatus.PAID);
        order.setPaidAt(paidAt);
        order.transitionTo(OrderStatus.CONFIRMED);
        List<ETicket> tickets = eTicketService.issueFor(order);
        orderRepository.save(order);

        String recipient = userRepository.findById(order.getUserId()).map(User::getEmail).orElse(null);
        List<String> codes = tickets.stream().map(ETicket::getTicketCode).toList();
        eventPublisher.publishEvent(new OrderConfirmedEvent(order.getOrderNumber(), recipient,
                order.getTotalAmount(), codes));
    }
}
