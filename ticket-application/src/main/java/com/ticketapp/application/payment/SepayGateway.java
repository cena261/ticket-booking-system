package com.ticketapp.application.payment;

import com.ticketapp.domain.order.Order;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;

@Component
@Profile("!payment-sim")
public class SepayGateway implements PaymentGateway {

    private static final String NAME = "sepay";

    private final SepayProperties properties;

    public SepayGateway(SepayProperties properties) {
        this.properties = properties;
    }

    @Override
    public PaymentInstruction createInstruction(Order order) {
        String memo = order.getPaymentRef();
        String qrUrl = "%s?acc=%s&bank=%s&amount=%d&des=%s".formatted(
                properties.getQrBaseUrl(),
                encode(properties.getAccountNumber()),
                encode(properties.getBankCode()),
                order.getTotalAmount(),
                encode(memo));
        return new PaymentInstruction(NAME, qrUrl, memo, order.getTotalAmount(), order.getExpiresAt());
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
