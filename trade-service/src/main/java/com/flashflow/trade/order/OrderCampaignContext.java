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
        OrderProductSnapshot productSnapshot
) {

    public boolean hasStarted(Instant now) {
        return !startsAt.isAfter(now);
    }

    public boolean hasEnded(Instant now) {
        return !endsAt.isAfter(now) || status == CampaignStatus.ENDED;
    }
}
