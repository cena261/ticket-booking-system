package com.ticketapp.infrastructure.security;

import com.ticketapp.domain.user.UserRole;

public record AccessClaims(Long userId, UserRole role) {
}
