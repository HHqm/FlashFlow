package com.flashflow.trade.outbox;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.flashflow.trade.common.RedisDistributedLock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
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
    private final int batchSize;
    private final Duration sendTimeout;
    private final Counter publishedCounter;
    private final Counter failedCounter;

    public OutboxPublisher(
            OutboxRepository outboxRepository,
            OutboxStateService stateService,
            KafkaTemplate<String, String> kafkaTemplate,
            RedisDistributedLock distributedLock,
            @Value("${flashflow.kafka.order-events-topic}") String topic,
            @Value("${flashflow.outbox.max-retries:10}") int maxRetries,
            @Value("${flashflow.outbox.batch-size:500}") int batchSize,
            @Value("${flashflow.outbox.send-timeout:PT5S}") Duration sendTimeout,
            MeterRegistry meterRegistry
    ) {
        this.outboxRepository = outboxRepository;
        this.stateService = stateService;
        this.kafkaTemplate = kafkaTemplate;
        this.distributedLock = distributedLock;
        this.topic = topic;
        this.maxRetries = maxRetries;
        this.batchSize = batchSize;
        this.sendTimeout = sendTimeout;
        this.publishedCounter = meterRegistry.counter("flashflow.outbox.published");
        this.failedCounter = meterRegistry.counter("flashflow.outbox.failed");
    }

    @Scheduled(fixedDelayString = "${flashflow.jobs.outbox-delay-ms:1000}")
    public void publishPendingEvents() {
        distributedLock.tryLock(LOCK_KEY, Duration.ofSeconds(10)).ifPresent(token -> {
            try {
                List<OutboxEntity> events = outboxRepository
                        .findByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                                OutboxStatus.PENDING,
                                Instant.now(),
                                PageRequest.of(0, batchSize));
                publish(events);
            } finally {
                distributedLock.unlock(token);
            }
        });
    }

    private void publish(List<OutboxEntity> events) {
        if (events.isEmpty()) {
            return;
        }

        List<PendingSend> pendingSends = new ArrayList<>(events.size());
        for (OutboxEntity event : events) {
            try {
                pendingSends.add(new PendingSend(
                        event,
                        kafkaTemplate.send(topic, event.getAggregateId(), event.getPayload())));
            } catch (RuntimeException exception) {
                markFailed(event, exception);
            }
        }

        long deadline = System.nanoTime() + sendTimeout.toNanos();
        List<UUID> publishedIds = new ArrayList<>(pendingSends.size());
        for (PendingSend pendingSend : pendingSends) {
            long remainingNanos = deadline - System.nanoTime();
            if (remainingNanos <= 0) {
                markFailed(pendingSend.event(), new TimeoutException("Kafka batch send timed out"));
                continue;
            }
            try {
                pendingSend.future().get(remainingNanos, TimeUnit.NANOSECONDS);
                publishedIds.add(pendingSend.event().getEventId());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                markFailed(pendingSend.event(), exception);
            } catch (ExecutionException | TimeoutException exception) {
                markFailed(pendingSend.event(), exception);
            }
        }

        int updated = stateService.markPublished(publishedIds);
        publishedCounter.increment(updated);
        if (updated != publishedIds.size()) {
            log.warn("Outbox batch state update mismatch, sent={}, updated={}",
                    publishedIds.size(), updated);
        }
    }

    private void markFailed(OutboxEntity event, Exception exception) {
        failedCounter.increment();
        stateService.markFailed(event.getEventId(), exception.getMessage(), maxRetries);
        log.warn("Outbox publish failed, eventId={}, eventType={}",
                event.getEventId(), event.getEventType(), exception);
    }

    private record PendingSend(OutboxEntity event, CompletableFuture<?> future) {
    }
}
