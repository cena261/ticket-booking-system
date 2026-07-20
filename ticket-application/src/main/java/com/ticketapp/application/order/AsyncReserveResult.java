package com.ticketapp.application.order;

import com.ticketapp.application.exception.ErrorCode;

public record AsyncReserveResult(boolean success, String token, ErrorCode errorCode) {

    public static AsyncReserveResult ok(String token) {
        return new AsyncReserveResult(true, token, null);
    }

    public static AsyncReserveResult failed(ErrorCode errorCode) {
        return new AsyncReserveResult(false, null, errorCode);
    }
}
