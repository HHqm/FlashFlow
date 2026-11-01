package com.flashflow.trade.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.flashflow.trade.campaign.SeckillRedisRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class StockCompensationServiceTest {

    private static final StockReservation RESERVATION =
            new StockReservation(10L, 7L, "request_12345678", "FF1234567890", 2);

    @Mock
    private StockCompensationRepository repository;

    @Mock
    private StockCompensationStateService stateService;

    @Mock
    private SeckillRedisRepository redisRepository;

    private SimpleMeterRegistry meterRegistry;
    private StockCompensationService service;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        service = new StockCompensationService(
                repository,
                stateService,
                redisRepository,
                Duration.ofSeconds(10),
                3,
                meterRegistry);
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void touchesRedisOnlyAfterTheOuterTransactionCommits() {
        TransactionSynchronizationManager.initSynchronization();
        when(repository.save(any(StockCompensationTask.class))).thenAnswer(invocation ->
                withId(invocation.getArgument(0), 42L));
        when(redisRepository.compensate(10L, 7L, "request_12345678", "FF1234567890", 2))
                .thenReturn(true);

        service.scheduleAfterCommit(RESERVATION, StockCompensationReason.REFUND_APPROVED);

        ArgumentCaptor<StockCompensationTask> saved = ArgumentCaptor.forClass(StockCompensationTask.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(StockCompensationStatus.PENDING);
        assertThat(saved.getValue().getNextAttemptAt())
                .as("the retry job must not race the immediate after-commit attempt")
                .isAfter(Instant.now().plusSeconds(5));
        verifyNoInteractions(redisRepository);

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);

        verify(stateService).markDone(42L, StockCompensationTask.Outcome.RESTORED);
    }

    @Test
    void failedImmediateAttemptLeavesTaskForTheRetryJob() {
        TransactionSynchronizationManager.initSynchronization();
        when(repository.save(any(StockCompensationTask.class))).thenAnswer(invocation ->
                withId(invocation.getArgument(0), 42L));
        when(redisRepository.compensate(anyLong(), anyLong(), anyString(), anyString(), anyInt()))
                .thenThrow(new QueryTimeoutException("redis timeout"));

        service.scheduleAfterCommit(RESERVATION, StockCompensationReason.PAYMENT_TIMEOUT);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);

        verify(stateService).markFailed(42L, "redis timeout", 3);
        verify(stateService, never()).markDone(eq(42L), any());
        assertThat(meterRegistry.counter("flashflow.stock.compensation.failed").count()).isEqualTo(1.0);
    }

    @Test
    void retryJobRecordsNoopWhenReservationWasAlreadyReturned() {
        StockCompensationTask task = withId(newTask(), 5L);
        when(repository.findByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
                eq(StockCompensationStatus.PENDING), any(Instant.class), any()))
                .thenReturn(List.of(task));
        when(redisRepository.compensate(10L, 7L, "request_12345678", "FF1234567890", 2))
                .thenReturn(false);

        int succeeded = service.retryDueTasks(100);

        assertThat(succeeded).isEqualTo(1);
        verify(stateService).markDone(5L, StockCompensationTask.Outcome.NOOP);
    }

    @Test
    void writeFailureTaskIsPersistedInItsOwnTransaction() {
        RuntimeException writeFailure = new IllegalStateException("database unavailable");

        service.recordFailedWrite(RESERVATION, writeFailure);

        ArgumentCaptor<StockCompensationTask> created = ArgumentCaptor.forClass(StockCompensationTask.class);
        verify(stateService).create(created.capture());
        assertThat(created.getValue().getReason()).isEqualTo(StockCompensationReason.ORDER_WRITE_FAILED);
        assertThat(created.getValue().getNextAttemptAt()).isBeforeOrEqualTo(Instant.now());
        assertThat(writeFailure.getSuppressed()).isEmpty();
    }

    @Test
    void countsLostCompensationWhenTaskCannotBePersisted() {
        RuntimeException writeFailure = new IllegalStateException("database unavailable");
        when(stateService.create(any())).thenThrow(new DataAccessResourceFailureException("db down"));

        service.recordFailedWrite(RESERVATION, writeFailure);

        assertThat(meterRegistry.counter("flashflow.stock.compensation.lost").count()).isEqualTo(1.0);
        assertThat(writeFailure.getSuppressed()).hasSize(1);
    }

    @Test
    void taskBacksOffExponentiallyAndGivesUpAfterMaxRetries() {
        StockCompensationTask task = newTask();
        Instant now = Instant.now();

        task.markFailed("timeout", 3, now);
        assertThat(task.getStatus()).isEqualTo(StockCompensationStatus.PENDING);
        assertThat(task.getNextAttemptAt()).isEqualTo(now.plusSeconds(2));

        task.markFailed("timeout", 3, now);
        assertThat(task.getNextAttemptAt()).isEqualTo(now.plusSeconds(4));

        task.markFailed("timeout", 3, now);
        assertThat(task.getStatus()).isEqualTo(StockCompensationStatus.FAILED);

        task.resetForRetry(now);
        assertThat(task.getStatus()).isEqualTo(StockCompensationStatus.PENDING);
        assertThat(task.getRetryCount()).isZero();
    }

    private static StockCompensationTask newTask() {
        Instant now = Instant.now();
        return new StockCompensationTask(
                RESERVATION.orderNo(),
                RESERVATION.campaignId(),
                RESERVATION.userId(),
                RESERVATION.idempotencyKey(),
                RESERVATION.quantity(),
                StockCompensationReason.CUSTOMER_CANCELLED,
                now,
                now);
    }

    private static StockCompensationTask withId(StockCompensationTask task, long id) {
        try {
            Field field = StockCompensationTask.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(task, id);
            return task;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
