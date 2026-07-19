package com.ticketapp.application.payment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class SepayWebhookVerifierTest {

    private static final String SECRET = "test-webhook-secret";

    private SepayProperties properties;
    private SepayWebhookVerifier verifier;

    @BeforeEach
    void setUp() {
        properties = new SepayProperties();
        properties.getWebhook().setSecret(SECRET);
        properties.getWebhook().setTimestampTolerance(Duration.ofMinutes(5));
        verifier = new SepayWebhookVerifier(properties);
    }

    @Test
    void acceptsValidSignature() {
        byte[] body = "{\"id\":1,\"transferAmount\":500000}".getBytes(StandardCharsets.UTF_8);
        long ts = Instant.now().getEpochSecond();
        assertThat(verifier.verify(body, "sha256=" + hmac(SECRET, ts, body), String.valueOf(ts))).isTrue();
    }

    @Test
    void rejectsTamperedBody() {
        byte[] signed = "{\"transferAmount\":500000}".getBytes(StandardCharsets.UTF_8);
        byte[] tampered = "{\"transferAmount\":999999}".getBytes(StandardCharsets.UTF_8);
        long ts = Instant.now().getEpochSecond();
        assertThat(verifier.verify(tampered, "sha256=" + hmac(SECRET, ts, signed), String.valueOf(ts))).isFalse();
    }

    @Test
    void rejectsWrongSecret() {
        byte[] body = "{\"id\":1}".getBytes(StandardCharsets.UTF_8);
        long ts = Instant.now().getEpochSecond();
        assertThat(verifier.verify(body, "sha256=" + hmac("other-secret", ts, body), String.valueOf(ts))).isFalse();
    }

    @Test
    void rejectsMissingHeaders() {
        byte[] body = "{\"id\":1}".getBytes(StandardCharsets.UTF_8);
        long ts = Instant.now().getEpochSecond();
        assertThat(verifier.verify(body, null, String.valueOf(ts))).isFalse();
        assertThat(verifier.verify(body, "sha256=" + hmac(SECRET, ts, body), null)).isFalse();
    }

    @Test
    void rejectsExpiredTimestamp() {
        byte[] body = "{\"id\":1}".getBytes(StandardCharsets.UTF_8);
        long ts = Instant.now().getEpochSecond() - Duration.ofMinutes(10).getSeconds();
        assertThat(verifier.verify(body, "sha256=" + hmac(SECRET, ts, body), String.valueOf(ts))).isFalse();
    }

    @Test
    void rejectsSignatureWithoutPrefix() {
        byte[] body = "{\"id\":1}".getBytes(StandardCharsets.UTF_8);
        long ts = Instant.now().getEpochSecond();
        assertThat(verifier.verify(body, hmac(SECRET, ts, body), String.valueOf(ts))).isFalse();
    }

    private static String hmac(String secret, long timestamp, byte[] body) {
        try {
            byte[] prefix = (timestamp + ".").getBytes(StandardCharsets.UTF_8);
            byte[] message = new byte[prefix.length + body.length];
            System.arraycopy(prefix, 0, message, 0, prefix.length);
            System.arraycopy(body, 0, message, prefix.length, body.length);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(message));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
