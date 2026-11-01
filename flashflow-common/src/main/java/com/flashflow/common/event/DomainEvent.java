package com.flashflow.common.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

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
        return new DomainEvent(timeOrderedId(), eventType, aggregateId, Instant.now(), 1, Map.copyOf(payload));
    }

    /**
     * RFC 9562 version 7 UUID: a 48-bit millisecond timestamp followed by random bits. Both the
     * Outbox and the consumer Inbox use the event id as their InnoDB primary key, so time-ordered
     * ids append to the end of the clustered index instead of splitting random pages.
     */
    public static UUID timeOrderedId() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        long mostSignificant = (System.currentTimeMillis() << 16) | 0x7000L | (random.nextLong() & 0x0FFFL);
        long leastSignificant = (random.nextLong() & 0x3FFF_FFFF_FFFF_FFFFL) | 0x8000_0000_0000_0000L;
        return new UUID(mostSignificant, leastSignificant);
    }
}
