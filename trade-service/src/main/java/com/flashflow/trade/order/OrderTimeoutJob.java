package com.flashflow.trade.order;

import java.time.Duration;
import java.time.Instant;

import com.flashflow.trade.campaign.SeckillRedisRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "flashflow.jobs.order-timeout-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class OrderTimeoutJob {

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutJob.class);

    private final OrderRepository orderRepository;
    private final OrderCommandService commandService;
    private final SeckillRedisRepository redisRepository;
    private final Duration paymentTimeout;

    public OrderTimeoutJob(
            OrderRepository orderRepository,
            OrderCommandService commandService,
            SeckillRedisRepository redisRepository,
            @Value("${flashflow.order.payment-timeout:PT15M}") Duration paymentTimeout
    ) {
        this.orderRepository = orderRepository;
        this.commandService = commandService;
        this.redisRepository = redisRepository;
        this.paymentTimeout = paymentTimeout;
    }

    @Scheduled(fixedDelayString = "${flashflow.jobs.order-timeout-delay-ms:30000}")
    public void cancelExpiredOrders() {
        Instant cutoff = Instant.now().minus(paymentTimeout);
        var candidates = orderRepository
                .findTop100ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
                        OrderStatus.PENDING_PAYMENT,
                        cutoff);
        for (OrderEntity order : candidates) {
            try {
                commandService.cancelIfExpired(order.getOrderNo(), cutoff)
                        .ifPresent(this::compensateRedis);
            } catch (RuntimeException exception) {
                log.error("Failed to cancel expired order, orderNo={}", order.getOrderNo(), exception);
            }
        }
    }

    private void compensateRedis(OrderCommandService.CancelledOrder order) {
        try {
            redisRepository.compensate(
                    order.campaignId(),
                    order.userId(),
                    order.idempotencyKey(),
                    order.orderNo(),
                    order.quantity());
        } catch (RuntimeException exception) {
            // DB stock is already restored. A Redis failure can only cause temporary underselling.
            log.error("Cancelled order Redis compensation failed, orderNo={}", order.orderNo(), exception);
        }
    }
}
