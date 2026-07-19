package com.ticketapp.application.payment;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

@Component
public class SepayWebhookVerifier {

    private static final String ALGORITHM = "HmacSHA256";
    private static final String SIGNATURE_PREFIX = "sha256=";

    private final SepayProperties properties;

    public SepayWebhookVerifier(SepayProperties properties) {
        this.properties = properties;
    }

    public boolean verify(byte[] rawBody, String signatureHeader, String timestampHeader) {
        if (signatureHeader == null || timestampHeader == null) {
            return false;
        }
        if (!signatureHeader.startsWith(SIGNATURE_PREFIX)) {
            return false;
        }
        long timestamp;
        try {
            timestamp = Long.parseLong(timestampHeader.trim());
        } catch (NumberFormatException ex) {
            return false;
        }
        if (isExpired(timestamp)) {
            return false;
        }
        byte[] expected = sign(timestamp, rawBody);
        byte[] provided = decodeHex(signatureHeader.substring(SIGNATURE_PREFIX.length()));
        if (provided == null) {
            return false;
        }
        return MessageDigest.isEqual(expected, provided);
    }

    private boolean isExpired(long timestamp) {
        Duration tolerance = properties.getWebhook().getTimestampTolerance();
        if (tolerance == null || tolerance.isZero() || tolerance.isNegative()) {
            return false;
        }
        long skew = Math.abs(Instant.now().getEpochSecond() - timestamp);
        return skew > tolerance.getSeconds();
    }

    private byte[] sign(long timestamp, byte[] rawBody) {
        byte[] prefix = (timestamp + ".").getBytes(StandardCharsets.UTF_8);
        byte[] message = new byte[prefix.length + rawBody.length];
        System.arraycopy(prefix, 0, message, 0, prefix.length);
        System.arraycopy(rawBody, 0, message, prefix.length, rawBody.length);
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(properties.getWebhook().getSecret().getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return mac.doFinal(message);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to compute webhook signature", ex);
        }
    }

    private static byte[] decodeHex(String hex) {
        try {
            return HexFormat.of().parseHex(hex.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
