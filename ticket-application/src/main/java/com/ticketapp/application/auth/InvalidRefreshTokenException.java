package com.ticketapp.application.auth;

public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException() {
        super("invalid refresh token");
    }
}
