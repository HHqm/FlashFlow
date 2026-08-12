package com.flashflow.trade.address;

public record ShippingAddressSnapshot(
        long addressId,
        String recipientName,
        String phone,
        String fullAddress
) {
    public static ShippingAddressSnapshot from(ShippingAddressEntity address) {
        return new ShippingAddressSnapshot(
                address.getId(),
                address.getRecipientName(),
                address.getPhone(),
                address.fullAddress());
    }
}
