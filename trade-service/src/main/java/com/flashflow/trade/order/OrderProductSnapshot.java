package com.flashflow.trade.order;

public record OrderProductSnapshot(
        long productId,
        long variantId,
        String productName,
        String variantLabel
) {
}
