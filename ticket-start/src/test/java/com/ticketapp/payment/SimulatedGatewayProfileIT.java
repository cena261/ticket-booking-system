package com.ticketapp.payment;

import com.ticketapp.application.payment.PaymentGateway;
import com.ticketapp.application.payment.SepayGateway;
import com.ticketapp.application.payment.SimulatedGateway;
import com.ticketapp.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SimulatedGatewayProfileIT extends AbstractIntegrationTest {

    @Autowired
    ApplicationContext context;

    @Autowired
    PaymentGateway paymentGateway;

    @Test
    void simulatedGatewayIsUnreachableOutsidePaymentSimProfile() {
        assertThat(context.getBeanNamesForType(SimulatedGateway.class)).isEmpty();
        assertThat(paymentGateway).isInstanceOf(SepayGateway.class);
    }
}
