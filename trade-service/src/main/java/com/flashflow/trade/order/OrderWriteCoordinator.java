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
    private final Counter bucketFallbackCounter;
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
        this.bucketFallbackCounter = meterRegistry.counter("flashflow.order.write.bucket.fallback");
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
        ArrayBlockingQueue<PendingOrder> queue = queues.get(stripe(command));
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

    // Buckets of one campaign map to consecutive stripes, so with stripes >= buckets every bucket
    // of a hot campaign has its own writer thread and commits in parallel.
    private int stripe(OrderWriteCommand command) {
        int bucket = command.campaign().bucketFor(command.orderNo());
        return Math.floorMod(Long.hashCode(command.campaign().campaignId()) + bucket, stripeCount);
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
        Map<BucketKey, List<PendingOrder>> groups = new LinkedHashMap<>();
        for (PendingOrder pending : pendingBatch) {
            OrderCampaignContext campaign = pending.command().campaign();
            groups.computeIfAbsent(
                    new BucketKey(campaign.campaignId(), campaign.bucketFor(pending.command().orderNo())),
                    ignored -> new ArrayList<>())
                    .add(pending);
        }
        groups.forEach((key, group) -> persistGroup(group, key.bucket()));
    }

    /**
     * Commits the group into its own bucket and moves whatever does not fit on to the next
     * bucket. Redis never admits more than the campaign's total stock, so an order only ends up
     * SOLD_OUT here when every bucket is exhausted: one bucket selling out early does not turn
     * buyers away while another still has stock.
     */
    private void persistGroup(List<PendingOrder> group, int ownBucket) {
        int buckets = group.getFirst().command().campaign().stockBuckets();
        List<PendingOrder> remaining = group;
        for (int offset = 0; offset < buckets && !remaining.isEmpty(); offset++) {
            if (offset > 0) {
                bucketFallbackCounter.increment(remaining.size());
            }
            remaining = persistIntoBucket(remaining, (ownBucket + offset) % buckets, new SoldOutBound());
        }
        fail(remaining, new BusinessException(ErrorCode.SOLD_OUT));
    }

    /** Returns the orders that did not fit into this bucket; every other order is completed. */
    private List<PendingOrder> persistIntoBucket(List<PendingOrder> group, int bucket, SoldOutBound bound) {
        int quantity = group.stream().mapToInt(pending -> pending.command().quantity()).sum();
        if (quantity >= bound.smallestRejected) {
            // Bucket stock only shrinks while we work on it, so a batch at least as large as one
            // MySQL just rejected cannot fit either: skip the round trip.
            return group;
        }
        List<OrderWriteCommand> commands = group.stream()
                .map(PendingOrder::command)
                .toList();
        try {
            List<OrderEntity> orders = transactionalService.persistReservedOrders(commands, bucket);
            batchSizeSummary.record(orders.size());
            for (int index = 0; index < group.size(); index++) {
                group.get(index).result().complete(orders.get(index));
            }
            return List.of();
        } catch (BusinessException exception) {
            if (exception.errorCode() != ErrorCode.SOLD_OUT) {
                fail(group, exception);
                return List.of();
            }
            bound.smallestRejected = quantity;
            if (group.size() == 1) {
                return group;
            }
            // The combined decrement fails as a whole, so split the batch and let the orders that
            // still fit commit. With the bound above this costs O(log n) extra transactions.
            splitCounter.increment();
            int middle = group.size() / 2;
            List<PendingOrder> leftOver = new ArrayList<>(
                    persistIntoBucket(group.subList(0, middle), bucket, bound));
            leftOver.addAll(persistIntoBucket(group.subList(middle, group.size()), bucket, bound));
            return leftOver;
        } catch (RuntimeException exception) {
            fail(group, exception);
            return List.of();
        }
    }

    private static void fail(List<PendingOrder> group, RuntimeException exception) {
        group.forEach(pending -> pending.result().completeExceptionally(exception));
    }

    private record BucketKey(long campaignId, int bucket) {
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
