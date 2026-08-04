package com.flashflow.trade.review;

import java.time.Instant;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class ReviewDtos {

    private ReviewDtos() {
    }

    public record CreateReviewRequest(
            @Min(1) @Max(5) int rating,
            @NotBlank @Size(min = 3, max = 1000) String content
    ) {
    }

    public record ChangeVisibilityRequest(@NotNull Boolean visible) {
    }

    public record ReplyReviewRequest(@NotBlank @Size(max = 500) String reply) {
    }

    public record ReviewView(
            Long id,
            String orderNo,
            long userId,
            String reviewerName,
            long productId,
            String productName,
            String variantLabel,
            int rating,
            String content,
            boolean visible,
            String merchantReply,
            Instant repliedAt,
            Instant createdAt
    ) {
    }
}
