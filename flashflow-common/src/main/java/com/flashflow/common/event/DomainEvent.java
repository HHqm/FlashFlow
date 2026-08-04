package com.flashflow.common.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Versioned integration-event envelope shared across service boundaries.
 *
 * @param eventId globally unique id used by consumers for idempotency
 * @param eventType stable routing name, for example {@code order.created.v1}
 * @param aggregateId business aggregate id, normally the order number
 * @param occurredAt UTC event creation time
 * @param schemaVersion payload schema version
 * @param payload event-specific data
 */
public record DomainEvent(
        UUID eventId,
        String eventType,
        String aggregateId,
        Instant occurredAt,
        int schemaVersion,
        Map<String, Object> payload
) {
    public static DomainEvent create(String eventType, String aggregateId, Map<String, Object> payload) {
        return new DomainEvent(UUID.randomUUID(), eventType, aggregateId, Instant.now(), 1, Map.copyOf(payload));
    }
}
