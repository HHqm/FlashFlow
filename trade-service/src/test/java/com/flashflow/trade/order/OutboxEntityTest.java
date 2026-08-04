package com.flashflow.trade.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import com.flashflow.trade.outbox.OutboxEntity;
import com.flashflow.trade.outbox.OutboxStatus;
import org.junit.jupiter.api.Test;

class OutboxEntityTest {

    @Test
    void movesToFailedAfterRetryBudgetIsExhausted() {
        OutboxEntity event = new OutboxEntity(
                UUID.randomUUID(),
                "order.created.v1",
                "FF-1",
                "{}",
                Instant.now());

        event.markFailed("first", 2, Instant.now());
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);

        event.markFailed("second", 2, Instant.now());
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.FAILED);
    }
}
