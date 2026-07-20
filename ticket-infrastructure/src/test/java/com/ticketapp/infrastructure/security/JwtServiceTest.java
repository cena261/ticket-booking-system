package com.ticketapp.infrastructure.security;

import com.ticketapp.domain.user.UserRole;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-that-is-at-least-32-bytes-long";

    @Test
    void issuesAndVerifiesToken() {
        JwtService service = new JwtService(SECRET, Duration.ofMinutes(15));

        String token = service.issueAccessToken(42L, UserRole.ORGANIZER);
        AccessClaims claims = service.verify(token);

        assertThat(claims.userId()).isEqualTo(42L);
        assertThat(claims.role()).isEqualTo(UserRole.ORGANIZER);
    }

    @Test
    void rejectsExpiredToken() throws InterruptedException {
        JwtService service = new JwtService(SECRET, Duration.ofMillis(1));

        String token = service.issueAccessToken(1L, UserRole.USER);
        Thread.sleep(20);

        assertThatThrownBy(() -> service.verify(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsTokenSignedWithDifferentSecret() {
        JwtService issuer = new JwtService(SECRET, Duration.ofMinutes(15));
        JwtService verifier = new JwtService("another-secret-that-is-at-least-32-bytes-long", Duration.ofMinutes(15));

        String token = issuer.issueAccessToken(1L, UserRole.USER);

        assertThatThrownBy(() -> verifier.verify(token)).isInstanceOf(JwtException.class);
    }
}
