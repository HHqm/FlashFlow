package com.flashflow.trade.inventory;

import java.time.Instant;

import com.flashflow.trade.common.BusinessException;
import com.flashflow.trade.common.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * State changes run in their own transaction because they are triggered from
 * {@code afterCommit} callbacks, where the outer transaction can no longer write.
 */
@Service
public class StockCompensationStateService {

    private final StockCompensationRepository repository;

    public StockCompensationStateService(StockCompensationRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public StockCompensationTask create(StockCompensationTask task) {
        return repository.save(task);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markDone(long taskId, StockCompensationTask.Outcome outcome) {
        repository.findById(taskId).ifPresent(task -> task.markDone(outcome, Instant.now()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(long taskId, String error, int maxRetries) {
        repository.findById(taskId).ifPresent(task -> task.markFailed(error, maxRetries, Instant.now()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public StockCompensationTask resetForRetry(long taskId) {
        StockCompensationTask task = repository.findById(taskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMPENSATION_TASK_NOT_FOUND));
        if (task.getStatus() != StockCompensationStatus.FAILED) {
            throw new BusinessException(ErrorCode.COMPENSATION_TASK_NOT_RETRYABLE);
        }
        task.resetForRetry(Instant.now());
        return task;
    }
}
