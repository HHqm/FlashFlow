package com.flashflow.trade.order;

import java.time.Instant;
import java.util.List;

import com.flashflow.trade.campaign.ReservationResult;
import com.flashflow.trade.address.ShippingAddressService;
import com.flashflow.trade.address.ShippingAddressSnapshot;
import com.flashflow.trade.campaign.SeckillRedisRepository;
import com.flashflow.trade.common.BusinessException;
import com.flashflow.trade.common.ErrorCode;
import com.flashflow.trade.inventory.StockCompensationService;
import com.flashflow.trade.inventory.StockReservation;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderCampaignContextService campaignContextService;
    private final OrderRepository orderRepository;
    private final OrderNumberGenerator orderNumberGenerator;
    private final SeckillRedisRepository seckillRedisRepository;
    private final OrderWriteCoordinator writeCoordinator;
    private final ShippingAddressService shippingAddressService;
    private final StockCompensationService stockCompensationService;
    private final Counter createdCounter;

    public OrderService(
            OrderCampaignContextService campaignContextService,
            OrderRepository orderRepository,
            OrderNumberGenerator orderNumberGenerator,
            SeckillRedisRepository seckillRedisRepository,
            OrderWriteCoordinator writeCoordinator,
            ShippingAddressService shippingAddressService,
            StockCompensationService stockCompensationService,
            MeterRegistry meterRegistry
    ) {
        this.campaignContextService = campaignContextService;
        this.orderRepository = orderRepository;
        this.orderNumberGenerator = orderNumberGenerator;
        this.seckillRedisRepository = seckillRedisRepository;
        this.writeCoordinator = writeCoordinator;
        this.shippingAddressService = shippingAddressService;
        this.stockCompensationService = stockCompensationService;
        this.createdCounter = meterRegistry.counter("flashflow.orders.created");
    }

    public OrderDtos.OrderView placeOrder(
            long userId,
            long campaignId,
            String idempotencyKey,
            int quantity,
            long addressId
    ) {
        OrderCampaignContext campaign = campaignContextService.resolve(campaignId);
        ShippingAddressSnapshot shippingAddress = shippingAddressService.resolveSnapshot(userId, addressId);
        validateCampaignWindow(campaign);
        if (quantity < 1 || quantity > campaign.purchaseLimit()) {
            throw new BusinessException(
                    ErrorCode.PURCHASE_LIMIT_EXCEEDED,
                    "本活动每人限购 " + campaign.purchaseLimit() + " 件");
        }
        seckillRedisRepository.checkRateLimit(userId);

        String orderNo = orderNumberGenerator.next();
        ReservationResult reservation = seckillRedisRepository.reserve(
                campaignId,
                userId,
                idempotencyKey,
                orderNo,
                campaign.endsAt(),
                quantity,
                campaign.purchaseLimit());

        return switch (reservation.status()) {
            case DUPLICATE -> replayExisting(reservation.orderNo());
            case SOLD_OUT -> throw new BusinessException(ErrorCode.SOLD_OUT);
            case ALREADY_PURCHASED, LIMIT_EXCEEDED -> throw new BusinessException(
                    ErrorCode.PURCHASE_LIMIT_EXCEEDED,
                    "本活动每人限购 " + campaign.purchaseLimit() + " 件");
            case RESERVED -> persistOrCompensate(
                    userId,
                    campaign,
                    idempotencyKey,
                    reservation.orderNo(),
                    quantity,
                    campaign.productSnapshot(),
                    shippingAddress);
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
            OrderCampaignContext campaign,
            String idempotencyKey,
            String orderNo,
            int quantity,
            OrderProductSnapshot productSnapshot,
            ShippingAddressSnapshot shippingAddress
    ) {
        try {
            OrderEntity order = writeCoordinator.persist(new OrderWriteCommand(
                    userId,
                    campaign,
                    idempotencyKey,
                    orderNo,
                    quantity,
                    productSnapshot,
                    shippingAddress));
            createdCounter.increment();
            return OrderDtos.OrderView.from(order, false);
        } catch (RuntimeException exception) {
            try {
                seckillRedisRepository.compensate(
                        campaign.campaignId(),
                        userId,
                        idempotencyKey,
                        orderNo,
                        quantity);
            } catch (RuntimeException compensationFailure) {
                log.error("Redis stock compensation failed, campaignId={}, orderNo={}",
                        campaign.campaignId(), orderNo, compensationFailure);
                exception.addSuppressed(compensationFailure);
                stockCompensationService.recordFailedWrite(
                        new StockReservation(
                                campaign.campaignId(), userId, idempotencyKey, orderNo, quantity),
                        exception);
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

    private void validateCampaignWindow(OrderCampaignContext campaign) {
        Instant now = Instant.now();
        if (campaign.status() != null
                && campaign.status() != com.flashflow.trade.campaign.CampaignStatus.ACTIVE) {
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
