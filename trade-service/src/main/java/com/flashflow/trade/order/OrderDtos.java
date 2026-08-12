package com.flashflow.trade.order;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public final class OrderDtos {

    private OrderDtos() {
    }

    public record PlaceOrderRequest(
            @Min(1) int quantity,
            @NotNull @Positive Long addressId
    ) {
    }

    public record ShipOrderRequest(
            @jakarta.validation.constraints.NotBlank
            @jakarta.validation.constraints.Size(max = 80) String carrier,
            @jakarta.validation.constraints.NotBlank
            @jakarta.validation.constraints.Size(max = 80) String trackingNo
    ) {
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
            String recipientName,
            String recipientPhone,
            String shippingAddress,
            String carrier,
            String trackingNo,
            Instant shippedAt,
            Instant receivedAt,
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
                    order.getRecipientName(),
                    order.getRecipientPhone(),
                    order.getShippingAddress(),
                    order.getCarrier(),
                    order.getTrackingNo(),
                    order.getShippedAt(),
                    order.getReceivedAt(),
                    replayed);
        }
    }
}
