package com.ticketapp.application.event;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class CacheTtlJitterTest {

    private static final Duration BASE = Duration.ofMinutes(10);

    @Test
    void jitteredTtlStaysWithinTheConfiguredBounds() {
        IntStream.range(0, 1000).forEach(i -> {
            Duration ttl = CacheTtlJitter.apply(BASE, 0.2);
            assertThat(ttl.toMillis())
                    .isBetween((long) (BASE.toMillis() * 0.8), (long) (BASE.toMillis() * 1.2));
        });
    }

    @Test
    void jitterActuallySpreadsExpiryRatherThanReturningOneValue() {
        long distinct = IntStream.range(0, 200)
                .mapToLong(i -> CacheTtlJitter.apply(BASE, 0.2).toMillis())
                .distinct()
                .count();

        assertThat(distinct)
                .as("identical TTLs across keys is the avalanche this jitter exists to prevent")
                .isGreaterThan(100);
    }

    @Test
    void zeroRatioDisablesJitter() {
        assertThat(CacheTtlJitter.apply(BASE, 0)).isEqualTo(BASE);
    }

    @Test
    void misconfiguredRatioAboveOneStillYieldsAUsefulTtlRatherThanCollapsingToNothing() {
        IntStream.range(0, 200).forEach(i -> assertThat(CacheTtlJitter.apply(BASE, 5.0).toMillis())
                .as("an out-of-range ratio must not silently disable the cache with a ~0 TTL")
                .isGreaterThan((long) (BASE.toMillis() * 0.05)));
    }
}
