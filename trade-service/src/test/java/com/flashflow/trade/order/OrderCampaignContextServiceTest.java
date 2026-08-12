package com.flashflow.trade.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.flashflow.trade.campaign.CampaignEntity;
import com.flashflow.trade.campaign.CampaignRepository;
import com.flashflow.trade.campaign.CampaignStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderCampaignContextServiceTest {

    @Mock
    private CampaignRepository campaignRepository;

    @Mock
    private OrderProductSnapshotService productSnapshotService;

    @Mock
    private CampaignEntity campaign;

    @Test
    void coalescesConcurrentCacheMissesWithoutDuplicatingDatabaseLoads() throws Exception {
        long campaignId = 42L;
        OrderProductSnapshot snapshot = new OrderProductSnapshot(3L, 4L, "商品", "黑色 · 标准版");
        Instant now = Instant.now();
        when(campaignRepository.findById(campaignId)).thenAnswer(invocation -> {
            Thread.sleep(50);
            return Optional.of(campaign);
        });
        when(campaign.getId()).thenReturn(campaignId);
        when(campaign.getFlashPrice()).thenReturn(new BigDecimal("99.00"));
        when(campaign.getPurchaseLimit()).thenReturn(2);
        when(campaign.getStartsAt()).thenReturn(now.minusSeconds(60));
        when(campaign.getEndsAt()).thenReturn(now.plusSeconds(3600));
        when(campaign.getStatus()).thenReturn(CampaignStatus.ACTIVE);
        when(productSnapshotService.resolve(campaign)).thenReturn(snapshot);

        var service = new OrderCampaignContextService(
                campaignRepository,
                productSnapshotService,
                Duration.ofMinutes(1),
                new SimpleMeterRegistry());
        var ready = new CountDownLatch(100);
        var start = new CountDownLatch(1);
        var futures = new ArrayList<Future<OrderCampaignContext>>();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int index = 0; index < 100; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return service.resolve(campaignId);
                }));
            }
            ready.await();
            start.countDown();
            for (Future<OrderCampaignContext> future : futures) {
                assertThat(future.get()).isSameAs(futures.getFirst().get());
            }
        }

        verify(campaignRepository, times(1)).findById(campaignId);
        verify(productSnapshotService, times(1)).resolve(campaign);
    }
}
