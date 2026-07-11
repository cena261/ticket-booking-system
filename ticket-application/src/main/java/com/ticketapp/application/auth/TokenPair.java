package com.ticketapp.application.auth;

public record TokenPair(String accessToken, String refreshToken, long expiresInSeconds) {
}
