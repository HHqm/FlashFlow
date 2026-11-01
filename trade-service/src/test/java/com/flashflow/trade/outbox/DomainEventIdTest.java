package com.flashflow.trade.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.flashflow.common.event.DomainEvent;
import org.junit.jupiter.api.Test;

class DomainEventIdTest {

    @Test
    void eventIdsAreVersionSevenAndOrderedByCreationTime() throws InterruptedException {
        UUID earlier = DomainEvent.timeOrderedId();
        Thread.sleep(2);
        UUID later = DomainEvent.timeOrderedId();

        assertThat(earlier.version()).isEqualTo(7);
        assertThat(earlier.variant()).isEqualTo(2);
        // Unsigned order of the leading 64 bits is what InnoDB sees for a BINARY(16) key.
        assertThat(Long.compareUnsigned(earlier.getMostSignificantBits(), later.getMostSignificantBits()))
                .isNegative();
    }

    @Test
    void eventIdsStayUniqueWithinTheSameMillisecond() {
        Set<UUID> ids = new HashSet<>();
        for (int index = 0; index < 100_000; index++) {
            ids.add(DomainEvent.timeOrderedId());
        }
        assertThat(ids).hasSize(100_000);
    }
}
