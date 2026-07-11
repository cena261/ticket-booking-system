package com.ticketapp;

import com.ticketapp.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.kafka.core.KafkaTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class InfraConnectivityIT extends AbstractIntegrationTest {

    @Autowired
    DataSource dataSource;

    @Autowired
    RedisConnectionFactory redisConnectionFactory;

    @Autowired
    KafkaTemplate<Object, Object> kafkaTemplate;

    @Test
    void mysqlDataSourceIsReachable() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.isValid(2)).isTrue();
        }
    }

    @Test
    void redisRespondsToPing() {
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            assertThat(connection.ping()).isEqualToIgnoringCase("PONG");
        }
    }

    @Test
    void kafkaAcceptsAProducedRecord() throws Exception {
        kafkaTemplate.send("infra-smoke", "ping").get(10, TimeUnit.SECONDS);
    }
}
