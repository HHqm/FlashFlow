package com.flashflow.notification.message;

import java.util.List;

import com.flashflow.common.event.DomainEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class OrderEventConsumer {

    private final ObjectMapper objectMapper;
    private final NotificationHandler notificationHandler;

    public OrderEventConsumer(ObjectMapper objectMapper, NotificationHandler notificationHandler) {
        this.objectMapper = objectMapper;
        this.notificationHandler = notificationHandler;
    }

    @KafkaListener(
            topics = "${flashflow.kafka.order-events-topic}",
            groupId = "${flashflow.kafka.notification-group}",
            concurrency = "${flashflow.kafka.listener-concurrency:3}")
    public void consume(List<String> payloads) {
        List<DomainEvent> events = payloads.stream()
                .map(this::readEvent)
                .toList();
        notificationHandler.handleBatch(events);
    }

    private DomainEvent readEvent(String payload) {
        try {
            return objectMapper.readValue(payload, DomainEvent.class);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Invalid order event payload", exception);
        }
    }
}
