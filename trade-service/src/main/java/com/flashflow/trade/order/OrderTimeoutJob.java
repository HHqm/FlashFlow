package com.flashflow.trade.order;

import java.time.Duration;
import java.time.Instant;

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
    private final Duration paymentTimeout;

    public OrderTimeoutJob(
            OrderRepository orderRepository,
            OrderCommandService commandService,
            @Value("${flashflow.order.payment-timeout:PT15M}") Duration paymentTimeout
    ) {
        this.orderRepository = orderRepository;
        this.commandService = commandService;
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
                // Redis compensation is scheduled inside the cancel transaction and retried
                // durably, so a Redis outage here no longer leaks reserved stock.
                commandService.cancelIfExpired(order.getOrderNo(), cutoff);
            } catch (RuntimeException exception) {
                log.error("Failed to cancel expired order, orderNo={}", order.getOrderNo(), exception);
            }
        }
    }
}
