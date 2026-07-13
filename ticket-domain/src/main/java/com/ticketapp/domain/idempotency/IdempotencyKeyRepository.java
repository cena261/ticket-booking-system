package com.ticketapp.domain.idempotency;

import java.time.Instant;

public interface IdempotencyKeyRepository {

    boolean tryInsert(String token, Instant expiresAt);
}
