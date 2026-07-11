package com.ticketapp.application.auth;

public class EmailAlreadyUsedException extends RuntimeException {

    public EmailAlreadyUsedException() {
        super("email already used");
    }
}
