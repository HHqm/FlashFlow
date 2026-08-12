package com.flashflow.trade.order;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.flashflow.common.event.DomainEvent;
import com.flashflow.common.event.EventTypes;
import com.flashflow.trade.campaign.CampaignRepository;
import com.flashflow.trade.common.BusinessException;
import com.flashflow.trade.common.ErrorCode;
import com.flashflow.trade.outbox.OutboxService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderTransactionalService {

    private final CampaignRepository campaignRepository;
    private final OrderRepository orderRepository;
    private final OutboxService outboxService;

    public OrderTransactionalService(
            CampaignRepository campaignRepository,
            OrderRepository orderRepository,
            OutboxService outboxService
    ) {
        this.campaignRepository = campaignRepository;
        this.orderRepository = orderRepository;
        this.outboxService = outboxService;
    }

    @Transactional
    public OrderEntity persistReservedOrder(
            long userId,
            OrderCampaignContext campaign,
            String idempotencyKey,
            String orderNo,
            OrderProductSnapshot productSnapshot
    ) {
        return persistReservedOrders(List.of(new OrderWriteCommand(
                userId,
                campaign,
                idempotencyKey,
                orderNo,
                1,
                productSnapshot,
                new com.flashflow.trade.address.ShippingAddressSnapshot(
                        0L, "测试收件人", "13800000000", "测试收货地址")))).getFirst();
    }

    @Transactional
    public OrderEntity persistReservedOrder(
            long userId,
            OrderCampaignContext campaign,
            String idempotencyKey,
            String orderNo,
            int quantity,
            OrderProductSnapshot productSnapshot
    ) {
        return persistReservedOrders(List.of(new OrderWriteCommand(
                userId,
                campaign,
                idempotencyKey,
                orderNo,
                quantity,
                productSnapshot,
                new com.flashflow.trade.address.ShippingAddressSnapshot(
                        0L, "测试收件人", "13800000000", "测试收货地址")))).getFirst();
    }

    @Transactional
    public List<OrderEntity> persistReservedOrders(List<OrderWriteCommand> commands) {
        if (commands.isEmpty()) {
            return List.of();
        }
        OrderCampaignContext campaign = commands.getFirst().campaign();
        Instant now = Instant.now();
        if (!campaign.hasStarted(now)) {
            throw new BusinessException(ErrorCode.CAMPAIGN_NOT_STARTED);
        }
        if (campaign.hasEnded(now)) {
            throw new BusinessException(ErrorCode.CAMPAIGN_ENDED);
        }
        int totalQuantity = commands.stream().mapToInt(OrderWriteCommand::quantity).sum();
        boolean mixedCampaigns = commands.stream()
                .anyMatch(command -> command.campaign().campaignId() != campaign.campaignId());
        if (mixedCampaigns) {
            throw new IllegalArgumentException("A write batch must contain one campaign");
        }
        if (campaignRepository.decrementStock(campaign.campaignId(), totalQuantity) != 1) {
            throw new BusinessException(ErrorCode.SOLD_OUT);
        }

        List<OrderEntity> orders = new ArrayList<>(commands.size());
        List<DomainEvent> events = new ArrayList<>(commands.size());
        for (OrderWriteCommand command : commands) {
            java.math.BigDecimal amount = campaign.flashPrice()
                    .multiply(java.math.BigDecimal.valueOf(command.quantity()));
            OrderProductSnapshot snapshot = command.productSnapshot();
            OrderEntity order = new OrderEntity(
                    command.orderNo(),
                    command.userId(),
                    campaign.campaignId(),
                    snapshot.productId(),
                    snapshot.variantId(),
                    amount,
                    command.quantity(),
                    snapshot.productName(),
                    snapshot.variantLabel(),
                    command.idempotencyKey(),
                    command.shippingAddress().recipientName(),
                    command.shippingAddress().phone(),
                    command.shippingAddress().fullAddress());
            orders.add(order);
            events.add(DomainEvent.create(
                    EventTypes.ORDER_CREATED,
                    command.orderNo(),
                    Map.of(
                            "orderNo", command.orderNo(),
                            "userId", command.userId(),
                            "campaignId", campaign.campaignId(),
                            "productId", snapshot.productId(),
                            "variantId", snapshot.variantId(),
                            "quantity", command.quantity(),
                            "amount", amount.toPlainString(),
                            "status", order.getStatus().name())));
        }
        orderRepository.saveAll(orders);
        outboxService.appendAll(events);
        return orders;
    }
}
