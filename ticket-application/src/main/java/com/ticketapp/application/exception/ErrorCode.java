package com.ticketapp.application.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    UNCATEGORIZED(9999, "Uncategorized error", HttpStatus.INTERNAL_SERVER_ERROR),
    VALIDATION_ERROR(1001, "Invalid request", HttpStatus.BAD_REQUEST),
    EMAIL_ALREADY_USED(1002, "Email already used", HttpStatus.CONFLICT),
    LOGIN_FAILED(1003, "Invalid email or password", HttpStatus.UNAUTHORIZED),
    INVALID_REFRESH_TOKEN(1004, "Invalid refresh token", HttpStatus.UNAUTHORIZED),
    UNAUTHENTICATED(1005, "Unauthenticated", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(1006, "You do not have permission", HttpStatus.FORBIDDEN),
    TICKET_TYPE_NOT_FOUND(2001, "Ticket type not found", HttpStatus.NOT_FOUND),
    OUT_OF_STOCK(2002, "Not enough tickets available", HttpStatus.CONFLICT),
    STOCK_CONFLICT(2003, "Stock changed during reservation, please retry", HttpStatus.CONFLICT),
    RESERVE_FAILED(2004, "Could not reserve tickets", HttpStatus.INTERNAL_SERVER_ERROR),
    ORDER_TOKEN_NOT_FOUND(2005, "Order token not found", HttpStatus.NOT_FOUND),
    TICKET_TYPE_NOT_ON_SALE(2006, "Ticket type is not on sale", HttpStatus.CONFLICT),
    ILLEGAL_ORDER_TRANSITION(2007, "Illegal order state transition", HttpStatus.CONFLICT),
    EVENT_NOT_FOUND(2008, "Event not found", HttpStatus.NOT_FOUND),
    RATE_LIMIT_EXCEEDED(2009, "Too many reserve requests, please slow down", HttpStatus.TOO_MANY_REQUESTS);

    private final int code;
    private final String message;
    private final HttpStatus status;

    ErrorCode(int code, String message, HttpStatus status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }
}
