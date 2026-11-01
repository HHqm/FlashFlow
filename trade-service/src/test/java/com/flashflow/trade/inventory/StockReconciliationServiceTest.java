package com.flashflow.trade.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.OptionalInt;

import com.flashflow.trade.campaign.CampaignEntity;
import com.flashflow.trade.campaign.CampaignRepository;
import com.flashflow.trade.campaign.CampaignStatus;
import com.flashflow.trade.campaign.SeckillRedisRepository;
import com.flashflow.trade.campaign.SeckillRedisRepository.StockRepairResult;
import com.flashflow.trade.inventory.InventoryDtos.ReconciliationReport;
import com.flashflow.trade.inventory.InventoryDtos.StockState;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StockReconciliationServiceTest {

    private static final long CAMPAIGN_ID = 10L;

    @Mock
    private CampaignRepository campaignRepository;

    @Mock
    private StockCompensationRepository compensationRepository;

    @Mock
    private StockReconciliationLogRepository logRepository;

    @Mock
    private SeckillRedisRepository redisRepository;

    private SimpleMeterRegistry meterRegistry;
    private StockReconciliationService service;
    private CampaignEntity campaign;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        service = new StockReconciliationService(
                campaignRepository,
                compensationRepository,
                logRepository,
                redisRepository,
                Duration.ZERO,
                meterRegistry);
        campaign = campaign(100, 40, 3L);
        when(campaignRepository.findAllByStatusAndEndsAtAfterOrderByStartsAtAsc(
                eq(CampaignStatus.ACTIVE), any(Instant.class)))
                .thenAnswer(invocation -> List.of(campaign));
        when(compensationRepository.countByCampaignIds(eq(StockCompensationStatus.PENDING), anyCollection()))
                .thenReturn(List.of());
    }

    @Test
    void consistentCacheIsLeftAlone() {
        when(redisRepository.readStock(CAMPAIGN_ID)).thenReturn(OptionalInt.of(40));

        ReconciliationReport report = service.reconcile(StockReconciliationLog.TriggerSource.SCHEDULED);

        assertThat(report.consistent()).isEqualTo(1);
        assertThat(report.items().getFirst().state()).isEqualTo(StockState.CONSISTENT);
        verify(redisRepository, never()).repairStock(anyLong(), anyInt(), anyInt());
    }

    @Test
    void leakedReservationIsRepairedOnlyAfterTheSameDriftIsSeenTwice() {
        when(redisRepository.readStock(CAMPAIGN_ID)).thenReturn(OptionalInt.of(37));
        when(redisRepository.repairStock(CAMPAIGN_ID, 37, 40)).thenReturn(StockRepairResult.REPAIRED);

        ReconciliationReport first = service.reconcile(StockReconciliationLog.TriggerSource.SCHEDULED);
        assertThat(first.items().getFirst().state()).isEqualTo(StockState.DRIFT_OBSERVED);
        assertThat(first.items().getFirst().drift()).isEqualTo(-3);
        verify(redisRepository, never()).repairStock(anyLong(), anyInt(), anyInt());

        ReconciliationReport second = service.reconcile(StockReconciliationLog.TriggerSource.SCHEDULED);

        assertThat(second.repaired()).isEqualTo(1);
        assertThat(second.items().getFirst().redisStock()).isEqualTo(40);
        ArgumentCaptor<StockReconciliationLog> logged = ArgumentCaptor.forClass(StockReconciliationLog.class);
        verify(logRepository).save(logged.capture());
        assertThat(logged.getValue().getDrift()).isEqualTo(-3);
        assertThat(logged.getValue().getRedisStockAfter()).isEqualTo(40);
        assertThat(meterRegistry.counter("flashflow.stock.reconciliation.repaired").count()).isEqualTo(1.0);
    }

    @Test
    void ongoingTrafficResetsTheQuietWindow() {
        when(redisRepository.readStock(CAMPAIGN_ID))
                .thenReturn(OptionalInt.of(37))
                .thenReturn(OptionalInt.of(36));

        service.reconcile(StockReconciliationLog.TriggerSource.SCHEDULED);
        campaign = campaign(100, 39, 4L);
        ReconciliationReport second = service.reconcile(StockReconciliationLog.TriggerSource.SCHEDULED);

        assertThat(second.items().getFirst().state()).isEqualTo(StockState.DRIFT_OBSERVED);
        verify(redisRepository, never()).repairStock(anyLong(), anyInt(), anyInt());
    }

    @Test
    void quietWindowMustElapseBeforeRepair() {
        service = new StockReconciliationService(
                campaignRepository,
                compensationRepository,
                logRepository,
                redisRepository,
                Duration.ofMinutes(5),
                meterRegistry);
        when(redisRepository.readStock(CAMPAIGN_ID)).thenReturn(OptionalInt.of(37));

        service.reconcile(StockReconciliationLog.TriggerSource.SCHEDULED);
        ReconciliationReport second = service.reconcile(StockReconciliationLog.TriggerSource.SCHEDULED);

        assertThat(second.items().getFirst().state()).isEqualTo(StockState.DRIFT_OBSERVED);
        verify(redisRepository, never()).repairStock(anyLong(), anyInt(), anyInt());
    }

    @Test
    void pendingCompensationTasksAreTrustedToCloseTheGap() {
        when(redisRepository.readStock(CAMPAIGN_ID)).thenReturn(OptionalInt.of(38));
        when(compensationRepository.countByCampaignIds(eq(StockCompensationStatus.PENDING), anyCollection()))
                .thenReturn(List.<Object[]>of(new Object[] {CAMPAIGN_ID, 2L}));

        service.reconcile(StockReconciliationLog.TriggerSource.SCHEDULED);
        ReconciliationReport second = service.reconcile(StockReconciliationLog.TriggerSource.SCHEDULED);

        assertThat(second.items().getFirst().state()).isEqualTo(StockState.PENDING_COMPENSATION);
        assertThat(second.items().getFirst().pendingCompensations()).isEqualTo(2);
        verify(redisRepository, never()).repairStock(anyLong(), anyInt(), anyInt());
    }

    @Test
    void missingKeyIsReportedButNeverRewarmedAutomatically() {
        when(redisRepository.readStock(CAMPAIGN_ID)).thenReturn(OptionalInt.empty());

        service.reconcile(StockReconciliationLog.TriggerSource.SCHEDULED);
        ReconciliationReport second = service.reconcile(StockReconciliationLog.TriggerSource.SCHEDULED);

        assertThat(second.items().getFirst().state()).isEqualTo(StockState.MISSING_KEY);
        assertThat(second.items().getFirst().redisStock()).isNull();
        verify(redisRepository, never()).repairStock(anyLong(), anyInt(), anyInt());
        assertThat(meterRegistry.get("flashflow.stock.reconciliation.missing").gauge().value()).isEqualTo(1.0);
    }

    @Test
    void casConflictMeansSomeoneElseMovedTheStockAndNothingIsWritten() {
        when(redisRepository.readStock(CAMPAIGN_ID)).thenReturn(OptionalInt.of(37));
        when(redisRepository.repairStock(CAMPAIGN_ID, 37, 40)).thenReturn(StockRepairResult.CONFLICT);

        service.reconcile(StockReconciliationLog.TriggerSource.SCHEDULED);
        ReconciliationReport second = service.reconcile(StockReconciliationLog.TriggerSource.SCHEDULED);

        assertThat(second.items().getFirst().state()).isEqualTo(StockState.CAS_CONFLICT);
        verify(logRepository, never()).save(any());
    }

    @Test
    void inspectionIsReadOnly() {
        when(redisRepository.readStock(CAMPAIGN_ID)).thenReturn(OptionalInt.of(37));

        service.inspect();
        service.inspect();
        ReconciliationReport report = service.inspect();

        assertThat(report.drifting()).isEqualTo(1);
        verify(redisRepository, never()).repairStock(anyLong(), anyInt(), anyInt());
    }

    private static CampaignEntity campaign(int totalStock, int remainingStock, long version) {
        CampaignEntity entity = new CampaignEntity(
                3L,
                4L,
                new BigDecimal("99.00"),
                totalStock,
                1,
                Instant.now().minus(1, ChronoUnit.HOURS),
                Instant.now().plus(1, ChronoUnit.HOURS));
        set(entity, "id", CAMPAIGN_ID);
        set(entity, "remainingStock", remainingStock);
        set(entity, "stockVersion", version);
        return entity;
    }

    private static void set(CampaignEntity entity, String name, Object value) {
        try {
            Field field = CampaignEntity.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(entity, value);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
