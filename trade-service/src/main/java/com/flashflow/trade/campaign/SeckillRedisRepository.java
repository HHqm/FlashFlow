package com.flashflow.trade.campaign;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.OptionalInt;

import com.flashflow.trade.common.BusinessException;
import com.flashflow.trade.common.ErrorCode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class SeckillRedisRepository {

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<String> reserveScript;
    private final DefaultRedisScript<Long> compensateScript;
    private final DefaultRedisScript<Long> stockReconcileScript;
    private final int requestsPerSecond;
    private final Counter reservedCounter;
    private final Counter rejectedCounter;

    public SeckillRedisRepository(
            StringRedisTemplate redisTemplate,
            DefaultRedisScript<String> seckillReserveScript,
            DefaultRedisScript<Long> seckillCompensateScript,
            DefaultRedisScript<Long> stockReconcileScript,
            @Value("${flashflow.seckill.requests-per-second:5}") int requestsPerSecond,
            MeterRegistry meterRegistry
    ) {
        this.redisTemplate = redisTemplate;
        this.reserveScript = seckillReserveScript;
        this.compensateScript = seckillCompensateScript;
        this.stockReconcileScript = stockReconcileScript;
        this.requestsPerSecond = requestsPerSecond;
        this.reservedCounter = meterRegistry.counter("flashflow.seckill.reserved");
        this.rejectedCounter = meterRegistry.counter("flashflow.seckill.rejected");
    }

    public ReservationResult reserve(
            long campaignId,
            long userId,
            String idempotencyKey,
            String orderNo,
            Instant campaignEndsAt
    ) {
        return reserve(campaignId, userId, idempotencyKey, orderNo, campaignEndsAt, 1, 1);
    }

    public ReservationResult reserve(
            long campaignId,
            long userId,
            String idempotencyKey,
            String orderNo,
            Instant campaignEndsAt,
            int quantity,
            int purchaseLimit
    ) {
        String purchaserKey = purchaserKey(campaignId, userId);
        String requestKey = requestKey(userId, idempotencyKey);
        long ttlSeconds = Math.max(
                Duration.between(Instant.now(), campaignEndsAt).plus(Duration.ofDays(1)).toSeconds(),
                Duration.ofMinutes(5).toSeconds());
        try {
            String result = redisTemplate.execute(
                    reserveScript,
                    List.of(
                            CampaignCacheInitializer.stockKey(campaignId),
                            purchaserKey,
                            requestKey,
                            rateKey(userId)),
                    orderNo,
                    Long.toString(ttlSeconds),
                    Integer.toString(quantity),
                    Integer.toString(purchaseLimit),
                    Integer.toString(requestsPerSecond));
            if (result == null) {
                throw new BusinessException(ErrorCode.INFRASTRUCTURE_UNAVAILABLE);
            }
            if (result.startsWith("RESERVED|")) {
                reservedCounter.increment();
                return ReservationResult.reserved(result.substring("RESERVED|".length()));
            }
            if (result.startsWith("DUPLICATE|")) {
                return ReservationResult.duplicate(result.substring("DUPLICATE|".length()));
            }
            rejectedCounter.increment();
            return switch (result) {
                case "RATE_LIMITED" -> throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
                case "SOLD_OUT" -> new ReservationResult(ReservationResult.Status.SOLD_OUT, null);
                case "ALREADY_PURCHASED" ->
                        new ReservationResult(ReservationResult.Status.ALREADY_PURCHASED, null);
                case "LIMIT_EXCEEDED" ->
                        new ReservationResult(ReservationResult.Status.LIMIT_EXCEEDED, null);
                default -> throw new BusinessException(
                        ErrorCode.INFRASTRUCTURE_UNAVAILABLE,
                        "Redis 返回未知预占结果");
            };
        } catch (BusinessException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            throw new BusinessException(ErrorCode.INFRASTRUCTURE_UNAVAILABLE, "Redis 暂时不可用");
        }
    }

    public boolean compensate(long campaignId, long userId, String idempotencyKey, String orderNo) {
        return compensate(campaignId, userId, idempotencyKey, orderNo, 1);
    }

    /**
     * Returns {@code true} when the reservation was found and returned, {@code false} when it had
     * already been returned or expired. Safe to call repeatedly for the same order.
     */
    public boolean compensate(
            long campaignId,
            long userId,
            String idempotencyKey,
            String orderNo,
            int quantity
    ) {
        Long restored = redisTemplate.execute(
                compensateScript,
                List.of(
                        CampaignCacheInitializer.stockKey(campaignId),
                        purchaserKey(campaignId, userId),
                        requestKey(userId, idempotencyKey)),
                orderNo,
                Integer.toString(quantity));
        return restored != null && restored == 1L;
    }

    public OptionalInt readStock(long campaignId) {
        String value = redisTemplate.opsForValue().get(CampaignCacheInitializer.stockKey(campaignId));
        return value == null ? OptionalInt.empty() : OptionalInt.of(Integer.parseInt(value));
    }

    public StockRepairResult repairStock(long campaignId, int expectedStock, int targetStock) {
        Long result = redisTemplate.execute(
                stockReconcileScript,
                List.of(CampaignCacheInitializer.stockKey(campaignId)),
                Integer.toString(expectedStock),
                Integer.toString(targetStock));
        if (result == null || result == -1L) {
            return StockRepairResult.MISSING;
        }
        return result == 1L ? StockRepairResult.REPAIRED : StockRepairResult.CONFLICT;
    }

    public enum StockRepairResult {
        REPAIRED,
        CONFLICT,
        MISSING
    }

    private static String purchaserKey(long campaignId, long userId) {
        return "flashflow:campaign:" + campaignId + ":buyer:" + userId;
    }

    private static String rateKey(long userId) {
        return "flashflow:rate:seckill:" + userId + ":" + Instant.now().getEpochSecond();
    }

    private static String requestKey(long userId, String idempotencyKey) {
        return "flashflow:idempotency:" + userId + ":" + idempotencyKey;
    }
}
