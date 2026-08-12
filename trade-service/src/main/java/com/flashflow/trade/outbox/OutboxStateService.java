package com.flashflow.trade.outbox;

import java.time.Instant;
import java.util.Collection;
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
    public int markPublished(Collection<UUID> eventIds) {
        if (eventIds.isEmpty()) {
            return 0;
        }
        return outboxRepository.markPublished(eventIds, Instant.now());
    }

    @Transactional
    public void markFailed(UUID eventId, String error, int maxRetries) {
        outboxRepository.findById(eventId)
                .ifPresent(event -> event.markFailed(error, maxRetries, Instant.now()));
    }
}
