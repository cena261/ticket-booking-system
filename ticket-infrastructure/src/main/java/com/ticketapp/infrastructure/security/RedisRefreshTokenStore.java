package com.ticketapp.infrastructure.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Component
public class RedisRefreshTokenStore {

    private static final String PREFIX = "refresh:";

    private final StringRedisTemplate redis;
    private final Duration refreshTtl;

    public RedisRefreshTokenStore(StringRedisTemplate redis, @Value("${jwt.refresh-ttl}") Duration refreshTtl) {
        this.redis = redis;
        this.refreshTtl = refreshTtl;
    }

    public String issue(Long userId) {
        String token = UUID.randomUUID().toString();
        redis.opsForValue().set(PREFIX + token, String.valueOf(userId), refreshTtl);
        return token;
    }

    public Optional<Long> consume(String token) {
        String value = redis.opsForValue().getAndDelete(PREFIX + token);
        return value == null ? Optional.empty() : Optional.of(Long.valueOf(value));
    }

    public void revoke(String token) {
        redis.delete(PREFIX + token);
    }
}
