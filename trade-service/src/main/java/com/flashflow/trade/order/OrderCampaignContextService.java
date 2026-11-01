package com.flashflow.trade.order;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

import com.flashflow.trade.campaign.CampaignEntity;
import com.flashflow.trade.campaign.CampaignRepository;
import com.flashflow.trade.common.BusinessException;
import com.flashflow.trade.common.ErrorCode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class OrderCampaignContextService {

    private final CampaignRepository campaignRepository;
    private final OrderProductSnapshotService productSnapshotService;
    private final Duration cacheTtl;
    private final ConcurrentMap<Long, CacheEntry> cache = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, ReentrantLock> loadLocks = new ConcurrentHashMap<>();
    private final Counter hitCounter;
    private final Counter missCounter;

    public OrderCampaignContextService(
            CampaignRepository campaignRepository,
            OrderProductSnapshotService productSnapshotService,
            @Value("${flashflow.order.campaign-cache-ttl:PT1S}") Duration cacheTtl,
            MeterRegistry meterRegistry
    ) {
        this.campaignRepository = campaignRepository;
        this.productSnapshotService = productSnapshotService;
        this.cacheTtl = cacheTtl;
        this.hitCounter = meterRegistry.counter("flashflow.order.campaign.cache.hit");
        this.missCounter = meterRegistry.counter("flashflow.order.campaign.cache.miss");
    }

    public OrderCampaignContext resolve(long campaignId) {
        Instant now = Instant.now();
        CacheEntry existing = cache.get(campaignId);
        if (existing != null && existing.expiresAt().isAfter(now)) {
            hitCounter.increment();
            return existing.context();
        }

        ReentrantLock loadLock = loadLocks.computeIfAbsent(campaignId, ignored -> new ReentrantLock());
        loadLock.lock();
        try {
            Instant loadTime = Instant.now();
            CacheEntry current = cache.get(campaignId);
            if (current != null && current.expiresAt().isAfter(loadTime)) {
                hitCounter.increment();
                return current.context();
            }
            missCounter.increment();
            CampaignEntity campaign = campaignRepository.findById(campaignId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.CAMPAIGN_NOT_FOUND));
            OrderCampaignContext context = new OrderCampaignContext(
                    campaign.getId(),
                    campaign.getFlashPrice(),
                    campaign.getPurchaseLimit(),
                    campaign.getStartsAt(),
                    campaign.getEndsAt(),
                    campaign.getStatus(),
                    productSnapshotService.resolve(campaign),
                    campaign.getStockBuckets());
            cache.put(campaignId, new CacheEntry(context, loadTime.plus(cacheTtl)));
            return context;
        } finally {
            loadLock.unlock();
        }
    }

    public void evict(long campaignId) {
        cache.remove(campaignId);
        productSnapshotService.evict(campaignId);
    }

    private record CacheEntry(OrderCampaignContext context, Instant expiresAt) {
    }
}
