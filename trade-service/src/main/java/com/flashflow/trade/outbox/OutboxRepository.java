package com.flashflow.trade.outbox;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxRepository extends JpaRepository<OutboxEntity, UUID> {

    List<OutboxEntity> findByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            OutboxStatus status,
            Instant now,
            Pageable pageable
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update OutboxEntity event
               set event.status = com.flashflow.trade.outbox.OutboxStatus.PUBLISHED,
                   event.publishedAt = :publishedAt,
                   event.lastError = null
             where event.eventId in :eventIds
               and event.status = com.flashflow.trade.outbox.OutboxStatus.PENDING
            """)
    int markPublished(
            @Param("eventIds") Collection<UUID> eventIds,
            @Param("publishedAt") Instant publishedAt
    );

    long countByStatus(OutboxStatus status);
}
