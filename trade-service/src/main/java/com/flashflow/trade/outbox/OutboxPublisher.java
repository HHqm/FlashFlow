package com.flashflow.trade.outbox;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import com.flashflow.trade.common.RedisDistributedLock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "flashflow.jobs.outbox-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final String LOCK_KEY = "flashflow:lock:outbox-publisher";

    private final OutboxRepository outboxRepository;
    private final OutboxStateService stateService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final RedisDistributedLock distributedLock;
    private final String topic;
    private final int maxRetries;
    private final Counter publishedCounter;
    private final Counter failedCounter;

    public OutboxPublisher(
            OutboxRepository outboxRepository,
            OutboxStateService stateService,
            KafkaTemplate<String, String> kafkaTemplate,
            RedisDistributedLock distributedLock,
            @Value("${flashflow.kafka.order-events-topic}") String topic,
            @Value("${flashflow.outbox.max-retries:10}") int maxRetries,
            MeterRegistry meterRegistry
    ) {
        this.outboxRepository = outboxRepository;
        this.stateService = stateService;
        this.kafkaTemplate = kafkaTemplate;
        this.distributedLock = distributedLock;
        this.topic = topic;
        this.maxRetries = maxRetries;
        this.publishedCounter = meterRegistry.counter("flashflow.outbox.published");
        this.failedCounter = meterRegistry.counter("flashflow.outbox.failed");
    }

    @Scheduled(fixedDelayString = "${flashflow.jobs.outbox-delay-ms:1000}")
    public void publishPendingEvents() {
        distributedLock.tryLock(LOCK_KEY, Duration.ofSeconds(10)).ifPresent(token -> {
            try {
                outboxRepository
                        .findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                                OutboxStatus.PENDING,
                                Instant.now())
                        .forEach(this::publish);
            } finally {
                distributedLock.unlock(token);
            }
        });
    }

    private void publish(OutboxEntity event) {
        try {
            kafkaTemplate.send(topic, event.getAggregateId(), event.getPayload())
                    .get(5, TimeUnit.SECONDS);
            stateService.markPublished(event.getEventId());
            publishedCounter.increment();
        } catch (Exception exception) {
            failedCounter.increment();
            stateService.markFailed(event.getEventId(), exception.getMessage(), maxRetries);
            log.warn("Outbox publish failed, eventId={}, eventType={}",
                    event.getEventId(), event.getEventType(), exception);
        }
    }
}
