package com.ticketapp.infrastructure.persistence.payment;

import com.ticketapp.domain.payment.ProcessedWebhookRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Repository
public class JdbcProcessedWebhookRepository implements ProcessedWebhookRepository {

    private static final String INSERT_IGNORE =
            "INSERT IGNORE INTO processed_webhook (sepay_txn_id, received_at) VALUES (?, ?)";

    private final JdbcTemplate jdbcTemplate;

    public JdbcProcessedWebhookRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean tryInsert(long sepayTxnId, Instant receivedAt) {
        int rows = jdbcTemplate.update(INSERT_IGNORE, sepayTxnId, utc(receivedAt));
        return rows > 0;
    }

    private static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
