package com.flashflow.trade.order;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import com.flashflow.common.event.DomainEvent;
import com.flashflow.common.event.EventTypes;
import com.flashflow.trade.campaign.CampaignRepository;
import com.flashflow.trade.common.BusinessException;
import com.flashflow.trade.common.ErrorCode;
import com.flashflow.trade.outbox.OutboxService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderCommandService {

    private final OrderRepository orderRepository;
    private final CampaignRepository campaignRepository;
    private final OutboxService outboxService;
    private final Duration paymentTimeout;

    public OrderCommandService(
            OrderRepository orderRepository,
            CampaignRepository campaignRepository,
            OutboxService outboxService,
            @Value("${flashflow.order.payment-timeout:PT15M}") Duration paymentTimeout
    ) {
        this.orderRepository = orderRepository;
        this.campaignRepository = campaignRepository;
        this.outboxService = outboxService;
        this.paymentTimeout = paymentTimeout;
    }

    @Transactional
    public OrderDtos.OrderView pay(long userId, String orderNo) {
        OrderEntity order = orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        if (order.getUserId() != userId) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        Instant now = Instant.now();
        // The timeout job only sweeps in batches, so the deadline is enforced here as well.
        if (order.getCreatedAt().isBefore(now.minus(paymentTimeout))) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID, "订单已超过支付时限");
        }
        order.pay(now);
        outboxService.append(DomainEvent.create(
                EventTypes.ORDER_PAID,
                orderNo,
                Map.of(
                        "orderNo", orderNo,
                        "userId", userId,
                        "paidAt", now.toString(),
                        "amount", order.getAmount().toPlainString())));
        return OrderDtos.OrderView.from(order, false);
    }

    @Transactional
    public OrderDtos.OrderView ship(String orderNo, OrderDtos.ShipOrderRequest request) {
        OrderEntity order = orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        Instant now = Instant.now();
        order.ship(request.carrier().trim(), request.trackingNo().trim(), now);
        outboxService.append(DomainEvent.create(
                EventTypes.ORDER_SHIPPED,
                orderNo,
                Map.of(
                        "orderNo", orderNo,
                        "userId", order.getUserId(),
                        "carrier", order.getCarrier(),
                        "trackingNo", order.getTrackingNo(),
                        "shippedAt", now.toString())));
        return OrderDtos.OrderView.from(order, false);
    }

    @Transactional
    public OrderDtos.OrderView receive(long userId, String orderNo) {
        OrderEntity order = orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        if (order.getUserId() != userId) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        Instant now = Instant.now();
        order.receive(now);
        outboxService.append(DomainEvent.create(
                EventTypes.ORDER_RECEIVED,
                orderNo,
                Map.of(
                        "orderNo", orderNo,
                        "userId", userId,
                        "receivedAt", now.toString())));
        return OrderDtos.OrderView.from(order, false);
    }

    @Transactional
    public Optional<CancelledOrder> cancelIfExpired(String orderNo, Instant cutoff) {
        OrderEntity order = orderRepository.findByOrderNo(orderNo).orElse(null);
        if (order == null || !order.getCreatedAt().isBefore(cutoff)) {
            return Optional.empty();
        }
        Instant now = Instant.now();
        if (!order.cancelIfPending(now)) {
            return Optional.empty();
        }
        campaignRepository.incrementStock(order.getCampaignId(), order.getQuantity());
        outboxService.append(DomainEvent.create(
                EventTypes.ORDER_CANCELLED,
                orderNo,
                Map.of(
                        "orderNo", orderNo,
                        "userId", order.getUserId(),
                        "campaignId", order.getCampaignId(),
                        "cancelledAt", now.toString(),
                        "reason", "PAYMENT_TIMEOUT")));
        return Optional.of(new CancelledOrder(
                order.getCampaignId(),
                order.getUserId(),
                order.getIdempotencyKey(),
                order.getOrderNo(),
                order.getQuantity()));
    }

    public record CancelledOrder(
            long campaignId,
            long userId,
            String idempotencyKey,
            String orderNo,
            int quantity
    ) {
    }
}
