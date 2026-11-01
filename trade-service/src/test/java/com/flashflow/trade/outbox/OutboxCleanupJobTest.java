package com.flashflow.trade.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;

import com.flashflow.trade.common.RedisDistributedLock;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboxCleanupJobTest {

    @Mock
    private OutboxStateService stateService;

    @Mock
    private RedisDistributedLock distributedLock;

    @Test
    void deletesInChunksUntilAPartialChunkAndCountsTheRows() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        OutboxCleanupJob job = new OutboxCleanupJob(
                stateService, distributedLock, Duration.ofDays(7), 100, 20, meterRegistry);
        when(stateService.purgePublishedBefore(any(Instant.class), eq(100)))
                .thenReturn(100, 100, 37);

        int purged = job.purge(Instant.now());

        assertThat(purged).isEqualTo(237);
        verify(stateService, times(3)).purgePublishedBefore(any(Instant.class), eq(100));
        assertThat(meterRegistry.counter("flashflow.outbox.purged").count()).isEqualTo(237.0);
    }

    @Test
    void stopsAfterTheChunkBudgetSoOneRunNeverHogsTheDatabase() {
        OutboxCleanupJob job = new OutboxCleanupJob(
                stateService, distributedLock, Duration.ofDays(7), 100, 3, new SimpleMeterRegistry());
        when(stateService.purgePublishedBefore(any(Instant.class), eq(100))).thenReturn(100);

        assertThat(job.purge(Instant.now())).isEqualTo(300);
        verify(stateService, times(3)).purgePublishedBefore(any(Instant.class), eq(100));
    }
}
