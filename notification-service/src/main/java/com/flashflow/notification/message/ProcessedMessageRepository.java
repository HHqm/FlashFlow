package com.flashflow.notification.message;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProcessedMessageRepository extends JpaRepository<ProcessedMessageEntity, UUID> {

    @Modifying
    @Query(
            value = """
                    INSERT IGNORE INTO processed_message(event_id, event_type, processed_at)
                    VALUES (:eventId, :eventType, :processedAt)
                    """,
            nativeQuery = true)
    int insertIfAbsent(
            @Param("eventId") UUID eventId,
            @Param("eventType") String eventType,
            @Param("processedAt") Instant processedAt
    );
}
