package com.flashflow.trade.campaign;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class CampaignCacheInitializer {

    private static final Logger log = LoggerFactory.getLogger(CampaignCacheInitializer.class);
    private static final Duration MINIMUM_TTL = Duration.ofMinutes(5);

    private final CampaignRepository campaignRepository;
    private final StringRedisTemplate redisTemplate;

    public CampaignCacheInitializer(
            CampaignRepository campaignRepository,
            StringRedisTemplate redisTemplate
    ) {
        this.campaignRepository = campaignRepository;
        this.redisTemplate = redisTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCampaignCreated(CampaignCreatedEvent event) {
        campaignRepository.findById(event.campaignId()).ifPresent(campaign -> warm(campaign, true));
    }

    public void warm(CampaignEntity campaign, boolean force) {
        String key = stockKey(campaign.getId());
        Duration ttl = Duration.between(Instant.now(), campaign.getEndsAt())
                .plus(Duration.ofDays(1));
        if (ttl.isNegative() || ttl.isZero()) {
            ttl = MINIMUM_TTL;
        }
        if (force) {
            redisTemplate.opsForValue().set(key, Integer.toString(campaign.getRemainingStock()), ttl);
        } else {
            redisTemplate.opsForValue().setIfAbsent(
                    key,
                    Integer.toString(campaign.getRemainingStock()),
                    ttl);
        }
        log.info("Campaign stock cached, campaignId={}, force={}", campaign.getId(), force);
    }

    public static String stockKey(long campaignId) {
        return "flashflow:campaign:" + campaignId + ":stock";
    }
}
