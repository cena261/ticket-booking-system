package com.ticketapp.infrastructure.lock;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class DistributedLockService {

    private final RedissonClient redisson;

    public DistributedLockService(RedissonClient redisson) {
        this.redisson = redisson;
    }

    public boolean tryRun(String key, Duration waitTime, Duration leaseTime, Runnable action) {
        RLock lock = redisson.getLock(key);
        boolean acquired;
        try {
            acquired = lock.tryLock(waitTime.toMillis(), leaseTime.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("interrupted while waiting for lock key={}", key, ex);
            return false;
        }
        if (!acquired) {
            log.debug("lock not acquired key={} waitTime={}", key, waitTime);
            return false;
        }
        log.debug("lock acquired key={} leaseTime={}", key, leaseTime);
        try {
            action.run();
            return true;
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
