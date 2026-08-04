package com.flashflow.trade.product;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "product_variant")
public class ProductVariantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "sku_code", nullable = false, unique = true, length = 64)
    private String skuCode;

    @Column(name = "color_name", nullable = false, length = 40)
    private String colorName;

    @Column(nullable = false, length = 120)
    private String specification;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private int stock;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProductVariantEntity() {
    }

    public ProductVariantEntity(
            Long productId,
            String skuCode,
            String colorName,
            String specification,
            BigDecimal price,
            int stock
    ) {
        this.productId = productId;
        this.skuCode = skuCode;
        this.colorName = colorName;
        this.specification = specification;
        this.price = price;
        this.stock = stock;
        this.active = true;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void changeStatus(boolean active) {
        this.active = active;
        this.updatedAt = Instant.now();
    }

    public void changeStock(int stock) {
        this.stock = stock;
        this.updatedAt = Instant.now();
    }

    public void changePrice(BigDecimal price) {
        this.price = price;
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getProductId() {
        return productId;
    }

    public String getSkuCode() {
        return skuCode;
    }

    public String getColorName() {
        return colorName;
    }

    public String getSpecification() {
        return specification;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public int getStock() {
        return stock;
    }

    public boolean isActive() {
        return active;
    }
}
