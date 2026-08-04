package com.flashflow.notification.message;

import java.time.Instant;

import com.flashflow.common.event.DomainEvent;
import com.flashflow.common.event.EventTypes;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationHandler {

    private static final Logger log = LoggerFactory.getLogger(NotificationHandler.class);

    private final ProcessedMessageRepository processedMessageRepository;
    private final NotificationRepository notificationRepository;
    private final Counter consumedCounter;
    private final Counter duplicateCounter;

    public NotificationHandler(
            ProcessedMessageRepository processedMessageRepository,
            NotificationRepository notificationRepository,
            MeterRegistry meterRegistry
    ) {
        this.processedMessageRepository = processedMessageRepository;
        this.notificationRepository = notificationRepository;
        this.consumedCounter = meterRegistry.counter("flashflow.notifications.consumed");
        this.duplicateCounter = meterRegistry.counter("flashflow.notifications.duplicates");
    }

    @Transactional
    public void handle(DomainEvent event) {
        int inserted = processedMessageRepository.insertIfAbsent(
                event.eventId(),
                event.eventType(),
                Instant.now());
        if (inserted == 0) {
            duplicateCounter.increment();
            log.info("Duplicate event ignored, eventId={}", event.eventId());
            return;
        }

        long userId = numberValue(event, "userId");
        NotificationContent content = contentFor(event);
        notificationRepository.save(new NotificationEntity(
                event.eventId(),
                userId,
                content.templateCode(),
                content.content()));
        consumedCounter.increment();
    }

    private NotificationContent contentFor(DomainEvent event) {
        String orderNo = stringValue(event, "orderNo");
        return switch (event.eventType()) {
            case EventTypes.ORDER_CREATED -> new NotificationContent(
                    "ORDER_CREATED",
                    "订单 " + orderNo + " 创建成功，请在 15 分钟内完成支付。");
            case EventTypes.ORDER_PAID -> new NotificationContent(
                    "ORDER_PAID",
                    "订单 " + orderNo + " 支付成功，正在为您安排履约。");
            case EventTypes.ORDER_CANCELLED -> new NotificationContent(
                    "ORDER_CANCELLED",
                    "PAYMENT_TIMEOUT".equals(String.valueOf(event.payload().get("reason")))
                            ? "订单 " + orderNo + " 已因超时未支付自动取消。"
                            : "订单 " + orderNo + " 已取消，库存已经恢复。");
            case EventTypes.ORDER_REFUND_REQUESTED -> new NotificationContent(
                    "ORDER_REFUND_REQUESTED",
                    "订单 " + orderNo + " 的退款申请已提交，等待商家审核。");
            case EventTypes.ORDER_REFUNDED -> new NotificationContent(
                    "ORDER_REFUNDED",
                    "订单 " + orderNo + " 已通过退款审核，库存已经恢复。");
            case EventTypes.ORDER_REFUND_REJECTED -> new NotificationContent(
                    "ORDER_REFUND_REJECTED",
                    "订单 " + orderNo + " 的退款申请未通过，请查看商家说明。");
            default -> throw new IllegalArgumentException("Unsupported event type: " + event.eventType());
        };
    }

    private static long numberValue(DomainEvent event, String name) {
        Object value = event.payload().get(name);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private static String stringValue(DomainEvent event, String name) {
        return String.valueOf(event.payload().get(name));
    }

    private record NotificationContent(String templateCode, String content) {
    }
}
