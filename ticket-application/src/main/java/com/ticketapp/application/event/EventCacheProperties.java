package com.ticketapp.application.event;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "event-cache")
public class EventCacheProperties {

    private long maximumSize = 10_000;
    private Duration l1ExpireAfterWrite = Duration.ofMinutes(10);
    private Duration metadataTtl = Duration.ofMinutes(10);
    private double jitterRatio = 0.2;
    private Duration nullTtl = Duration.ofSeconds(30);
    private Duration lockWait = Duration.ofSeconds(1);
    private Duration invalidateLockWait = Duration.ofSeconds(5);
    private Duration lockLease = Duration.ofSeconds(5);
    private int rebuildRetries = 10;
    private Duration rebuildBackoff = Duration.ofMillis(10);

    public long getMaximumSize() {
        return maximumSize;
    }

    public void setMaximumSize(long maximumSize) {
        this.maximumSize = maximumSize;
    }

    public Duration getL1ExpireAfterWrite() {
        return l1ExpireAfterWrite;
    }

    public void setL1ExpireAfterWrite(Duration l1ExpireAfterWrite) {
        this.l1ExpireAfterWrite = l1ExpireAfterWrite;
    }

    public Duration getMetadataTtl() {
        return metadataTtl;
    }

    public void setMetadataTtl(Duration metadataTtl) {
        this.metadataTtl = metadataTtl;
    }

    public double getJitterRatio() {
        return jitterRatio;
    }

    public void setJitterRatio(double jitterRatio) {
        this.jitterRatio = jitterRatio;
    }

    public Duration getNullTtl() {
        return nullTtl;
    }

    public void setNullTtl(Duration nullTtl) {
        this.nullTtl = nullTtl;
    }

    public Duration getLockWait() {
        return lockWait;
    }

    public void setLockWait(Duration lockWait) {
        this.lockWait = lockWait;
    }

    public Duration getInvalidateLockWait() {
        return invalidateLockWait;
    }

    public void setInvalidateLockWait(Duration invalidateLockWait) {
        this.invalidateLockWait = invalidateLockWait;
    }

    public Duration getLockLease() {
        return lockLease;
    }

    public void setLockLease(Duration lockLease) {
        this.lockLease = lockLease;
    }

    public int getRebuildRetries() {
        return rebuildRetries;
    }

    public void setRebuildRetries(int rebuildRetries) {
        this.rebuildRetries = rebuildRetries;
    }

    public Duration getRebuildBackoff() {
        return rebuildBackoff;
    }

    public void setRebuildBackoff(Duration rebuildBackoff) {
        this.rebuildBackoff = rebuildBackoff;
    }
}
