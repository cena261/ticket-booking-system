package com.ticketapp.application.event;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

public final class CacheTtlJitter {

    private CacheTtlJitter() {
    }

    public static Duration apply(Duration base, double ratio) {
        if (ratio <= 0) {
            return base;
        }
        double factor = 1.0 + ThreadLocalRandom.current().nextDouble(-ratio, ratio);
        long millis = Math.round(base.toMillis() * factor);
        return Duration.ofMillis(Math.max(1, millis));
    }
}
