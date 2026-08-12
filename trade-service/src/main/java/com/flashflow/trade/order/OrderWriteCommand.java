package com.flashflow.trade.order;

import com.flashflow.trade.address.ShippingAddressSnapshot;

record OrderWriteCommand(
        long userId,
        OrderCampaignContext campaign,
        String idempotencyKey,
        String orderNo,
        int quantity,
        OrderProductSnapshot productSnapshot,
        ShippingAddressSnapshot shippingAddress
) {
}
