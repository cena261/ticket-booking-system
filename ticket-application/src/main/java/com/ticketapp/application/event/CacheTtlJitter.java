package com.ticketapp.application.event;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

public final class CacheTtlJitter {

    private CacheTtlJitter() {
    }

    private static final double MAX_RATIO = 0.9;

    public static Duration apply(Duration base, double ratio) {
        if (ratio <= 0) {
            return base;
        }
        double bounded = Math.min(ratio, MAX_RATIO);
        double factor = 1.0 + ThreadLocalRandom.current().nextDouble(-bounded, bounded);
        long millis = Math.round(base.toMillis() * factor);
        return Duration.ofMillis(Math.max(1, millis));
    }
}
