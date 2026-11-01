package com.flashflow.trade.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import com.flashflow.trade.campaign.CampaignCacheInitializer;
import com.flashflow.trade.inventory.InventoryDtos.CampaignStockView;
import com.flashflow.trade.inventory.InventoryDtos.ReconciliationReport;
import com.flashflow.trade.inventory.InventoryDtos.StockState;
import com.flashflow.trade.inventory.StockCompensationReason;
import com.flashflow.trade.inventory.StockCompensationRepository;
import com.flashflow.trade.inventory.StockCompensationService;
import com.flashflow.trade.inventory.StockCompensationStatus;
import com.flashflow.trade.inventory.StockCompensationTask;
import com.flashflow.trade.inventory.StockReconciliationLog;
import com.flashflow.trade.inventory.StockReconciliationLogRepository;
import com.flashflow.trade.inventory.StockReconciliationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class StockSelfHealingIT extends MiddlewareIntegrationTest {

    @Autowired
    StockCompensationService compensationService;

    @Autowired
    StockCompensationRepository compensationRepository;

    @Autowired
    StockReconciliationService reconciliationService;

    @Autowired
    StockReconciliationLogRepository reconciliationLogRepository;

    @Test
    void redisOutageDuringCancellationIsHealedByDurableCompensationTask() {
        long campaignId = createCampaign(5, 1);
        Buyer buyer = createBuyers(1).getFirst();
        ApiResult order = placeOrder(buyer, campaignId, newIdempotencyKey());
        assertThat(order.status()).isEqualTo(200);
        assertThat(redisStock(campaignId)).isEqualTo(4);

        // Fault injection: freeze Redis so the after-commit compensation times out.
        pause(REDIS);
        ApiResult cancelled;
        try {
            cancelled = cancelOrder(buyer, order.orderNo());
        } finally {
            unpause(REDIS);
        }

        // MySQL is the source of truth and is already right even though Redis was unreachable.
        assertThat(cancelled.status()).isEqualTo(200);
        assertThat(databaseStock(campaignId)).isEqualTo(5);
        StockCompensationTask task = compensationRepository.findByOrderNo(order.orderNo()).orElseThrow();
        assertThat(task.getReason()).isEqualTo(StockCompensationReason.CUSTOMER_CANCELLED);
        assertThat(task.getStatus()).isEqualTo(StockCompensationStatus.PENDING);
        assertThat(task.getRetryCount()).isEqualTo(1);
        assertThat(stateOf(reconciliationService.inspect(), campaignId))
                .as("reconciliation must leave the gap to the compensation task")
                .isIn(StockState.PENDING_COMPENSATION, StockState.CONSISTENT);

        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(500)).untilAsserted(() -> {
            compensationService.retryDueTasks(100);
            assertThat(compensationRepository.findByOrderNo(order.orderNo()).orElseThrow().getStatus())
                    .isEqualTo(StockCompensationStatus.DONE);
        });

        // A client-side timeout does not prove Redis skipped the command: the frozen script may run
        // once the container resumes, and the retry then becomes a NOOP. Either way the order-number
        // guard in the Lua script returns the stock exactly once.
        assertThat(redisStock(campaignId)).isEqualTo(5);
        assertThat(compensationRepository.findByOrderNo(order.orderNo()).orElseThrow().getOutcome())
                .isIn(StockCompensationTask.Outcome.RESTORED, StockCompensationTask.Outcome.NOOP);
        assertThat(stateOf(reconciliationService.inspect(), campaignId)).isEqualTo(StockState.CONSISTENT);
    }

    @Test
    void reconciliationRepairsLeakedReservationsOnlyAfterTheDriftIsStable() {
        long campaignId = createCampaign(20, 1);
        for (Buyer buyer : createBuyers(2)) {
            assertThat(placeOrder(buyer, campaignId, newIdempotencyKey()).status()).isEqualTo(200);
        }
        // Three reservations whose compensation was lost entirely, e.g. the instance was killed.
        redisTemplate.opsForValue().decrement(CampaignCacheInitializer.stockKey(campaignId), 3);
        assertThat(redisStock(campaignId)).isEqualTo(15);

        assertThat(stateOf(reconciliationService.inspect(), campaignId)).isEqualTo(StockState.DRIFT_OBSERVED);
        assertThat(stateOf(reconcile(), campaignId)).isEqualTo(StockState.DRIFT_OBSERVED);
        assertThat(redisStock(campaignId)).as("a single observation must never repair").isEqualTo(15);

        CampaignStockView repaired = itemOf(reconcile(), campaignId);

        assertThat(repaired.state()).isEqualTo(StockState.REPAIRED);
        assertThat(redisStock(campaignId)).isEqualTo(18);
        List<StockReconciliationLog> logs =
                reconciliationLogRepository.findAllByCampaignIdOrderByCreatedAtDesc(campaignId);
        assertThat(logs).singleElement().satisfies(log -> {
            assertThat(log.getDrift()).isEqualTo(-3);
            assertThat(log.getRedisStockBefore()).isEqualTo(15);
            assertThat(log.getRedisStockAfter()).isEqualTo(18);
        });

        // The repaired cache keeps selling and stays in lockstep with MySQL.
        assertThat(placeOrder(createBuyers(1).getFirst(), campaignId, newIdempotencyKey()).status())
                .isEqualTo(200);
        assertThat(redisStock(campaignId)).isEqualTo(17);
        assertThat(databaseStock(campaignId)).isEqualTo(17);
    }

    @Test
    void aggressiveReconciliationDuringTrafficStillNeverOversells() throws Exception {
        int stock = 40;
        long campaignId = createCampaign(stock, 1);
        List<Buyer> buyers = createBuyers(150);
        AtomicBoolean trafficDone = new AtomicBoolean();

        List<ApiResult> results;
        // The quiet window is zero in tests, the worst case: the reconciler may "repair" Redis while
        // reservations are still in flight. MySQL's conditional update must still prevent oversell.
        try (ExecutorService background = Executors.newSingleThreadExecutor()) {
            Future<?> reconciler = background.submit(() -> {
                while (!trafficDone.get()) {
                    reconcile();
                }
            });
            try {
                results = runConcurrently(buyers, buyer -> placeOrder(buyer, campaignId, newIdempotencyKey()));
            } finally {
                trafficDone.set(true);
            }
            reconciler.get();
        }

        long successes = results.stream().filter(result -> result.status() == 200).count();
        assertThat(results).allSatisfy(result -> assertThat(result.status()).isIn(200, 409));
        assertThat(databaseStock(campaignId)).isZero();
        assertThat(countOrders(campaignId)).isEqualTo(successes);
        // A premature repair only ever over-admits in Redis; splitting oversized write batches
        // means the extra admissions do not cost real sales either: all stock is sold, none twice.
        assertThat(successes).isEqualTo(stock);

        // Once traffic stops, two scans are enough for the cache to converge on MySQL.
        reconcile();
        reconcile();
        assertThat(redisStock(campaignId)).isZero();
    }

    private ReconciliationReport reconcile() {
        return reconciliationService.reconcile(StockReconciliationLog.TriggerSource.MANUAL);
    }

    private static StockState stateOf(ReconciliationReport report, long campaignId) {
        return itemOf(report, campaignId).state();
    }

    private static CampaignStockView itemOf(ReconciliationReport report, long campaignId) {
        return report.items().stream()
                .filter(item -> item.campaignId() == campaignId)
                .findFirst()
                .orElseThrow();
    }
}
