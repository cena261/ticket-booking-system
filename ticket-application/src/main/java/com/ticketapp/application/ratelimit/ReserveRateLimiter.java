package com.ticketapp.application.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ticketapp.application.exception.AppException;
import com.ticketapp.application.exception.ErrorCode;
import com.ticketapp.application.observability.BuyPathMetrics;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class ReserveRateLimiter {

    private final RateLimiterConfig config;
    private final Cache<Long, RateLimiter> limiters;
    private final BuyPathMetrics metrics;

    public ReserveRateLimiter(ReserveRateLimitProperties properties, BuyPathMetrics metrics) {
        this.config = RateLimiterConfig.custom()
                .limitForPeriod(properties.getLimitForPeriod())
                .limitRefreshPeriod(properties.getLimitRefreshPeriod())
                .timeoutDuration(Duration.ZERO)
                .build();
        this.limiters = Caffeine.newBuilder()
                .maximumSize(properties.getMaximumUsers())
                .expireAfterAccess(properties.getUserExpireAfterAccess())
                .build();
        this.metrics = metrics;
    }

    public void check(Long userId) {
        RateLimiter limiter = limiters.get(userId, id -> RateLimiter.of("reserve-user-" + id, config));
        if (!limiter.acquirePermission()) {
            metrics.rateLimiterRejected();
            throw new AppException(ErrorCode.RATE_LIMIT_EXCEEDED);
        }
    }
}
