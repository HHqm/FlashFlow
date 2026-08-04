package com.flashflow.trade.order;

import java.time.Instant;
import java.util.Map;

import com.flashflow.common.event.DomainEvent;
import com.flashflow.common.event.EventTypes;
import com.flashflow.trade.campaign.CampaignEntity;
import com.flashflow.trade.campaign.CampaignRepository;
import com.flashflow.trade.common.BusinessException;
import com.flashflow.trade.common.ErrorCode;
import com.flashflow.trade.outbox.OutboxService;
import com.flashflow.trade.product.ProductEntity;
import com.flashflow.trade.product.ProductRepository;
import com.flashflow.trade.product.ProductVariantEntity;
import com.flashflow.trade.product.ProductVariantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderTransactionalService {

    private final CampaignRepository campaignRepository;
    private final OrderRepository orderRepository;
    private final OutboxService outboxService;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;

    public OrderTransactionalService(
            CampaignRepository campaignRepository,
            OrderRepository orderRepository,
            OutboxService outboxService,
            ProductRepository productRepository,
            ProductVariantRepository variantRepository
    ) {
        this.campaignRepository = campaignRepository;
        this.orderRepository = orderRepository;
        this.outboxService = outboxService;
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
    }

    @Transactional
    public OrderEntity persistReservedOrder(
            long userId,
            CampaignEntity campaign,
            String idempotencyKey,
            String orderNo
    ) {
        return persistReservedOrder(userId, campaign, idempotencyKey, orderNo, 1);
    }

    @Transactional
    public OrderEntity persistReservedOrder(
            long userId,
            CampaignEntity campaign,
            String idempotencyKey,
            String orderNo,
            int quantity
    ) {
        Instant now = Instant.now();
        if (!campaign.hasStarted(now)) {
            throw new BusinessException(ErrorCode.CAMPAIGN_NOT_STARTED);
        }
        if (campaign.hasEnded(now)) {
            throw new BusinessException(ErrorCode.CAMPAIGN_ENDED);
        }
        if (campaignRepository.decrementStock(campaign.getId(), quantity) != 1) {
            throw new BusinessException(ErrorCode.SOLD_OUT);
        }

        ProductEntity product = productRepository.findById(campaign.getProductId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        ProductVariantEntity variant = campaign.getVariantId() == null
                ? variantRepository.findAllByProductIdOrderByIdAsc(product.getId()).stream()
                        .findFirst()
                        .orElseThrow(() -> new BusinessException(ErrorCode.VARIANT_NOT_FOUND))
                : variantRepository.findById(campaign.getVariantId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.VARIANT_NOT_FOUND));
        java.math.BigDecimal amount = campaign.getFlashPrice()
                .multiply(java.math.BigDecimal.valueOf(quantity));

        OrderEntity order = orderRepository.save(new OrderEntity(
                orderNo,
                userId,
                campaign.getId(),
                campaign.getProductId(),
                variant.getId(),
                amount,
                quantity,
                product.getName(),
                variant.getColorName() + " · " + variant.getSpecification(),
                idempotencyKey));
        outboxService.append(DomainEvent.create(
                EventTypes.ORDER_CREATED,
                orderNo,
                Map.of(
                        "orderNo", orderNo,
                        "userId", userId,
                        "campaignId", campaign.getId(),
                        "productId", campaign.getProductId(),
                        "variantId", variant.getId(),
                        "quantity", quantity,
                        "amount", amount.toPlainString(),
                        "status", order.getStatus().name())));
        return order;
    }
}
