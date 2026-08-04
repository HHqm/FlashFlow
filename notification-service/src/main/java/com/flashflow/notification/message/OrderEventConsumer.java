package com.flashflow.notification.message;

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
            groupId = "${flashflow.kafka.notification-group}")
    public void consume(String payload) throws JacksonException {
        DomainEvent event = objectMapper.readValue(payload, DomainEvent.class);
        notificationHandler.handle(event);
    }
}
