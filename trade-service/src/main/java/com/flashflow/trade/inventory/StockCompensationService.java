package com.flashflow.trade.inventory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import com.flashflow.trade.campaign.SeckillRedisRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Turns "give the Redis reservation back" into a durable task, the same way the Outbox turns
 * "publish the event" into a durable row. The compensation script is idempotent per order
 * number, so the task can be retried until Redis answers.
 */
@Service
public class StockCompensationService {

    private static final Logger log = LoggerFactory.getLogger(StockCompensationService.class);

    private final StockCompensationRepository repository;
    private final StockCompensationStateService stateService;
    private final SeckillRedisRepository redisRepository;
    private final Duration retryGracePeriod;
    private final int maxRetries;
    private final Counter succeededCounter;
    private final Counter failedCounter;
    private final Counter lostCounter;
    private final AtomicLong pendingTasks = new AtomicLong();
    private final AtomicLong deadTasks = new AtomicLong();

    public StockCompensationService(
            StockCompensationRepository repository,
            StockCompensationStateService stateService,
            SeckillRedisRepository redisRepository,
            @Value("${flashflow.stock-compensation.retry-grace-period:PT10S}") Duration retryGracePeriod,
            @Value("${flashflow.stock-compensation.max-retries:12}") int maxRetries,
            MeterRegistry meterRegistry
    ) {
        this.repository = repository;
        this.stateService = stateService;
        this.redisRepository = redisRepository;
        this.retryGracePeriod = retryGracePeriod;
        this.maxRetries = maxRetries;
        this.succeededCounter = meterRegistry.counter("flashflow.stock.compensation.succeeded");
        this.failedCounter = meterRegistry.counter("flashflow.stock.compensation.failed");
        this.lostCounter = meterRegistry.counter("flashflow.stock.compensation.lost");
        Gauge.builder("flashflow.stock.compensation.pending", pendingTasks, AtomicLong::get)
                .register(meterRegistry);
        Gauge.builder("flashflow.stock.compensation.dead", deadTasks, AtomicLong::get)
                .register(meterRegistry);
    }

    /**
     * Must run inside the transaction that restored the MySQL stock: the task commits together
     * with it, and Redis is only touched after that commit succeeds.
     */
    public void scheduleAfterCommit(StockReservation reservation, StockCompensationReason reason) {
        Instant now = Instant.now();
        StockCompensationTask task = repository.save(newTask(
                reservation, reason, now, now.plus(retryGracePeriod)));
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                attempt(task.getId(), reservation);
            }
        });
    }

    /**
     * Used when the order transaction itself failed and the immediate Redis compensation also
     * failed. The task is written in a fresh transaction; if even that fails, reconciliation is
     * the last line of defence.
     */
    public void recordFailedWrite(StockReservation reservation, RuntimeException cause) {
        Instant now = Instant.now();
        try {
            stateService.create(newTask(
                    reservation, StockCompensationReason.ORDER_WRITE_FAILED, now, now));
            log.warn("Redis compensation deferred to retry job, orderNo={}", reservation.orderNo());
        } catch (DataIntegrityViolationException duplicate) {
            log.info("Compensation task already exists, orderNo={}", reservation.orderNo());
        } catch (RuntimeException exception) {
            lostCounter.increment();
            cause.addSuppressed(exception);
            log.error("Compensation task could not be persisted, campaignId={}, orderNo={}",
                    reservation.campaignId(), reservation.orderNo(), exception);
        }
    }

    public int retryDueTasks(int batchSize) {
        List<StockCompensationTask> due = repository
                .findByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
                        StockCompensationStatus.PENDING,
                        Instant.now(),
                        PageRequest.of(0, batchSize));
        int succeeded = 0;
        for (StockCompensationTask task : due) {
            if (attempt(task.getId(), toReservation(task))) {
                succeeded++;
            }
        }
        return succeeded;
    }

    public StockCompensationTask retryNow(long taskId) {
        StockCompensationTask task = stateService.resetForRetry(taskId);
        attempt(task.getId(), toReservation(task));
        return repository.findById(taskId).orElse(task);
    }

    public void refreshGauges() {
        pendingTasks.set(repository.countByStatus(StockCompensationStatus.PENDING));
        deadTasks.set(repository.countByStatus(StockCompensationStatus.FAILED));
    }

    private boolean attempt(long taskId, StockReservation reservation) {
        boolean restored;
        try {
            restored = redisRepository.compensate(
                    reservation.campaignId(),
                    reservation.userId(),
                    reservation.idempotencyKey(),
                    reservation.orderNo(),
                    reservation.quantity());
        } catch (RuntimeException exception) {
            failedCounter.increment();
            log.warn("Redis compensation attempt failed, taskId={}, orderNo={}",
                    taskId, reservation.orderNo(), exception);
            try {
                stateService.markFailed(taskId, exception.getMessage(), maxRetries);
            } catch (RuntimeException stateFailure) {
                // The task stays PENDING with its old schedule, so the retry job picks it up again.
                log.error("Compensation task state update failed, taskId={}", taskId, stateFailure);
            }
            return false;
        }
        succeededCounter.increment();
        try {
            stateService.markDone(
                    taskId,
                    restored ? StockCompensationTask.Outcome.RESTORED : StockCompensationTask.Outcome.NOOP);
        } catch (RuntimeException stateFailure) {
            // Redis is already correct; a later retry is a harmless NOOP.
            log.error("Compensation task state update failed, taskId={}", taskId, stateFailure);
        }
        return true;
    }

    private static StockCompensationTask newTask(
            StockReservation reservation,
            StockCompensationReason reason,
            Instant now,
            Instant firstAttemptAt
    ) {
        return new StockCompensationTask(
                reservation.orderNo(),
                reservation.campaignId(),
                reservation.userId(),
                reservation.idempotencyKey(),
                reservation.quantity(),
                reason,
                now,
                firstAttemptAt);
    }

    private static StockReservation toReservation(StockCompensationTask task) {
        return new StockReservation(
                task.getCampaignId(),
                task.getUserId(),
                task.getIdempotencyKey(),
                task.getOrderNo(),
                task.getQuantity());
    }
}
