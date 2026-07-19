package com.ticketapp.application.payment;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentMemoTest {

    @Test
    void extractsPaymentRefFromBankContent() {
        assertThat(PaymentMemo.extract("TKTABC123DEF456 chuyen tien"))
                .contains("TKTABC123DEF456");
    }

    @Test
    void extractsPaymentRefEmbeddedInNoise() {
        assertThat(PaymentMemo.extract("NGUYEN VAN A thanh toan TKT0A1B2C3D4E5F cam on"))
                .contains("TKT0A1B2C3D4E5F");
    }

    @Test
    void normalisesLowercaseToUppercase() {
        assertThat(PaymentMemo.extract("tktabc123def456"))
                .contains("TKTABC123DEF456");
    }

    @Test
    void rejectsMissingOrMalformedMemo() {
        assertThat(PaymentMemo.extract("no code here")).isEmpty();
        assertThat(PaymentMemo.extract("TKTSHORT")).isEmpty();
        assertThat(PaymentMemo.extract(null)).isEmpty();
        assertThat(PaymentMemo.extract("")).isEmpty();
    }
}
