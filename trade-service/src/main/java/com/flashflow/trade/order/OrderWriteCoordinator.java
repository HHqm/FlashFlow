package com.flashflow.trade.order;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

import com.flashflow.trade.common.BusinessException;
import com.flashflow.trade.common.ErrorCode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class OrderWriteCoordinator {

    private final OrderTransactionalService transactionalService;
    private final int stripeCount;
    private final int queueCapacity;
    private final int maxBatchSize;
    private final Duration batchWindow;
    private final List<ArrayBlockingQueue<PendingOrder>> queues;
    private final List<Thread> workers = new ArrayList<>();
    private final Counter rejectedCounter;
    private final Counter splitCounter;
    private final DistributionSummary batchSizeSummary;
    private volatile boolean running;

    public OrderWriteCoordinator(
            OrderTransactionalService transactionalService,
            @Value("${flashflow.order.write-stripes:4}") int stripeCount,
            @Value("${flashflow.order.write-queue-capacity:2048}") int queueCapacity,
            @Value("${flashflow.order.write-batch-size:64}") int maxBatchSize,
            @Value("${flashflow.order.write-batch-window:PT0.008S}") Duration batchWindow,
            MeterRegistry meterRegistry
    ) {
        this.transactionalService = transactionalService;
        this.stripeCount = Math.max(1, stripeCount);
        this.queueCapacity = Math.max(1, queueCapacity);
        this.maxBatchSize = Math.max(1, maxBatchSize);
        this.batchWindow = batchWindow.isNegative() ? Duration.ZERO : batchWindow;
        this.queues = new ArrayList<>(this.stripeCount);
        for (int stripe = 0; stripe < this.stripeCount; stripe++) {
            queues.add(new ArrayBlockingQueue<>(this.queueCapacity));
        }
        this.rejectedCounter = meterRegistry.counter("flashflow.order.write.rejected");
        this.splitCounter = meterRegistry.counter("flashflow.order.write.batch.split");
        this.batchSizeSummary = DistributionSummary.builder("flashflow.order.write.batch.size")
                .minimumExpectedValue(1.0)
                .maximumExpectedValue((double) this.maxBatchSize)
                .publishPercentileHistogram()
                .register(meterRegistry);
    }

    @PostConstruct
    void start() {
        running = true;
        for (int stripe = 0; stripe < stripeCount; stripe++) {
            int stripeIndex = stripe;
            Thread worker = Thread.ofPlatform()
                    .daemon(true)
                    .name("order-writer-" + stripeIndex)
                    .start(() -> runWorker(queues.get(stripeIndex)));
            workers.add(worker);
        }
    }

    @PreDestroy
    void stop() {
        running = false;
        workers.forEach(Thread::interrupt);
        BusinessException shutdown = new BusinessException(
                ErrorCode.INFRASTRUCTURE_UNAVAILABLE,
                "下单服务正在停止，请稍后重试");
        queues.forEach(queue -> {
            PendingOrder pending;
            while ((pending = queue.poll()) != null) {
                pending.result().completeExceptionally(shutdown);
            }
        });
    }

    public OrderEntity persist(OrderWriteCommand command) {
        PendingOrder pending = new PendingOrder(command, new CompletableFuture<>());
        ArrayBlockingQueue<PendingOrder> queue = queues.get(stripe(command.campaign().campaignId()));
        if (!queue.offer(pending)) {
            rejectedCounter.increment();
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "下单请求过多，请稍后重试");
        }
        try {
            return pending.result().join();
        } catch (CompletionException exception) {
            if (exception.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw exception;
        }
    }

    private int stripe(long campaignId) {
        return Math.floorMod(Long.hashCode(campaignId), stripeCount);
    }

    private void runWorker(ArrayBlockingQueue<PendingOrder> queue) {
        while (running) {
            try {
                PendingOrder first = queue.take();
                List<PendingOrder> pendingBatch = collectBatch(queue, first);
                if (!running) {
                    BusinessException shutdown = new BusinessException(
                            ErrorCode.INFRASTRUCTURE_UNAVAILABLE,
                            "下单服务正在停止，请稍后重试");
                    pendingBatch.forEach(pending -> pending.result().completeExceptionally(shutdown));
                    return;
                }
                persistGroups(pendingBatch);
            } catch (InterruptedException exception) {
                if (!running) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private List<PendingOrder> collectBatch(
            ArrayBlockingQueue<PendingOrder> queue,
            PendingOrder first
    ) {
        List<PendingOrder> batch = new ArrayList<>(maxBatchSize);
        batch.add(first);
        long deadline = System.nanoTime() + batchWindow.toNanos();
        while (batch.size() < maxBatchSize) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                break;
            }
            PendingOrder next;
            try {
                next = queue.poll(remaining, TimeUnit.NANOSECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            }
            if (next == null) {
                break;
            }
            batch.add(next);
        }
        queue.drainTo(batch, maxBatchSize - batch.size());
        return batch;
    }

    private void persistGroups(List<PendingOrder> pendingBatch) {
        Map<Long, List<PendingOrder>> groups = new LinkedHashMap<>();
        for (PendingOrder pending : pendingBatch) {
            groups.computeIfAbsent(
                    pending.command().campaign().campaignId(),
                    ignored -> new ArrayList<>())
                    .add(pending);
        }
        groups.values().forEach(group -> persistGroup(group, new SoldOutBound()));
    }

    private void persistGroup(List<PendingOrder> group, SoldOutBound bound) {
        int quantity = group.stream().mapToInt(pending -> pending.command().quantity()).sum();
        if (quantity >= bound.smallestRejected) {
            // This worker is the only writer for the campaign, so stock can only have shrunk
            // since MySQL rejected a smaller batch: skip the round trip.
            fail(group, new BusinessException(ErrorCode.SOLD_OUT));
            return;
        }
        List<OrderWriteCommand> commands = group.stream()
                .map(PendingOrder::command)
                .toList();
        try {
            List<OrderEntity> orders = transactionalService.persistReservedOrders(commands);
            batchSizeSummary.record(orders.size());
            for (int index = 0; index < group.size(); index++) {
                group.get(index).result().complete(orders.get(index));
            }
        } catch (BusinessException exception) {
            if (exception.errorCode() != ErrorCode.SOLD_OUT) {
                fail(group, exception);
                return;
            }
            bound.smallestRejected = quantity;
            if (group.size() == 1) {
                fail(group, exception);
                return;
            }
            // Redis admitted more than MySQL can fill, e.g. right after a cache re-warm or a
            // reconciliation repair. The combined decrement fails as a whole, so split the batch
            // and let the orders that still fit commit instead of rejecting all of them. With the
            // bound above this costs O(log n) extra transactions, not O(n).
            splitCounter.increment();
            int middle = group.size() / 2;
            persistGroup(group.subList(0, middle), bound);
            persistGroup(group.subList(middle, group.size()), bound);
        } catch (RuntimeException exception) {
            fail(group, exception);
        }
    }

    private static void fail(List<PendingOrder> group, RuntimeException exception) {
        group.forEach(pending -> pending.result().completeExceptionally(exception));
    }

    private static final class SoldOutBound {
        private int smallestRejected = Integer.MAX_VALUE;
    }

    private record PendingOrder(
            OrderWriteCommand command,
            CompletableFuture<OrderEntity> result
    ) {
    }
}
