package com.flashflow.trade.category;

import java.util.List;

import com.flashflow.trade.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping("/categories")
    ApiResponse<List<CategoryDtos.CategoryView>> listPublic() {
        return ApiResponse.success(categoryService.listPublic());
    }

    @GetMapping("/admin/categories")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    ApiResponse<List<CategoryDtos.CategoryView>> listAdmin() {
        return ApiResponse.success(categoryService.listAdmin());
    }

    @PostMapping("/admin/categories")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    ApiResponse<CategoryDtos.CategoryView> create(
            @Valid @RequestBody CategoryDtos.CreateCategoryRequest request
    ) {
        return ApiResponse.success(categoryService.create(request));
    }

    @PatchMapping("/admin/categories/{categoryId}/status")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    ApiResponse<CategoryDtos.CategoryView> changeStatus(
            @PathVariable long categoryId,
            @Valid @RequestBody CategoryDtos.ChangeCategoryStatusRequest request
    ) {
        return ApiResponse.success(categoryService.changeStatus(categoryId, request));
    }
}
