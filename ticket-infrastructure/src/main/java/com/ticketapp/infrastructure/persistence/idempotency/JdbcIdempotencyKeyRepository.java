package com.ticketapp.infrastructure.persistence.idempotency;

import com.ticketapp.domain.idempotency.IdempotencyKeyRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Repository
public class JdbcIdempotencyKeyRepository implements IdempotencyKeyRepository {

    private static final String INSERT_IGNORE =
            "INSERT IGNORE INTO idempotency_key (token, created_at, expires_at) VALUES (?, ?, ?)";

    private final JdbcTemplate jdbcTemplate;

    public JdbcIdempotencyKeyRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean tryInsert(String token, Instant expiresAt) {
        int rows = jdbcTemplate.update(INSERT_IGNORE, token, utc(Instant.now()), utc(expiresAt));
        return rows > 0;
    }

    private static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
