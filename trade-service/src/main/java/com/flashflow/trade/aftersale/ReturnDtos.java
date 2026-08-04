package com.flashflow.trade.aftersale;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class ReturnDtos {

    private ReturnDtos() {
    }

    public record CancelOrderRequest(@Size(max = 500) String reason) {
    }

    public record CreateReturnRequest(
            @NotBlank @Size(min = 5, max = 500) String reason
    ) {
    }

    public record ReturnDecisionRequest(
            @NotNull Boolean approved,
            @Size(max = 500) String note
    ) {
    }

    public record ReturnView(
            Long id,
            String returnNo,
            String orderNo,
            long userId,
            String productName,
            String variantLabel,
            int quantity,
            BigDecimal amount,
            String reason,
            ReturnStatus status,
            String adminNote,
            Instant requestedAt,
            Instant processedAt
    ) {
    }
}
