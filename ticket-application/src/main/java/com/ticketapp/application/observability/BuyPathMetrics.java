package com.ticketapp.application.observability;

import com.ticketapp.domain.outbox.OutboxEventRepository;
import com.ticketapp.domain.outbox.OutboxStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Single home for the buy-path business metrics. Centralizing meter names and tags here keeps them
 * consistent across the sync/async reserve paths, the outbox publisher, and (later) payment, cache,
 * and rate-limiter code, so a dashboard query never has to chase a renamed meter.
 *
 * Meter names use dots; the Prometheus registry renders them as `ticket_*` with an `_total` suffix
 * on counters. The `instance` tag is applied globally by MetricsConfig, not here.
 */
@Component
public class BuyPathMetrics {

    private static final String RESERVE_TIMER = "ticket.reserve";
    private static final String OVERSELL_PREVENTED = "ticket.oversell.prevented";
    private static final String PAYMENT_CONFIRMED = "ticket.payment.confirmed";
    private static final String RATE_LIMITER_REJECTIONS = "ticket.ratelimiter.rejections";
    private static final String CACHE_REQUESTS = "ticket.cache.requests";
    private static final String OUTBOX_PENDING = "ticket.outbox.pending";

    private final MeterRegistry registry;
    private final Counter oversellPrevented;
    private final Counter paymentConfirmed;
    private final Counter rateLimiterRejections;

    public BuyPathMetrics(MeterRegistry registry, OutboxEventRepository outboxEventRepository) {
        this.registry = registry;
        this.oversellPrevented = Counter.builder(OVERSELL_PREVENTED)
                .description("Reserve attempts rejected because stock was exhausted (oversell prevented)")
                .register(registry);
        this.paymentConfirmed = Counter.builder(PAYMENT_CONFIRMED)
                .description("Payments confirmed via SePay webhook (wired in Phase 7)")
                .register(registry);
        this.rateLimiterRejections = Counter.builder(RATE_LIMITER_REJECTIONS)
                .description("Requests rejected by the per-user rate limiter (wired in Phase 15)")
                .register(registry);

        // Pre-register the cache counters at zero so Phase 8c's success metric and any dashboard
        // query exist from the first scrape, before a single cache lookup has happened.
        cacheCounter("l1", "hit");
        cacheCounter("l1", "miss");
        cacheCounter("l2", "hit");
        cacheCounter("l2", "miss");

        // Async-settlement lag: rows still waiting to be published to Kafka. A gauge polls on scrape,
        // so it always reflects the live backlog without any hot-path cost.
        Gauge.builder(OUTBOX_PENDING, outboxEventRepository, repo -> repo.countByStatus(OutboxStatus.PENDING))
                .description("Outbox rows awaiting Kafka publish (async settlement backlog)")
                .register(registry);
    }

    /**
     * Records one reserve attempt. {@code mode} is sync|async, {@code outcome} is the terminal state
     * (success, out_of_stock, conflict, not_on_sale, not_found, error). Callers pass the start value
     * captured with {@link System#nanoTime()} at entry.
     */
    public void recordReserve(String mode, String outcome, long startNanos) {
        Timer.builder(RESERVE_TIMER)
                .tag("mode", mode)
                .tag("outcome", outcome)
                .register(registry)
                .record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
    }

    public void oversellPrevented() {
        oversellPrevented.increment();
    }

    public void paymentConfirmed() {
        paymentConfirmed.increment();
    }

    public void rateLimiterRejected() {
        rateLimiterRejections.increment();
    }

    public void cacheHit(String level) {
        cacheCounter(level, "hit").increment();
    }

    public void cacheMiss(String level) {
        cacheCounter(level, "miss").increment();
    }

    private Counter cacheCounter(String level, String result) {
        return Counter.builder(CACHE_REQUESTS)
                .description("Metadata cache lookups by level (l1/l2) and result (hit/miss)")
                .tag("level", level)
                .tag("result", result)
                .register(registry);
    }
}
