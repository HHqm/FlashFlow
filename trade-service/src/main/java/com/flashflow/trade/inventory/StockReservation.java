package com.flashflow.trade.inventory;

/**
 * Identifies one Redis reservation made by {@code seckill_reserve.lua}.
 */
public record StockReservation(
        long campaignId,
        long userId,
        String idempotencyKey,
        String orderNo,
        int quantity
) {
}
