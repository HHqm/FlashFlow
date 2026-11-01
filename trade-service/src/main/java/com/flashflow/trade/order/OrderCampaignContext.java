package com.flashflow.trade.order;

import java.math.BigDecimal;
import java.time.Instant;

import com.flashflow.trade.campaign.CampaignStatus;

public record OrderCampaignContext(
        long campaignId,
        BigDecimal flashPrice,
        int purchaseLimit,
        Instant startsAt,
        Instant endsAt,
        CampaignStatus status,
        OrderProductSnapshot productSnapshot,
        int stockBuckets
) {

    public OrderCampaignContext(
            long campaignId,
            BigDecimal flashPrice,
            int purchaseLimit,
            Instant startsAt,
            Instant endsAt,
            CampaignStatus status,
            OrderProductSnapshot productSnapshot
    ) {
        this(campaignId, flashPrice, purchaseLimit, startsAt, endsAt, status, productSnapshot, 1);
    }

    /** Picks the stock bucket for an order; order numbers are random, so load spreads evenly. */
    public int bucketFor(String orderNo) {
        return Math.floorMod(orderNo.hashCode(), stockBuckets);
    }

    public boolean hasStarted(Instant now) {
        return !startsAt.isAfter(now);
    }

    public boolean hasEnded(Instant now) {
        return !endsAt.isAfter(now) || status == CampaignStatus.ENDED;
    }
}
