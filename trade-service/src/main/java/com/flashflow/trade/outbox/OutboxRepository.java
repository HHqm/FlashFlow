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

    // No automatic clear: the caller's other managed entities (orders being paid or cancelled in the
    // same transaction) must stay attached. The deleted events are never modified afterwards.
    @Modifying
    @Query("delete from OutboxEntity event where event.eventId in :eventIds")
    int deleteCapturedEvents(@Param("eventIds") Collection<UUID> eventIds);

    // Native because JPQL has no DELETE ... LIMIT. The (status, created_at) range keeps the scan
    // and its locks inside expired PUBLISHED rows, away from where new PENDING events are inserted.
    @Modifying
    @Query(value = """
            delete /*+ INDEX(outbox_event idx_outbox_status_created) */ from outbox_event
             where status = 'PUBLISHED'
               and created_at < :cutoff
             order by created_at
             limit :chunkSize
            """, nativeQuery = true)
    int deletePublishedBefore(
            @Param("cutoff") Instant cutoff,
            @Param("chunkSize") int chunkSize
    );
}
