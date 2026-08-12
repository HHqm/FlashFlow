package com.flashflow.trade.order;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

import com.flashflow.trade.campaign.CampaignEntity;
import com.flashflow.trade.common.BusinessException;
import com.flashflow.trade.common.ErrorCode;
import com.flashflow.trade.product.ProductEntity;
import com.flashflow.trade.product.ProductRepository;
import com.flashflow.trade.product.ProductVariantEntity;
import com.flashflow.trade.product.ProductVariantRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class OrderProductSnapshotService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final Duration cacheTtl;
    private final ConcurrentMap<Long, CacheEntry> cache = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, ReentrantLock> loadLocks = new ConcurrentHashMap<>();
    private final Counter hitCounter;
    private final Counter missCounter;

    public OrderProductSnapshotService(
            ProductRepository productRepository,
            ProductVariantRepository variantRepository,
            @Value("${flashflow.order.snapshot-cache-ttl:PT5M}") Duration cacheTtl,
            MeterRegistry meterRegistry
    ) {
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.cacheTtl = cacheTtl;
        this.hitCounter = meterRegistry.counter("flashflow.order.snapshot.cache.hit");
        this.missCounter = meterRegistry.counter("flashflow.order.snapshot.cache.miss");
    }

    public OrderProductSnapshot resolve(CampaignEntity campaign) {
        Instant now = Instant.now();
        CacheEntry existing = cache.get(campaign.getId());
        if (existing != null && existing.expiresAt().isAfter(now)) {
            hitCounter.increment();
            return existing.snapshot();
        }

        ReentrantLock loadLock = loadLocks.computeIfAbsent(
                campaign.getId(),
                ignored -> new ReentrantLock());
        loadLock.lock();
        try {
            Instant loadTime = Instant.now();
            CacheEntry current = cache.get(campaign.getId());
            if (current != null && current.expiresAt().isAfter(loadTime)) {
                hitCounter.increment();
                return current.snapshot();
            }
            missCounter.increment();
            OrderProductSnapshot snapshot = loadSnapshot(campaign);
            cache.put(campaign.getId(), new CacheEntry(snapshot, loadTime.plus(cacheTtl)));
            return snapshot;
        } finally {
            loadLock.unlock();
        }
    }

    public void evict(long campaignId) {
        cache.remove(campaignId);
    }

    private OrderProductSnapshot loadSnapshot(CampaignEntity campaign) {
        ProductEntity product = productRepository.findById(campaign.getProductId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        ProductVariantEntity variant = campaign.getVariantId() == null
                ? variantRepository.findAllByProductIdOrderByIdAsc(product.getId()).stream()
                        .findFirst()
                        .orElseThrow(() -> new BusinessException(ErrorCode.VARIANT_NOT_FOUND))
                : variantRepository.findById(campaign.getVariantId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.VARIANT_NOT_FOUND));
        return new OrderProductSnapshot(
                product.getId(),
                variant.getId(),
                product.getName(),
                variant.getColorName() + " · " + variant.getSpecification());
    }

    private record CacheEntry(OrderProductSnapshot snapshot, Instant expiresAt) {
    }
}
