package com.ticketapp.application.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "reserve.rate-limit")
public class ReserveRateLimitProperties {

    private int limitForPeriod = 20;
    private Duration limitRefreshPeriod = Duration.ofSeconds(1);
    private long maximumUsers = 200_000;
    private Duration userExpireAfterAccess = Duration.ofMinutes(10);

    public int getLimitForPeriod() {
        return limitForPeriod;
    }

    public void setLimitForPeriod(int limitForPeriod) {
        this.limitForPeriod = limitForPeriod;
    }

    public Duration getLimitRefreshPeriod() {
        return limitRefreshPeriod;
    }

    public void setLimitRefreshPeriod(Duration limitRefreshPeriod) {
        this.limitRefreshPeriod = limitRefreshPeriod;
    }

    public long getMaximumUsers() {
        return maximumUsers;
    }

    public void setMaximumUsers(long maximumUsers) {
        this.maximumUsers = maximumUsers;
    }

    public Duration getUserExpireAfterAccess() {
        return userExpireAfterAccess;
    }

    public void setUserExpireAfterAccess(Duration userExpireAfterAccess) {
        this.userExpireAfterAccess = userExpireAfterAccess;
    }
}
