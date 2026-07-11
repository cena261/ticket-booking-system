package com.ticketapp.domain.order;

public enum OrderStatus {
    PENDING,
    PAID,
    CONFIRMED,
    CANCELLED,
    EXPIRED;

    public boolean canTransitionTo(OrderStatus target) {
        return switch (this) {
            case PENDING -> target == PAID || target == CANCELLED || target == EXPIRED;
            case PAID -> target == CONFIRMED || target == CANCELLED;
            case CONFIRMED -> target == CANCELLED;
            case CANCELLED, EXPIRED -> false;
        };
    }
}
