package com.flashflow.trade.aftersale;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.flashflow.common.event.DomainEvent;
import com.flashflow.common.event.EventTypes;
import com.flashflow.trade.campaign.CampaignStockRepository;
import com.flashflow.trade.common.BusinessException;
import com.flashflow.trade.common.ErrorCode;
import com.flashflow.trade.inventory.StockCompensationReason;
import com.flashflow.trade.inventory.StockCompensationService;
import com.flashflow.trade.order.OrderDtos;
import com.flashflow.trade.order.OrderEntity;
import com.flashflow.trade.order.OrderRepository;
import com.flashflow.trade.outbox.OutboxService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AfterSaleService {

    private final OrderRepository orderRepository;
    private final ReturnRequestRepository returnRepository;
    private final CampaignStockRepository stockRepository;
    private final StockCompensationService stockCompensationService;
    private final OutboxService outboxService;

    public AfterSaleService(
            OrderRepository orderRepository,
            ReturnRequestRepository returnRepository,
            CampaignStockRepository stockRepository,
            StockCompensationService stockCompensationService,
            OutboxService outboxService
    ) {
        this.orderRepository = orderRepository;
        this.returnRepository = returnRepository;
        this.stockRepository = stockRepository;
        this.stockCompensationService = stockCompensationService;
        this.outboxService = outboxService;
    }

    @Transactional
    public OrderDtos.OrderView cancelPending(long userId, String orderNo, String reason) {
        OrderEntity order = ownedOrder(userId, orderNo);
        Instant now = Instant.now();
        String normalizedReason = normalize(reason, "客户主动取消");
        order.cancelByCustomer(normalizedReason, now);
        restoreDatabaseStock(order);
        outboxService.append(DomainEvent.create(
                EventTypes.ORDER_CANCELLED,
                orderNo,
                Map.of(
                        "orderNo", orderNo,
                        "userId", userId,
                        "campaignId", order.getCampaignId(),
                        "cancelledAt", now.toString(),
                        "reason", "CUSTOMER_CANCELLED",
                        "detail", normalizedReason)));
        stockCompensationService.scheduleAfterCommit(
                order.stockReservation(), StockCompensationReason.CUSTOMER_CANCELLED);
        return OrderDtos.OrderView.from(order, false);
    }

    @Transactional
    public ReturnDtos.ReturnView requestReturn(
            long userId,
            String orderNo,
            ReturnDtos.CreateReturnRequest request
    ) {
        OrderEntity order = ownedOrder(userId, orderNo);
        if (returnRepository.existsByOrderId(order.getId())) {
            throw new BusinessException(ErrorCode.RETURN_ALREADY_EXISTS);
        }
        Instant now = Instant.now();
        order.requestRefund(now);
        ReturnRequestEntity returnRequest = returnRepository.save(new ReturnRequestEntity(
                nextReturnNo(),
                order.getId(),
                orderNo,
                userId,
                request.reason().trim()));
        outboxService.append(DomainEvent.create(
                EventTypes.ORDER_REFUND_REQUESTED,
                orderNo,
                Map.of(
                        "orderNo", orderNo,
                        "returnNo", returnRequest.getReturnNo(),
                        "userId", userId,
                        "reason", returnRequest.getReason())));
        return toView(returnRequest, order);
    }

    @Transactional(readOnly = true)
    public List<ReturnDtos.ReturnView> listMine(long userId) {
        return returnRepository.findTop100ByUserIdOrderByRequestedAtDesc(userId).stream()
                .map(item -> toView(item, orderById(item.getOrderId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReturnDtos.ReturnView> listAll() {
        return returnRepository.findTop200ByOrderByRequestedAtDesc().stream()
                .map(item -> toView(item, orderById(item.getOrderId())))
                .toList();
    }

    @Transactional
    public ReturnDtos.ReturnView decide(
            long returnId,
            ReturnDtos.ReturnDecisionRequest request
    ) {
        ReturnRequestEntity returnRequest = returnRepository.findById(returnId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RETURN_NOT_FOUND));
        OrderEntity order = orderById(returnRequest.getOrderId());
        Instant now = Instant.now();
        String note = normalize(request.note(), request.approved() ? "审核通过" : "不符合退款条件");
        if (request.approved()) {
            returnRequest.approve(note, now);
            order.approveRefund(now);
            restoreDatabaseStock(order);
            outboxService.append(DomainEvent.create(
                    EventTypes.ORDER_REFUNDED,
                    order.getOrderNo(),
                    Map.of(
                            "orderNo", order.getOrderNo(),
                            "returnNo", returnRequest.getReturnNo(),
                            "userId", order.getUserId(),
                            "processedAt", now.toString(),
                            "adminNote", note)));
            stockCompensationService.scheduleAfterCommit(
                    order.stockReservation(), StockCompensationReason.REFUND_APPROVED);
        } else {
            returnRequest.reject(note, now);
            order.rejectRefund(now);
            outboxService.append(DomainEvent.create(
                    EventTypes.ORDER_REFUND_REJECTED,
                    order.getOrderNo(),
                    Map.of(
                            "orderNo", order.getOrderNo(),
                            "returnNo", returnRequest.getReturnNo(),
                            "userId", order.getUserId(),
                            "processedAt", now.toString(),
                            "adminNote", note)));
        }
        return toView(returnRequest, order);
    }

    private OrderEntity ownedOrder(long userId, String orderNo) {
        OrderEntity order = orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        if (order.getUserId() != userId) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return order;
    }

    private OrderEntity orderById(long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
    }

    private void restoreDatabaseStock(OrderEntity order) {
        if (stockRepository.increment(order.getCampaignId(), order.getStockBucket(), order.getQuantity()) != 1) {
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "活动库存恢复失败，请稍后重试");
        }
    }

    private ReturnDtos.ReturnView toView(ReturnRequestEntity request, OrderEntity order) {
        return new ReturnDtos.ReturnView(
                request.getId(),
                request.getReturnNo(),
                request.getOrderNo(),
                request.getUserId(),
                order.getProductName(),
                order.getVariantLabel(),
                order.getQuantity(),
                order.getAmount(),
                request.getReason(),
                request.getStatus(),
                request.getAdminNote(),
                request.getRequestedAt(),
                request.getProcessedAt());
    }

    private static String nextReturnNo() {
        return "RT" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
