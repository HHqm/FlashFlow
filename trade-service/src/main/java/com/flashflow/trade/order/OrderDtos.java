package com.flashflow.trade.order;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.validation.constraints.Min;

public final class OrderDtos {

    private OrderDtos() {
    }

    public record PlaceOrderRequest(@Min(1) int quantity) {
    }

    public record OrderView(
            String orderNo,
            long campaignId,
            long productId,
            Long variantId,
            String productName,
            String variantLabel,
            int quantity,
            BigDecimal amount,
            OrderStatus status,
            Instant createdAt,
            Instant paidAt,
            Instant cancelledAt,
            String cancelReason,
            Instant refundedAt,
            boolean replayed
    ) {
        public static OrderView from(OrderEntity order, boolean replayed) {
            return new OrderView(
                    order.getOrderNo(),
                    order.getCampaignId(),
                    order.getProductId(),
                    order.getVariantId(),
                    order.getProductName(),
                    order.getVariantLabel(),
                    order.getQuantity(),
                    order.getAmount(),
                    order.getStatus(),
                    order.getCreatedAt(),
                    order.getPaidAt(),
                    order.getCancelledAt(),
                    order.getCancelReason(),
                    order.getRefundedAt(),
                    replayed);
        }
    }
}
