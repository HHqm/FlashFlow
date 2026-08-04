package com.flashflow.trade.product;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.flashflow.trade.category.CategoryEntity;
import com.flashflow.trade.category.CategoryRepository;
import com.flashflow.trade.campaign.CampaignRepository;
import com.flashflow.trade.campaign.CampaignStatus;
import com.flashflow.trade.common.BusinessException;
import com.flashflow.trade.common.ErrorCode;
import com.flashflow.trade.review.ReviewEntity;
import com.flashflow.trade.review.ReviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final CategoryRepository categoryRepository;
    private final CampaignRepository campaignRepository;
    private final ReviewRepository reviewRepository;

    public ProductService(
            ProductRepository productRepository,
            ProductVariantRepository variantRepository,
            CategoryRepository categoryRepository,
            CampaignRepository campaignRepository,
            ReviewRepository reviewRepository
    ) {
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.categoryRepository = categoryRepository;
        this.campaignRepository = campaignRepository;
        this.reviewRepository = reviewRepository;
    }

    @Transactional(readOnly = true)
    public List<ProductDtos.ProductView> listActiveProducts() {
        return listActiveProducts(null, null);
    }

    @Transactional(readOnly = true)
    public List<ProductDtos.ProductView> listActiveProducts(String keyword, Long categoryId) {
        String search = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        return toViews(productRepository.findAllByActiveTrueOrderByIdDesc(), false).stream()
                .filter(product -> categoryId == null || product.categoryId().equals(categoryId))
                .filter(product -> search.isBlank() || matches(product, search))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProductDtos.ProductView> listAllProducts() {
        return toViews(productRepository.findAllByOrderByIdDesc(), true);
    }

    @Transactional(readOnly = true)
    public ProductDtos.ProductView getProduct(long id) {
        ProductEntity product = productRepository.findById(id)
                .filter(ProductEntity::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        return toView(
                product,
                categoryRepository.findById(product.getCategoryId()).map(CategoryEntity::getName).orElse("未分类"),
                variantRepository.findAllByProductIdOrderByIdAsc(product.getId()),
                reviewRepository.findTop100ByProductIdAndVisibleTrueOrderByCreatedAtDesc(product.getId()),
                false);
    }

    @Transactional
    public ProductDtos.ProductView createProduct(ProductDtos.CreateProductRequest request) {
        CategoryEntity category = categoryRepository.findById(request.categoryId())
                .filter(CategoryEntity::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
        validateUniqueSkuCodes(request.variants());

        ProductDtos.CreateVariantRequest primary = request.variants().getFirst();
        ProductEntity product = productRepository.save(new ProductEntity(
                category.getId(),
                primary.skuCode().trim(),
                request.name().trim(),
                request.description() == null ? "" : request.description().trim(),
                request.imageUrl() == null ? "" : request.imageUrl().trim(),
                primary.price()));

        List<ProductVariantEntity> variants = request.variants().stream()
                .map(item -> new ProductVariantEntity(
                        product.getId(),
                        item.skuCode().trim(),
                        item.colorName().trim(),
                        item.specification().trim(),
                        item.price(),
                        item.stock()))
                .map(variantRepository::save)
                .toList();
        return toView(product, category.getName(), variants, List.of(), true);
    }

    @Transactional
    public ProductDtos.VariantView addVariant(
            long productId,
            ProductDtos.CreateVariantRequest request
    ) {
        ProductEntity product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        variantRepository.findBySkuCode(request.skuCode().trim()).ifPresent(existing -> {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "SKU 编码已存在");
        });
        ProductVariantEntity variant = variantRepository.save(new ProductVariantEntity(
                product.getId(),
                request.skuCode().trim(),
                request.colorName().trim(),
                request.specification().trim(),
                request.price(),
                request.stock()));
        return ProductDtos.VariantView.from(variant);
    }

    @Transactional
    public ProductDtos.ProductView changeProductStatus(
            long productId,
            ProductDtos.ChangeStatusRequest request
    ) {
        ProductEntity product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        product.changeStatus(request.active());
        if (!request.active()) {
            campaignRepository.pauseActiveByProductId(productId);
        }
        String categoryName = categoryRepository.findById(product.getCategoryId())
                .map(CategoryEntity::getName)
                .orElse("未分类");
        return toView(
                product,
                categoryName,
                variantRepository.findAllByProductIdOrderByIdAsc(productId),
                reviewRepository.findTop100ByProductIdAndVisibleTrueOrderByCreatedAtDesc(productId),
                true);
    }

    @Transactional
    public ProductDtos.VariantView changeVariantStatus(
            long variantId,
            ProductDtos.ChangeStatusRequest request
    ) {
        ProductVariantEntity variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VARIANT_NOT_FOUND));
        variant.changeStatus(request.active());
        if (!request.active()) {
            campaignRepository.pauseActiveByVariantId(variantId);
        }
        return ProductDtos.VariantView.from(variant);
    }

    @Transactional
    public ProductDtos.VariantView changeVariantStock(
            long variantId,
            ProductDtos.ChangeStockRequest request
    ) {
        ProductVariantEntity variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VARIANT_NOT_FOUND));
        variant.changeStock(request.stock());
        return ProductDtos.VariantView.from(variant);
    }

    @Transactional
    public ProductDtos.VariantView changeVariantPrice(
            long variantId,
            ProductDtos.ChangePriceRequest request
    ) {
        ProductVariantEntity variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VARIANT_NOT_FOUND));
        boolean conflictsWithCampaign = campaignRepository
                .findAllByVariantIdAndEndsAtAfter(variantId, java.time.Instant.now())
                .stream()
                .filter(campaign -> campaign.getStatus() != CampaignStatus.ENDED)
                .anyMatch(campaign -> campaign.getFlashPrice().compareTo(request.price()) >= 0);
        if (conflictsWithCampaign) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_FAILED,
                    "新原价必须高于该 SKU 尚未结束的秒杀价");
        }
        variant.changePrice(request.price());
        return ProductDtos.VariantView.from(variant);
    }

    private void validateUniqueSkuCodes(List<ProductDtos.CreateVariantRequest> variants) {
        long distinctCount = variants.stream()
                .map(item -> item.skuCode().trim())
                .distinct()
                .count();
        if (distinctCount != variants.size()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "同一商品内 SKU 编码不能重复");
        }
        variants.forEach(item ->
                variantRepository.findBySkuCode(item.skuCode().trim()).ifPresent(existing -> {
                    throw new BusinessException(ErrorCode.VALIDATION_FAILED, "SKU 编码已存在：" + item.skuCode());
                }));
    }

    private List<ProductDtos.ProductView> toViews(List<ProductEntity> products, boolean includeInactive) {
        if (products.isEmpty()) {
            return List.of();
        }
        Map<Long, String> categories = new HashMap<>();
        categoryRepository.findAll().forEach(category -> categories.put(category.getId(), category.getName()));
        Map<Long, List<ProductVariantEntity>> variantsByProduct = variantRepository
                .findAllByProductIdInOrderByIdAsc(products.stream().map(ProductEntity::getId).toList())
                .stream()
                .collect(java.util.stream.Collectors.groupingBy(ProductVariantEntity::getProductId));
        Map<Long, List<ReviewEntity>> reviewsByProduct = reviewRepository
                .findAllByProductIdInAndVisibleTrue(products.stream().map(ProductEntity::getId).toList())
                .stream()
                .collect(java.util.stream.Collectors.groupingBy(ReviewEntity::getProductId));
        return products.stream()
                .map(product -> toView(
                        product,
                        categories.getOrDefault(product.getCategoryId(), "未分类"),
                        variantsByProduct.getOrDefault(product.getId(), List.of()),
                        reviewsByProduct.getOrDefault(product.getId(), List.of()),
                        includeInactive))
                .toList();
    }

    private ProductDtos.ProductView toView(
            ProductEntity product,
            String categoryName,
            List<ProductVariantEntity> variants,
            List<ReviewEntity> reviews,
            boolean includeInactive
    ) {
        List<ProductVariantEntity> visibleVariants = includeInactive
                ? variants
                : variants.stream().filter(ProductVariantEntity::isActive).toList();
        BigDecimal minPrice = visibleVariants.stream()
                .map(ProductVariantEntity::getPrice)
                .min(BigDecimal::compareTo)
                .orElse(product.getPrice());
        int totalStock = visibleVariants.stream().mapToInt(ProductVariantEntity::getStock).sum();
        double averageRating = reviews.stream()
                .mapToInt(ReviewEntity::getRating)
                .average()
                .orElse(0.0);
        return new ProductDtos.ProductView(
                product.getId(),
                product.getCategoryId(),
                categoryName,
                product.getName(),
                product.getDescription(),
                product.getImageUrl(),
                product.isActive(),
                minPrice,
                totalStock,
                Math.round(averageRating * 10.0) / 10.0,
                reviews.size(),
                visibleVariants.stream().map(ProductDtos.VariantView::from).toList());
    }

    private static boolean matches(ProductDtos.ProductView product, String search) {
        return product.name().toLowerCase(Locale.ROOT).contains(search)
                || product.description().toLowerCase(Locale.ROOT).contains(search)
                || product.categoryName().toLowerCase(Locale.ROOT).contains(search)
                || product.variants().stream().anyMatch(variant ->
                        variant.skuCode().toLowerCase(Locale.ROOT).contains(search)
                                || variant.colorName().toLowerCase(Locale.ROOT).contains(search)
                                || variant.specification().toLowerCase(Locale.ROOT).contains(search));
    }
}
