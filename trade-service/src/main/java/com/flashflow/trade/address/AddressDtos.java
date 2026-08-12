package com.flashflow.trade.address;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AddressDtos {

    private AddressDtos() {
    }

    public record SaveAddressRequest(
            @NotBlank @Size(max = 80) String recipientName,
            @NotBlank @Pattern(regexp = "^[0-9+() -]{6,32}$") String phone,
            @NotBlank @Size(max = 80) String province,
            @NotBlank @Size(max = 80) String city,
            @NotBlank @Size(max = 80) String district,
            @NotBlank @Size(max = 240) String detailAddress,
            @Size(max = 20) String postalCode,
            boolean defaultAddress
    ) {
    }

    public record AddressView(
            Long id,
            String recipientName,
            String phone,
            String province,
            String city,
            String district,
            String detailAddress,
            String postalCode,
            boolean defaultAddress
    ) {
        public static AddressView from(ShippingAddressEntity address) {
            return new AddressView(
                    address.getId(),
                    address.getRecipientName(),
                    address.getPhone(),
                    address.getProvince(),
                    address.getCity(),
                    address.getDistrict(),
                    address.getDetailAddress(),
                    address.getPostalCode(),
                    address.isDefaultAddress());
        }
    }
}
