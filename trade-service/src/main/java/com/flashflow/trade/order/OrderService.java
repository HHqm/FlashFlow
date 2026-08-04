package com.flashflow.trade.order;

import java.time.Instant;
import java.util.List;

import com.flashflow.trade.campaign.CampaignEntity;
import com.flashflow.trade.campaign.CampaignRepository;
import com.flashflow.trade.campaign.ReservationResult;
import com.flashflow.trade.campaign.SeckillRedisRepository;
import com.flashflow.trade.common.BusinessException;
import com.flashflow.trade.common.ErrorCode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final CampaignRepository campaignRepository;
    private final OrderRepository orderRepository;
    private final OrderNumberGenerator orderNumberGenerator;
    private final SeckillRedisRepository seckillRedisRepository;
    private final OrderTransactionalService transactionalService;
    private final Counter createdCounter;

    public OrderService(
            CampaignRepository campaignRepository,
            OrderRepository orderRepository,
            OrderNumberGenerator orderNumberGenerator,
            SeckillRedisRepository seckillRedisRepository,
            OrderTransactionalService transactionalService,
            MeterRegistry meterRegistry
    ) {
        this.campaignRepository = campaignRepository;
        this.orderRepository = orderRepository;
        this.orderNumberGenerator = orderNumberGenerator;
        this.seckillRedisRepository = seckillRedisRepository;
        this.transactionalService = transactionalService;
        this.createdCounter = meterRegistry.counter("flashflow.orders.created");
    }

    public OrderDtos.OrderView placeOrder(long userId, long campaignId, String idempotencyKey) {
        CampaignEntity campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CAMPAIGN_NOT_FOUND));
        validateCampaignWindow(campaign);
        seckillRedisRepository.checkRateLimit(userId);

        String orderNo = orderNumberGenerator.next();
        ReservationResult reservation = seckillRedisRepository.reserve(
                campaignId,
                userId,
                idempotencyKey,
                orderNo,
                campaign.getEndsAt());

        return switch (reservation.status()) {
            case DUPLICATE -> replayExisting(reservation.orderNo());
            case SOLD_OUT -> throw new BusinessException(ErrorCode.SOLD_OUT);
            case ALREADY_PURCHASED -> throw new BusinessException(ErrorCode.ALREADY_PURCHASED);
            case LIMIT_EXCEEDED -> throw new BusinessException(ErrorCode.PURCHASE_LIMIT_EXCEEDED);
            case RESERVED -> persistOrCompensate(
                    userId,
                    campaign,
                    idempotencyKey,
                    reservation.orderNo());
        };
    }

    public OrderDtos.OrderView placeOrder(
            long userId,
            long campaignId,
            String idempotencyKey,
            int quantity
    ) {
        CampaignEntity campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CAMPAIGN_NOT_FOUND));
        validateCampaignWindow(campaign);
        if (quantity < 1 || quantity > campaign.getPurchaseLimit()) {
            throw new BusinessException(
                    ErrorCode.PURCHASE_LIMIT_EXCEEDED,
                    "本活动每人限购 " + campaign.getPurchaseLimit() + " 件");
        }
        seckillRedisRepository.checkRateLimit(userId);

        String orderNo = orderNumberGenerator.next();
        ReservationResult reservation = seckillRedisRepository.reserve(
                campaignId,
                userId,
                idempotencyKey,
                orderNo,
                campaign.getEndsAt(),
                quantity,
                campaign.getPurchaseLimit());

        return switch (reservation.status()) {
            case DUPLICATE -> replayExisting(reservation.orderNo());
            case SOLD_OUT -> throw new BusinessException(ErrorCode.SOLD_OUT);
            case ALREADY_PURCHASED, LIMIT_EXCEEDED -> throw new BusinessException(
                    ErrorCode.PURCHASE_LIMIT_EXCEEDED,
                    "本活动每人限购 " + campaign.getPurchaseLimit() + " 件");
            case RESERVED -> persistOrCompensate(
                    userId,
                    campaign,
                    idempotencyKey,
                    reservation.orderNo(),
                    quantity);
        };
    }

    @Transactional(readOnly = true)
    public List<OrderDtos.OrderView> listMyOrders(long userId) {
        return orderRepository.findTop100ByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(order -> OrderDtos.OrderView.from(order, false))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OrderDtos.OrderView> listAllOrders() {
        return orderRepository.findTop200ByOrderByCreatedAtDesc().stream()
                .map(order -> OrderDtos.OrderView.from(order, false))
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderDtos.OrderView getOrder(long userId, String orderNo, boolean admin) {
        OrderEntity order = orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        if (!admin && order.getUserId() != userId) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return OrderDtos.OrderView.from(order, false);
    }

    private OrderDtos.OrderView persistOrCompensate(
            long userId,
            CampaignEntity campaign,
            String idempotencyKey,
            String orderNo
    ) {
        try {
            OrderEntity order = transactionalService.persistReservedOrder(
                    userId,
                    campaign,
                    idempotencyKey,
                    orderNo);
            createdCounter.increment();
            return OrderDtos.OrderView.from(order, false);
        } catch (RuntimeException exception) {
            try {
                seckillRedisRepository.compensate(
                        campaign.getId(),
                        userId,
                        idempotencyKey,
                        orderNo);
            } catch (RuntimeException compensationFailure) {
                log.error("Redis stock compensation failed, campaignId={}, orderNo={}",
                        campaign.getId(), orderNo, compensationFailure);
                exception.addSuppressed(compensationFailure);
            }
            throw exception;
        }
    }

    private OrderDtos.OrderView persistOrCompensate(
            long userId,
            CampaignEntity campaign,
            String idempotencyKey,
            String orderNo,
            int quantity
    ) {
        try {
            OrderEntity order = transactionalService.persistReservedOrder(
                    userId,
                    campaign,
                    idempotencyKey,
                    orderNo,
                    quantity);
            createdCounter.increment();
            return OrderDtos.OrderView.from(order, false);
        } catch (RuntimeException exception) {
            try {
                seckillRedisRepository.compensate(
                        campaign.getId(),
                        userId,
                        idempotencyKey,
                        orderNo,
                        quantity);
            } catch (RuntimeException compensationFailure) {
                log.error("Redis stock compensation failed, campaignId={}, orderNo={}",
                        campaign.getId(), orderNo, compensationFailure);
                exception.addSuppressed(compensationFailure);
            }
            throw exception;
        }
    }

    private OrderDtos.OrderView replayExisting(String orderNo) {
        return orderRepository.findByOrderNo(orderNo)
                .map(order -> OrderDtos.OrderView.from(order, true))
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.REQUEST_PROCESSING,
                        "订单正在创建中，订单号：" + orderNo));
    }

    private void validateCampaignWindow(CampaignEntity campaign) {
        Instant now = Instant.now();
        if (campaign.getStatus() != null
                && campaign.getStatus() != com.flashflow.trade.campaign.CampaignStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.CAMPAIGN_UNAVAILABLE);
        }
        if (!campaign.hasStarted(now)) {
            throw new BusinessException(ErrorCode.CAMPAIGN_NOT_STARTED);
        }
        if (campaign.hasEnded(now)) {
            throw new BusinessException(ErrorCode.CAMPAIGN_ENDED);
        }
    }
}
