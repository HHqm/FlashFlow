package com.flashflow.trade.product;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class ProductDtos {

    private ProductDtos() {
    }

    public record CreateVariantRequest(
            @NotBlank @Size(max = 64) String skuCode,
            @NotBlank @Size(max = 40) String colorName,
            @NotBlank @Size(max = 120) String specification,
            @NotNull @DecimalMin("0.01") BigDecimal price,
            @Min(0) int stock
    ) {
    }

    public record CreateProductRequest(
            @NotNull Long categoryId,
            @NotBlank @Size(max = 160) String name,
            @Size(max = 500) String description,
            @Size(max = 300) String imageUrl,
            @NotEmpty @Valid List<CreateVariantRequest> variants
    ) {
    }

    public record ChangeStatusRequest(@NotNull Boolean active) {
    }

    public record ChangeStockRequest(@Min(0) int stock) {
    }

    public record ChangePriceRequest(@NotNull @DecimalMin("0.01") BigDecimal price) {
    }

    public record VariantView(
            Long id,
            String skuCode,
            String colorName,
            String specification,
            BigDecimal price,
            int stock,
            boolean active
    ) {
        static VariantView from(ProductVariantEntity variant) {
            return new VariantView(
                    variant.getId(),
                    variant.getSkuCode(),
                    variant.getColorName(),
                    variant.getSpecification(),
                    variant.getPrice(),
                    variant.getStock(),
                    variant.isActive());
        }
    }

    public record ProductView(
            Long id,
            Long categoryId,
            String categoryName,
            String name,
            String description,
            String imageUrl,
            boolean active,
            BigDecimal minPrice,
            int totalStock,
            double averageRating,
            long reviewCount,
            List<VariantView> variants
    ) {
    }
}
