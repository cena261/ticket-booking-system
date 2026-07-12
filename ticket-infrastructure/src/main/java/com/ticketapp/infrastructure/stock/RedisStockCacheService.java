package com.ticketapp.infrastructure.stock;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RedisStockCacheService {

    public static final long MISS = -1L;
    public static final long INSUFFICIENT = 0L;
    public static final long OK = 1L;

    private static final String KEY_FORMAT = "TICKET:%d:STOCK";

    private static final String DEDUCT_LUA = """
            local stock = redis.call('GET', KEYS[1]);
            if stock == false then return -1 end;
            stock = tonumber(stock);
            local quantity = tonumber(ARGV[1]);
            if (stock >= quantity) then
                redis.call('SET', KEYS[1], stock - quantity);
                return 1;
            end;
            return 0;
            """;

    private static final String RESTORE_LUA = """
            local stock = redis.call('GET', KEYS[1]);
            if stock == false then return 0 end;
            redis.call('SET', KEYS[1], tonumber(stock) + tonumber(ARGV[1]));
            return 1;
            """;

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> deductScript;
    private final DefaultRedisScript<Long> restoreScript;

    public RedisStockCacheService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.deductScript = new DefaultRedisScript<>(DEDUCT_LUA, Long.class);
        this.restoreScript = new DefaultRedisScript<>(RESTORE_LUA, Long.class);
    }

    public long deduct(Long ticketTypeId, int quantity) {
        Long result = redisTemplate.execute(deductScript, List.of(key(ticketTypeId)), String.valueOf(quantity));
        return result == null ? MISS : result;
    }

    public void restore(Long ticketTypeId, int quantity) {
        redisTemplate.execute(restoreScript, List.of(key(ticketTypeId)), String.valueOf(quantity));
    }

    public void warmUp(Long ticketTypeId, int stockAvailable) {
        redisTemplate.opsForValue().setIfAbsent(key(ticketTypeId), String.valueOf(stockAvailable));
    }

    public Long currentStock(Long ticketTypeId) {
        String value = redisTemplate.opsForValue().get(key(ticketTypeId));
        return value == null ? null : Long.valueOf(value);
    }

    private String key(Long ticketTypeId) {
        return KEY_FORMAT.formatted(ticketTypeId);
    }
}
