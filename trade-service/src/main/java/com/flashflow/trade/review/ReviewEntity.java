package com.flashflow.trade.review;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "product_review")
public class ReviewEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Column(name = "order_no", nullable = false, length = 40)
    private String orderNo;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "variant_id")
    private Long variantId;

    @Column(nullable = false)
    private int rating;

    @Column(nullable = false, length = 1000)
    private String content;

    @Column(nullable = false)
    private boolean visible;

    @Column(name = "merchant_reply", length = 500)
    private String merchantReply;

    @Column(name = "replied_at")
    private Instant repliedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ReviewEntity() {
    }

    public ReviewEntity(
            Long orderId,
            String orderNo,
            Long userId,
            Long productId,
            Long variantId,
            int rating,
            String content
    ) {
        this.orderId = orderId;
        this.orderNo = orderNo;
        this.userId = userId;
        this.productId = productId;
        this.variantId = variantId;
        this.rating = rating;
        this.content = content;
        this.visible = true;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void changeVisibility(boolean visible) {
        this.visible = visible;
        this.updatedAt = Instant.now();
    }

    public void reply(String reply) {
        this.merchantReply = reply;
        this.repliedAt = Instant.now();
        this.updatedAt = this.repliedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getProductId() {
        return productId;
    }

    public Long getVariantId() {
        return variantId;
    }

    public int getRating() {
        return rating;
    }

    public String getContent() {
        return content;
    }

    public boolean isVisible() {
        return visible;
    }

    public String getMerchantReply() {
        return merchantReply;
    }

    public Instant getRepliedAt() {
        return repliedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
