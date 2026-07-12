package com.ticketapp.application.order;

import com.ticketapp.application.exception.ErrorCode;

import java.time.Instant;

public record ReserveResult(boolean success, String orderNumber, Instant expiresAt, ErrorCode errorCode) {

    public static ReserveResult ok(String orderNumber, Instant expiresAt) {
        return new ReserveResult(true, orderNumber, expiresAt, null);
    }

    public static ReserveResult failed(ErrorCode errorCode) {
        return new ReserveResult(false, null, null, errorCode);
    }
}
