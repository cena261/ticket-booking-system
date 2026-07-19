package com.ticketapp.application.payment;

import com.ticketapp.domain.order.Order;

public interface PaymentGateway {

    PaymentInstruction createInstruction(Order order);
}
