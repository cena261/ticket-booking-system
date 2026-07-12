package com.ticketapp.controller.order;

import java.time.Instant;

public record ReserveResponse(String orderNumber, Instant expiresAt) {
}
