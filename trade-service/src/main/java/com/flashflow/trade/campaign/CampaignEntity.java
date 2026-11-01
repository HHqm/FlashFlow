package com.flashflow.trade.campaign;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.Formula;

@Entity
@Table(name = "flash_campaign")
public class CampaignEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "variant_id")
    private Long variantId;

    @Column(name = "flash_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal flashPrice;

    @Column(name = "total_stock", nullable = false)
    private int totalStock;

    @Column(name = "stock_buckets", nullable = false)
    private int stockBuckets;

    // Stock lives in campaign_stock_bucket rows; these are read-only aggregates loaded with the row.
    @Formula("(select coalesce(sum(b.remaining_stock), 0) from campaign_stock_bucket b where b.campaign_id = id)")
    private int remainingStock;

    // Changes whenever any bucket changes, so reconciliation can tell "nothing was committed".
    @Formula("(select coalesce(sum(b.version), 0) from campaign_stock_bucket b where b.campaign_id = id)")
    private long stockVersion;

    @Column(name = "purchase_limit", nullable = false)
    private int purchaseLimit;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CampaignStatus status;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CampaignEntity() {
    }

    public CampaignEntity(
            Long productId,
            BigDecimal flashPrice,
            int totalStock,
            Instant startsAt,
            Instant endsAt
    ) {
        this(productId, null, flashPrice, totalStock, 1, startsAt, endsAt);
    }

    public CampaignEntity(
            Long productId,
            Long variantId,
            BigDecimal flashPrice,
            int totalStock,
            int purchaseLimit,
            Instant startsAt,
            Instant endsAt
    ) {
        this(productId, variantId, flashPrice, totalStock, purchaseLimit, 1, startsAt, endsAt);
    }

    public CampaignEntity(
            Long productId,
            Long variantId,
            BigDecimal flashPrice,
            int totalStock,
            int purchaseLimit,
            int stockBuckets,
            Instant startsAt,
            Instant endsAt
    ) {
        this.productId = productId;
        this.variantId = variantId;
        this.flashPrice = flashPrice;
        this.totalStock = totalStock;
        this.remainingStock = totalStock;
        this.stockBuckets = Math.max(1, Math.min(stockBuckets, totalStock));
        this.purchaseLimit = purchaseLimit;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.status = CampaignStatus.ACTIVE;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getProductId() {
        return productId;
    }

    public Long getVariantId() {
        return variantId;
    }

    public BigDecimal getFlashPrice() {
        return flashPrice;
    }

    public int getTotalStock() {
        return totalStock;
    }

    public int getRemainingStock() {
        return remainingStock;
    }

    public int getPurchaseLimit() {
        return purchaseLimit;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public long getVersion() {
        return version;
    }

    public long getStockVersion() {
        return stockVersion;
    }

    public int getStockBuckets() {
        return stockBuckets;
    }

    public CampaignStatus getStatus() {
        return status;
    }

    public void changeStatus(CampaignStatus status) {
        this.status = status;
    }

    public boolean hasStarted(Instant now) {
        return !startsAt.isAfter(now);
    }

    public boolean hasEnded(Instant now) {
        return !endsAt.isAfter(now) || status == CampaignStatus.ENDED;
    }
}
