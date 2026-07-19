package com.ticketapp.application.payment;

public enum WebhookOutcome {
    CONFIRMED,
    DUPLICATE,
    AMOUNT_MISMATCH,
    REFUND_REQUIRED,
    UNMATCHED,
    IGNORED
}
