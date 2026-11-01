package com.flashflow.trade.inventory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import com.flashflow.trade.campaign.CampaignEntity;
import com.flashflow.trade.campaign.CampaignRepository;
import com.flashflow.trade.campaign.CampaignStatus;
import com.flashflow.trade.campaign.SeckillRedisRepository;
import com.flashflow.trade.inventory.InventoryDtos.CampaignStockView;
import com.flashflow.trade.inventory.InventoryDtos.ReconciliationReport;
import com.flashflow.trade.inventory.InventoryDtos.StockState;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Compares the Redis stock cache with MySQL, the source of truth, and repairs drift that
 * compensation could not close (for example when the compensation task itself was lost).
 *
 * <p>Redis is legitimately lower than MySQL while a reservation is in flight, so a mismatch is
 * only repaired after the same mismatch has been seen with an unchanged campaign row version
 * and an unchanged Redis value for a whole quiet window. The repair itself is a Lua
 * compare-and-set, so a reservation that sneaks in between the read and the write wins.
 */
@Service
public class StockReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(StockReconciliationService.class);

    private final CampaignRepository campaignRepository;
    private final StockCompensationRepository compensationRepository;
    private final StockReconciliationLogRepository logRepository;
    private final SeckillRedisRepository redisRepository;
    private final Duration quietWindow;
    private final Map<Long, Observation> observations = new ConcurrentHashMap<>();
    private final Counter repairedCounter;
    private final AtomicLong driftingCampaigns = new AtomicLong();
    private final AtomicLong missingKeys = new AtomicLong();

    public StockReconciliationService(
            CampaignRepository campaignRepository,
            StockCompensationRepository compensationRepository,
            StockReconciliationLogRepository logRepository,
            SeckillRedisRepository redisRepository,
            @Value("${flashflow.stock-reconciliation.quiet-window:PT30S}") Duration quietWindow,
            MeterRegistry meterRegistry
    ) {
        this.campaignRepository = campaignRepository;
        this.compensationRepository = compensationRepository;
        this.logRepository = logRepository;
        this.redisRepository = redisRepository;
        this.quietWindow = quietWindow;
        this.repairedCounter = meterRegistry.counter("flashflow.stock.reconciliation.repaired");
        Gauge.builder("flashflow.stock.reconciliation.drifting", driftingCampaigns, AtomicLong::get)
                .description("Active campaigns whose Redis stock differs from MySQL")
                .register(meterRegistry);
        Gauge.builder("flashflow.stock.reconciliation.missing", missingKeys, AtomicLong::get)
                .description("Active campaigns without a Redis stock key")
                .register(meterRegistry);
    }

    /** Read-only report for the admin console; never changes Redis. */
    public ReconciliationReport inspect() {
        return scan(false, StockReconciliationLog.TriggerSource.MANUAL);
    }

    public ReconciliationReport reconcile(StockReconciliationLog.TriggerSource triggerSource) {
        return scan(true, triggerSource);
    }

    private ReconciliationReport scan(boolean repair, StockReconciliationLog.TriggerSource triggerSource) {
        Instant now = Instant.now();
        List<CampaignEntity> campaigns = campaignRepository
                .findAllByStatusAndEndsAtAfterOrderByStartsAtAsc(CampaignStatus.ACTIVE, now);
        Map<Long, Long> pending = pendingCompensations(campaigns);
        List<CampaignStockView> items = new ArrayList<>(campaigns.size());
        for (CampaignEntity campaign : campaigns) {
            items.add(check(campaign, pending.getOrDefault(campaign.getId(), 0L), now, repair, triggerSource));
        }
        if (repair) {
            Set<Long> activeIds = campaigns.stream().map(CampaignEntity::getId).collect(Collectors.toSet());
            observations.keySet().retainAll(activeIds);
            driftingCampaigns.set(items.stream()
                    .filter(item -> item.state() != StockState.CONSISTENT
                            && item.state() != StockState.REPAIRED)
                    .count());
            missingKeys.set(items.stream()
                    .filter(item -> item.state() == StockState.MISSING_KEY)
                    .count());
        }
        return ReconciliationReport.of(now, quietWindow, items);
    }

    private CampaignStockView check(
            CampaignEntity campaign,
            long pendingCompensations,
            Instant now,
            boolean repair,
            StockReconciliationLog.TriggerSource triggerSource
    ) {
        long campaignId = campaign.getId();
        int databaseStock = campaign.getRemainingStock();
        OptionalInt cached = redisRepository.readStock(campaignId);
        if (cached.isEmpty()) {
            forget(repair, campaignId);
            return view(campaign, null, pendingCompensations, StockState.MISSING_KEY);
        }
        int redisStock = cached.getAsInt();
        if (redisStock == databaseStock) {
            forget(repair, campaignId);
            return view(campaign, redisStock, pendingCompensations, StockState.CONSISTENT);
        }
        if (pendingCompensations > 0) {
            forget(repair, campaignId);
            return view(campaign, redisStock, pendingCompensations, StockState.PENDING_COMPENSATION);
        }
        if (!repair) {
            return view(campaign, redisStock, pendingCompensations, StockState.DRIFT_OBSERVED);
        }

        Observation current = new Observation(campaign.getVersion(), redisStock, now);
        Observation previous = observations.get(campaignId);
        if (previous == null || !previous.sameStateAs(current)) {
            observations.put(campaignId, current);
            return view(campaign, redisStock, pendingCompensations, StockState.DRIFT_OBSERVED);
        }
        if (Duration.between(previous.observedAt(), now).compareTo(quietWindow) < 0) {
            return view(campaign, redisStock, pendingCompensations, StockState.DRIFT_OBSERVED);
        }

        observations.remove(campaignId);
        return switch (redisRepository.repairStock(campaignId, redisStock, databaseStock)) {
            case REPAIRED -> {
                logRepository.save(new StockReconciliationLog(
                        campaignId, databaseStock, redisStock, triggerSource, now));
                repairedCounter.increment();
                log.warn("Redis stock repaired, campaignId={}, redis={}, mysql={}, source={}",
                        campaignId, redisStock, databaseStock, triggerSource);
                yield view(campaign, databaseStock, pendingCompensations, StockState.REPAIRED);
            }
            case CONFLICT -> view(campaign, redisStock, pendingCompensations, StockState.CAS_CONFLICT);
            case MISSING -> view(campaign, null, pendingCompensations, StockState.MISSING_KEY);
        };
    }

    private void forget(boolean repair, long campaignId) {
        if (repair) {
            observations.remove(campaignId);
        }
    }

    private Map<Long, Long> pendingCompensations(List<CampaignEntity> campaigns) {
        if (campaigns.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = campaigns.stream().map(CampaignEntity::getId).toList();
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : compensationRepository.countByCampaignIds(StockCompensationStatus.PENDING, ids)) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }

    private static CampaignStockView view(
            CampaignEntity campaign,
            Integer redisStock,
            long pendingCompensations,
            StockState state
    ) {
        return new CampaignStockView(
                campaign.getId(),
                campaign.getTotalStock(),
                campaign.getRemainingStock(),
                redisStock,
                redisStock == null ? null : redisStock - campaign.getRemainingStock(),
                pendingCompensations,
                state);
    }

    private record Observation(long campaignVersion, int redisStock, Instant observedAt) {

        boolean sameStateAs(Observation other) {
            return campaignVersion == other.campaignVersion && redisStock == other.redisStock;
        }
    }
}
