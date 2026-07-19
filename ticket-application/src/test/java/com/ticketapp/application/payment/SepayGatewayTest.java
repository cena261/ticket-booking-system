package com.ticketapp.application.payment;

import com.ticketapp.domain.order.Order;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SepayGatewayTest {

    @Test
    void buildsQrUrlWithUniqueMemoAndAmount() {
        SepayProperties properties = new SepayProperties();
        properties.setAccountNumber("0123456789");
        properties.setBankCode("MBBank");

        Order order = new Order();
        order.setPaymentRef("TKTABC123DEF456");
        order.setTotalAmount(500000);
        order.setExpiresAt(Instant.parse("2026-07-19T10:00:00Z"));

        PaymentInstruction instruction = new SepayGateway(properties).createInstruction(order);

        assertThat(instruction.gateway()).isEqualTo("sepay");
        assertThat(instruction.memo()).isEqualTo("TKTABC123DEF456");
        assertThat(instruction.amount()).isEqualTo(500000);
        assertThat(instruction.expiresAt()).isEqualTo(Instant.parse("2026-07-19T10:00:00Z"));
        assertThat(instruction.qrUrl())
                .isEqualTo("https://qr.sepay.vn/img?acc=0123456789&bank=MBBank&amount=500000&des=TKTABC123DEF456");
    }
}
