package com.ticketapp.application.order;

import com.ticketapp.domain.outbox.OutboxEvent;
import com.ticketapp.domain.outbox.OutboxEventRepository;
import com.ticketapp.infrastructure.messaging.KafkaOrderProducer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class OutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaOrderProducer producer;
    private final int batchSize;
    private final long ackTimeoutSeconds;

    public OutboxPublisher(OutboxEventRepository outboxEventRepository, KafkaOrderProducer producer,
                           @Value("${outbox.batch-size}") int batchSize,
                           @Value("${kafka.producer.ack-timeout-seconds}") long ackTimeoutSeconds) {
        this.outboxEventRepository = outboxEventRepository;
        this.producer = producer;
        this.batchSize = batchSize;
        this.ackTimeoutSeconds = ackTimeoutSeconds;
    }

    @Scheduled(fixedDelayString = "${outbox.poll-interval-ms}")
    public void publishPending() {
        List<OutboxEvent> pending = outboxEventRepository.findPending(batchSize);
        if (pending.isEmpty()) {
            return;
        }

        Map<Long, CompletableFuture<SendResult<String, String>>> inFlight = new LinkedHashMap<>();
        for (OutboxEvent event : pending) {
            inFlight.put(event.getId(), producer.send(event.getAggregateId(), event.getPayload()));
        }

        List<Long> acknowledged = new ArrayList<>(inFlight.size());
        for (Map.Entry<Long, CompletableFuture<SendResult<String, String>>> entry : inFlight.entrySet()) {
            try {
                entry.getValue().get(ackTimeoutSeconds, TimeUnit.SECONDS);
                acknowledged.add(entry.getKey());
            } catch (Exception ex) {
                log.error("outbox publish failed for event {}, leaving PENDING for retry", entry.getKey(), ex);
            }
        }

        if (!acknowledged.isEmpty()) {
            outboxEventRepository.markPublished(acknowledged);
        }
    }
}
