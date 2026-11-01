package com.flashflow.trade.inventory;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockCompensationRepository extends JpaRepository<StockCompensationTask, Long> {

    Optional<StockCompensationTask> findByOrderNo(String orderNo);

    List<StockCompensationTask> findByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
            StockCompensationStatus status,
            Instant now,
            Pageable pageable
    );

    List<StockCompensationTask> findTop100ByOrderByCreatedAtDesc();

    long countByStatus(StockCompensationStatus status);

    @Query("""
            select task.campaignId, count(task)
              from StockCompensationTask task
             where task.status = :status
               and task.campaignId in :campaignIds
             group by task.campaignId
            """)
    List<Object[]> countByCampaignIds(
            @Param("status") StockCompensationStatus status,
            @Param("campaignIds") Collection<Long> campaignIds
    );
}
