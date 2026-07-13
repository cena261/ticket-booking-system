package com.ticketapp.infrastructure.messaging;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class KafkaOrderProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;

    public KafkaOrderProducer(KafkaTemplate<String, String> kafkaTemplate,
                              @Value("${kafka.topic.order-place}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public CompletableFuture<SendResult<String, String>> send(String key, String payload) {
        return kafkaTemplate.send(topic, key, payload);
    }
}
