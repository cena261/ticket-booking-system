package com.ticketapp.application.payment;

import com.ticketapp.domain.order.Order;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Primary
@Profile("payment-sim")
public class SimulatedGateway implements PaymentGateway {

    private static final String NAME = "simulated";

    @Override
    public PaymentInstruction createInstruction(Order order) {
        String memo = order.getPaymentRef();
        String qrUrl = "sim://payment/%s?amount=%d".formatted(memo, order.getTotalAmount());
        return new PaymentInstruction(NAME, qrUrl, memo, order.getTotalAmount(), order.getExpiresAt());
    }
}
