package com.flashflow.trade.outbox;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxStateService {

    private final OutboxRepository outboxRepository;

    public OutboxStateService(OutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    @Transactional
    public void markPublished(UUID eventId) {
        outboxRepository.findById(eventId).ifPresent(event -> event.markPublished(Instant.now()));
    }

    @Transactional
    public void markFailed(UUID eventId, String error, int maxRetries) {
        outboxRepository.findById(eventId)
                .ifPresent(event -> event.markFailed(error, maxRetries, Instant.now()));
    }
}
