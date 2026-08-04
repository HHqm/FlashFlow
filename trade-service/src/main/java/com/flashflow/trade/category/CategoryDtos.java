package com.flashflow.trade.category;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class CategoryDtos {

    private CategoryDtos() {
    }

    public record CreateCategoryRequest(
            @NotBlank @Size(max = 80) String name,
            @Min(0) int sortOrder
    ) {
    }

    public record ChangeCategoryStatusRequest(@NotNull Boolean active) {
    }

    public record CategoryView(Long id, String name, int sortOrder, boolean active) {
        public static CategoryView from(CategoryEntity category) {
            return new CategoryView(
                    category.getId(),
                    category.getName(),
                    category.getSortOrder(),
                    category.isActive());
        }
    }
}
