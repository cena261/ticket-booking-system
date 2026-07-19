package com.ticketapp.controller.payment;

import com.ticketapp.application.payment.PaymentInstruction;

import java.time.Instant;

public record PayResponse(String gateway, String qrUrl, String memo, long amount, Instant expiresAt) {

    public static PayResponse from(PaymentInstruction instruction) {
        return new PayResponse(instruction.gateway(), instruction.qrUrl(), instruction.memo(),
                instruction.amount(), instruction.expiresAt());
    }
}
