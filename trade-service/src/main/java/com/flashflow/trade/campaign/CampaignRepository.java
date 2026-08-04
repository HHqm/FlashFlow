package com.flashflow.trade.campaign;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CampaignRepository extends JpaRepository<CampaignEntity, Long> {

    List<CampaignEntity> findAllByStatusAndEndsAtAfterOrderByStartsAtAsc(
            CampaignStatus status,
            Instant now
    );

    List<CampaignEntity> findAllByOrderByCreatedAtDesc();

    List<CampaignEntity> findAllByVariantIdAndEndsAtAfter(long variantId, Instant now);

    boolean existsByProductIdAndStatusAndEndsAtAfter(
            long productId,
            CampaignStatus status,
            Instant now
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update CampaignEntity campaign
               set campaign.status = com.flashflow.trade.campaign.CampaignStatus.PAUSED
             where campaign.productId = :productId
               and campaign.status = com.flashflow.trade.campaign.CampaignStatus.ACTIVE
            """)
    int pauseActiveByProductId(@Param("productId") long productId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update CampaignEntity campaign
               set campaign.status = com.flashflow.trade.campaign.CampaignStatus.PAUSED
             where campaign.variantId = :variantId
               and campaign.status = com.flashflow.trade.campaign.CampaignStatus.ACTIVE
            """)
    int pauseActiveByVariantId(@Param("variantId") long variantId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update CampaignEntity campaign
               set campaign.remainingStock = campaign.remainingStock - :quantity,
                   campaign.version = campaign.version + 1
             where campaign.id = :campaignId
               and campaign.remainingStock >= :quantity
               and campaign.status = com.flashflow.trade.campaign.CampaignStatus.ACTIVE
            """)
    int decrementStock(
            @Param("campaignId") long campaignId,
            @Param("quantity") int quantity
    );

    default int decrementStock(long campaignId) {
        return decrementStock(campaignId, 1);
    }

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update CampaignEntity campaign
               set campaign.remainingStock = campaign.remainingStock + :quantity,
                   campaign.version = campaign.version + 1
             where campaign.id = :campaignId
               and campaign.remainingStock + :quantity <= campaign.totalStock
            """)
    int incrementStock(
            @Param("campaignId") long campaignId,
            @Param("quantity") int quantity
    );

    default int incrementStock(long campaignId) {
        return incrementStock(campaignId, 1);
    }
}
