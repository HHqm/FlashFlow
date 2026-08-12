package com.flashflow.trade.campaign;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

import com.flashflow.trade.category.CategoryRepository;
import com.flashflow.trade.common.BusinessException;
import com.flashflow.trade.common.ErrorCode;
import com.flashflow.trade.product.ProductEntity;
import com.flashflow.trade.product.ProductRepository;
import com.flashflow.trade.product.ProductVariantEntity;
import com.flashflow.trade.product.ProductVariantRepository;
import com.flashflow.trade.order.OrderCampaignContextService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CampaignService {

    private final CampaignRepository campaignRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final CategoryRepository categoryRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final CampaignCacheInitializer cacheInitializer;
    private final OrderCampaignContextService orderCampaignContextService;

    public CampaignService(
            CampaignRepository campaignRepository,
            ProductRepository productRepository,
            ProductVariantRepository variantRepository,
            CategoryRepository categoryRepository,
            ApplicationEventPublisher eventPublisher,
            CampaignCacheInitializer cacheInitializer,
            OrderCampaignContextService orderCampaignContextService
    ) {
        this.campaignRepository = campaignRepository;
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.categoryRepository = categoryRepository;
        this.eventPublisher = eventPublisher;
        this.cacheInitializer = cacheInitializer;
        this.orderCampaignContextService = orderCampaignContextService;
    }

    @Transactional(readOnly = true)
    public List<CampaignDtos.CampaignView> listAvailableCampaigns() {
        return listAvailableCampaigns(null, null);
    }

    @Transactional(readOnly = true)
    public List<CampaignDtos.CampaignView> listAvailableCampaigns(String keyword, Long categoryId) {
        Instant now = Instant.now();
        String search = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        return campaignRepository
                .findAllByStatusAndEndsAtAfterOrderByStartsAtAsc(CampaignStatus.ACTIVE, now)
                .stream()
                .map(this::toView)
                .filter(view -> view.productActive() && view.variantActive())
                .filter(view -> categoryId == null
                        || productRepository.findById(view.productId())
                                .map(ProductEntity::getCategoryId)
                                .filter(categoryId::equals)
                                .isPresent())
                .filter(view -> search.isBlank()
                        || view.productName().toLowerCase(Locale.ROOT).contains(search)
                        || view.categoryName().toLowerCase(Locale.ROOT).contains(search)
                        || view.skuCode().toLowerCase(Locale.ROOT).contains(search)
                        || view.colorName().toLowerCase(Locale.ROOT).contains(search)
                        || view.specification().toLowerCase(Locale.ROOT).contains(search))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CampaignDtos.CampaignView> listAllCampaigns() {
        return campaignRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toView)
                .toList();
    }

    @Transactional
    public CampaignDtos.CampaignView createCampaign(CampaignDtos.CreateCampaignRequest request) {
        if (!request.endsAt().isAfter(request.startsAt())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "结束时间必须晚于开始时间");
        }
        if (request.purchaseLimit() > request.totalStock()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "单人限购不能超过活动总库存");
        }
        ProductVariantEntity variant = variantRepository.findById(request.variantId())
                .filter(ProductVariantEntity::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.VARIANT_NOT_FOUND));
        ProductEntity product = productRepository.findById(variant.getProductId())
                .filter(ProductEntity::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        if (request.flashPrice().compareTo(variant.getPrice()) >= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "秒杀价必须低于 SKU 原价");
        }
        if (request.totalStock() > variant.getStock()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "活动库存不能超过 SKU 可用库存");
        }

        variant.changeStock(variant.getStock() - request.totalStock());
        CampaignEntity campaign = campaignRepository.save(new CampaignEntity(
                product.getId(),
                variant.getId(),
                request.flashPrice(),
                request.totalStock(),
                request.purchaseLimit(),
                request.startsAt(),
                request.endsAt()));
        eventPublisher.publishEvent(new CampaignCreatedEvent(campaign.getId()));
        return toView(campaign, product, variant);
    }

    @Transactional
    public CampaignDtos.CampaignView changeStatus(
            long campaignId,
            CampaignDtos.ChangeCampaignStatusRequest request
    ) {
        CampaignEntity campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CAMPAIGN_NOT_FOUND));
        campaign.changeStatus(request.status());
        orderCampaignContextService.evict(campaignId);
        cacheInitializer.warm(campaign, true);
        return toView(campaign);
    }

    @Transactional(readOnly = true)
    public void forceWarmup(long campaignId) {
        CampaignEntity campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CAMPAIGN_NOT_FOUND));
        cacheInitializer.warm(campaign, true);
    }

    private CampaignDtos.CampaignView toView(CampaignEntity campaign) {
        ProductEntity product = productRepository.findById(campaign.getProductId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        ProductVariantEntity variant = campaign.getVariantId() == null
                ? variantRepository.findAllByProductIdOrderByIdAsc(product.getId()).stream()
                        .findFirst()
                        .orElseThrow(() -> new BusinessException(ErrorCode.VARIANT_NOT_FOUND))
                : variantRepository.findById(campaign.getVariantId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.VARIANT_NOT_FOUND));
        return toView(campaign, product, variant);
    }

    private CampaignDtos.CampaignView toView(
            CampaignEntity campaign,
            ProductEntity product,
            ProductVariantEntity variant
    ) {
        String categoryName = product.getCategoryId() == null
                ? "未分类"
                : categoryRepository.findById(product.getCategoryId())
                        .map(category -> category.getName())
                        .orElse("未分类");
        return new CampaignDtos.CampaignView(
                campaign.getId(),
                campaign.getProductId(),
                variant.getId(),
                product.getName(),
                categoryName,
                product.getImageUrl(),
                variant.getSkuCode(),
                variant.getColorName(),
                variant.getSpecification(),
                variant.getPrice(),
                campaign.getFlashPrice(),
                campaign.getTotalStock(),
                campaign.getRemainingStock(),
                campaign.getPurchaseLimit(),
                campaign.getStartsAt(),
                campaign.getEndsAt(),
                campaign.getStatus(),
                product.isActive(),
                variant.isActive());
    }
}
