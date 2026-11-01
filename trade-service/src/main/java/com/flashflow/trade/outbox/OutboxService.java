package com.flashflow.trade.outbox;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.flashflow.common.event.DomainEvent;
import com.flashflow.trade.common.BusinessException;
import com.flashflow.trade.common.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class OutboxService {

    /** How committed Outbox rows reach Kafka. */
    public enum Mode {
        /** {@link OutboxPublisher} polls PENDING rows and marks them PUBLISHED. */
        POLLING,
        /**
         * Debezium tails the MySQL binlog. The row is deleted in the same transaction that inserted
         * it: the binlog still carries the insert, and the table never grows.
         */
        CDC
    }

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final Mode mode;

    public OutboxService(
            OutboxRepository outboxRepository,
            ObjectMapper objectMapper,
            @Value("${flashflow.outbox.mode:POLLING}") Mode mode
    ) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.mode = mode;
    }

    public void append(DomainEvent event) {
        appendAll(List.of(event));
    }

    public void appendAll(List<DomainEvent> events) {
        List<OutboxEntity> entities = new ArrayList<>(events.size());
        try {
            for (DomainEvent event : events) {
                entities.add(new OutboxEntity(
                        event.eventId(),
                        event.eventType(),
                        event.aggregateId(),
                        objectMapper.writeValueAsString(event),
                        event.occurredAt()));
            }
        } catch (JacksonException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "领域事件序列化失败");
        }
        outboxRepository.saveAll(entities);
        if (mode == Mode.CDC) {
            // Flush first so the INSERT reaches the binlog before the DELETE; both commit together.
            outboxRepository.flush();
            List<UUID> ids = entities.stream().map(OutboxEntity::getEventId).toList();
            outboxRepository.deleteCapturedEvents(ids);
        }
    }
}
