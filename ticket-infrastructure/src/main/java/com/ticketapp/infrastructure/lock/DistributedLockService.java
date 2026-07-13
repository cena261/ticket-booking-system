package com.ticketapp.infrastructure.lock;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Redisson-backed distributed lock. Prevents two instances doing the same work concurrently, so it
 * removes wasted effort and contention.
 *
 * It is not a correctness guarantee on its own: a lease can expire while the holder is still
 * running (long GC pause, slow query), after which a second holder may enter the same section.
 * Every critical section under this lock must therefore also carry its own conditional guard,
 * such as the status-conditional UPDATE used to claim an order for expiry.
 */
@Service
public class DistributedLockService {

    private final RedissonClient redisson;

    public DistributedLockService(RedissonClient redisson) {
        this.redisson = redisson;
    }

    /**
     * Runs the action while holding the lock. Returns false without running it if the lock could
     * not be acquired within waitTime.
     */
    public boolean tryRun(String key, Duration waitTime, Duration leaseTime, Runnable action) {
        RLock lock = redisson.getLock(key);
        boolean acquired;
        try {
            acquired = lock.tryLock(waitTime.toMillis(), leaseTime.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
        if (!acquired) {
            return false;
        }
        try {
            action.run();
            return true;
        } finally {
            // The lease may have expired mid-action, in which case the lock is no longer ours and
            // unlocking would throw.
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
