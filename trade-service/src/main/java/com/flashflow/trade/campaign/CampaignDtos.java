package com.flashflow.trade.campaign;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public final class CampaignDtos {

    private CampaignDtos() {
    }

    public record CreateCampaignRequest(
            @NotNull Long variantId,
            @NotNull @DecimalMin("0.01") BigDecimal flashPrice,
            @Min(1) int totalStock,
            @Min(1) int purchaseLimit,
            @NotNull Instant startsAt,
            @NotNull @Future Instant endsAt
    ) {
    }

    public record ChangeCampaignStatusRequest(@NotNull CampaignStatus status) {
    }

    public record CampaignView(
            Long id,
            Long productId,
            Long variantId,
            String productName,
            String categoryName,
            String imageUrl,
            String skuCode,
            String colorName,
            String specification,
            BigDecimal originalPrice,
            BigDecimal flashPrice,
            int totalStock,
            int remainingStock,
            int purchaseLimit,
            Instant startsAt,
            Instant endsAt,
            CampaignStatus status,
            boolean productActive,
            boolean variantActive
    ) {
    }
}
