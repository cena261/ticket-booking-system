package com.ticketapp.application.auth;

import com.ticketapp.domain.user.UserRole;

public record AuthenticatedUser(Long id, String email, UserRole role) {
}
