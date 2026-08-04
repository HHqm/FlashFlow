package com.flashflow.trade.order;

import java.math.BigDecimal;
import java.time.Instant;

import com.flashflow.trade.common.BusinessException;
import com.flashflow.trade.common.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "trade_order")
public class OrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_no", nullable = false, unique = true, length = 40)
    private String orderNo;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "campaign_id", nullable = false)
    private Long campaignId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "variant_id")
    private Long variantId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "product_name", nullable = false, length = 160)
    private String productName;

    @Column(name = "variant_label", nullable = false, length = 180)
    private String variantLabel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrderStatus status;

    @Column(name = "idempotency_key", nullable = false, length = 64)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected OrderEntity() {
    }

    public OrderEntity(
            String orderNo,
            Long userId,
            Long campaignId,
            Long productId,
            BigDecimal amount,
            String idempotencyKey
    ) {
        this(orderNo, userId, campaignId, productId, null, amount, 1, "", "", idempotencyKey);
    }

    public OrderEntity(
            String orderNo,
            Long userId,
            Long campaignId,
            Long productId,
            Long variantId,
            BigDecimal amount,
            int quantity,
            String productName,
            String variantLabel,
            String idempotencyKey
    ) {
        this.orderNo = orderNo;
        this.userId = userId;
        this.campaignId = campaignId;
        this.productId = productId;
        this.variantId = variantId;
        this.amount = amount;
        this.quantity = quantity;
        this.productName = productName;
        this.variantLabel = variantLabel;
        this.idempotencyKey = idempotencyKey;
        this.status = OrderStatus.PENDING_PAYMENT;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void pay(Instant now) {
        if (status != OrderStatus.PENDING_PAYMENT) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID);
        }
        status = OrderStatus.PAID;
        paidAt = now;
        updatedAt = now;
    }

    public boolean cancelIfPending(Instant now) {
        if (status != OrderStatus.PENDING_PAYMENT) {
            return false;
        }
        status = OrderStatus.CANCELLED;
        cancelledAt = now;
        updatedAt = now;
        return true;
    }

    public void cancelByCustomer(String reason, Instant now) {
        if (!cancelIfPending(now)) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID, "只有待支付订单可以直接取消");
        }
        cancelReason = reason;
    }

    public void requestRefund(Instant now) {
        if (status != OrderStatus.PAID) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID, "只有已支付订单可以申请退款");
        }
        status = OrderStatus.REFUND_REQUESTED;
        updatedAt = now;
    }

    public void approveRefund(Instant now) {
        if (status != OrderStatus.REFUND_REQUESTED) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID);
        }
        status = OrderStatus.REFUNDED;
        refundedAt = now;
        updatedAt = now;
    }

    public void rejectRefund(Instant now) {
        if (status != OrderStatus.REFUND_REQUESTED) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID);
        }
        status = OrderStatus.PAID;
        updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getCampaignId() {
        return campaignId;
    }

    public Long getProductId() {
        return productId;
    }

    public Long getVariantId() {
        return variantId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public int getQuantity() {
        return quantity;
    }

    public String getProductName() {
        return productName;
    }

    public String getVariantLabel() {
        return variantLabel;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public Instant getRefundedAt() {
        return refundedAt;
    }
}
