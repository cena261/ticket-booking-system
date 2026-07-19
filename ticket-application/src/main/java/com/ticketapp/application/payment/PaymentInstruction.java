package com.ticketapp.application.payment;

import java.time.Instant;

public record PaymentInstruction(String gateway, String qrUrl, String memo, long amount, Instant expiresAt) {
}
