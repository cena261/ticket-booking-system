package com.ticketapp.domain.payment;

import java.time.Instant;

public interface ProcessedWebhookRepository {

    boolean tryInsert(long sepayTxnId, Instant receivedAt);
}
