package com.ticketapp.application.notification;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Runs best-effort SMTP sends behind a SemaphoreBulkhead + TimeLimiter so a hung mail server
 * can occupy at most maxConcurrentCalls permits and never holds a sender thread past the timeout.
 * ThreadPoolBulkhead is avoided on purpose: virtual threads are enabled and it would conflict.
 */
@Slf4j
@Component
public class EmailDispatcher {

    private final Bulkhead bulkhead;
    private final TimeLimiter timeLimiter;
    private final ExecutorService sendExecutor;

    public EmailDispatcher(NotificationProperties properties) {
        this.bulkhead = Bulkhead.of("smtp", BulkheadConfig.custom()
                .maxConcurrentCalls(properties.getBulkhead().getMaxConcurrentCalls())
                .maxWaitDuration(properties.getBulkhead().getMaxWait())
                .build());
        this.timeLimiter = TimeLimiter.of("smtp", TimeLimiterConfig.custom()
                .timeoutDuration(properties.getTimeout())
                .cancelRunningFuture(true)
                .build());
        this.sendExecutor = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("smtp-send-", 0).factory());
    }

    public void dispatch(String description, Runnable send) {
        Callable<Void> timed = TimeLimiter.decorateFutureSupplier(timeLimiter,
                () -> sendExecutor.submit(() -> {
                    send.run();
                    return null;
                }));
        try {
            Bulkhead.decorateCallable(bulkhead, timed).call();
        } catch (BulkheadFullException ex) {
            log.warn("email dropped, smtp bulkhead full: {}", description);
        } catch (Exception ex) {
            log.warn("email send failed: {}", description, ex);
        }
    }
}
