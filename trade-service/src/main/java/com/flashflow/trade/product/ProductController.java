package com.flashflow.trade.product;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/products")
    ApiResponse<List<ProductDtos.ProductView>> listProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId
    ) {
        return ApiResponse.success(productService.listActiveProducts(keyword, categoryId));
    }

    @GetMapping("/products/{id}")
    ApiResponse<ProductDtos.ProductView> getProduct(@PathVariable long id) {
        return ApiResponse.success(productService.getProduct(id));
    }

    @GetMapping("/admin/products")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    ApiResponse<List<ProductDtos.ProductView>> listAdminProducts() {
        return ApiResponse.success(productService.listAllProducts());
    }

    @PostMapping("/admin/products")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    ApiResponse<ProductDtos.ProductView> createProduct(
            @Valid @RequestBody ProductDtos.CreateProductRequest request
    ) {
        return ApiResponse.success(productService.createProduct(request));
    }

    @PostMapping("/admin/products/{productId}/variants")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    ApiResponse<ProductDtos.VariantView> addVariant(
            @PathVariable long productId,
            @Valid @RequestBody ProductDtos.CreateVariantRequest request
    ) {
        return ApiResponse.success(productService.addVariant(productId, request));
    }

    @PatchMapping("/admin/products/{productId}/status")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    ApiResponse<ProductDtos.ProductView> changeProductStatus(
            @PathVariable long productId,
            @Valid @RequestBody ProductDtos.ChangeStatusRequest request
    ) {
        return ApiResponse.success(productService.changeProductStatus(productId, request));
    }

    @PatchMapping("/admin/variants/{variantId}/status")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    ApiResponse<ProductDtos.VariantView> changeVariantStatus(
            @PathVariable long variantId,
            @Valid @RequestBody ProductDtos.ChangeStatusRequest request
    ) {
        return ApiResponse.success(productService.changeVariantStatus(variantId, request));
    }

    @PatchMapping("/admin/variants/{variantId}/stock")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    ApiResponse<ProductDtos.VariantView> changeVariantStock(
            @PathVariable long variantId,
            @Valid @RequestBody ProductDtos.ChangeStockRequest request
    ) {
        return ApiResponse.success(productService.changeVariantStock(variantId, request));
    }

    @PatchMapping("/admin/variants/{variantId}/price")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    ApiResponse<ProductDtos.VariantView> changeVariantPrice(
            @PathVariable long variantId,
            @Valid @RequestBody ProductDtos.ChangePriceRequest request
    ) {
        return ApiResponse.success(productService.changeVariantPrice(variantId, request));
    }
}
