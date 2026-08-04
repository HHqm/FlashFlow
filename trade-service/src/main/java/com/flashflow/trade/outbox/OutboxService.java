package com.flashflow.trade.outbox;

import com.flashflow.common.event.DomainEvent;
import com.flashflow.trade.common.BusinessException;
import com.flashflow.trade.common.ErrorCode;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class OutboxService {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OutboxService(OutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    public void append(DomainEvent event) {
        try {
            outboxRepository.save(new OutboxEntity(
                    event.eventId(),
                    event.eventType(),
                    event.aggregateId(),
                    objectMapper.writeValueAsString(event),
                    event.occurredAt()));
        } catch (JacksonException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "领域事件序列化失败");
        }
    }
}
