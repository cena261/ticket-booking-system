package com.ticketapp.stock;

import com.ticketapp.infrastructure.stock.RedisStockCacheService;
import com.ticketapp.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RedisStockCacheServiceIT extends AbstractIntegrationTest {

    @Autowired
    RedisStockCacheService stockCache;

    private Long uniqueTicketId() {
        return ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    }

    @Test
    void deductOnMissingKeyReturnsMiss() {
        assertThat(stockCache.deduct(uniqueTicketId(), 1)).isEqualTo(RedisStockCacheService.MISS);
    }

    @Test
    void warmUpDeductAndRestore() {
        Long id = uniqueTicketId();
        stockCache.warmUp(id, 5);

        assertThat(stockCache.deduct(id, 3)).isEqualTo(RedisStockCacheService.OK);
        assertThat(stockCache.currentStock(id)).isEqualTo(2);
        assertThat(stockCache.deduct(id, 5)).isEqualTo(RedisStockCacheService.INSUFFICIENT);

        stockCache.restore(id, 3);
        assertThat(stockCache.currentStock(id)).isEqualTo(5);
    }

    @Test
    void warmUpDoesNotOverwriteExistingStock() {
        Long id = uniqueTicketId();
        stockCache.warmUp(id, 5);
        stockCache.deduct(id, 2);

        stockCache.warmUp(id, 5);

        assertThat(stockCache.currentStock(id)).isEqualTo(3);
    }
}
