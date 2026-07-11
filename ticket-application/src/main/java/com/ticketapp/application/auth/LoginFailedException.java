package com.ticketapp.application.auth;

public class LoginFailedException extends RuntimeException {

    public LoginFailedException() {
        super("invalid email or password");
    }
}
