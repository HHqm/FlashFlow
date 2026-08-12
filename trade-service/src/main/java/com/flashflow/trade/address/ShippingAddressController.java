package com.flashflow.trade.address;

import java.util.List;

import com.flashflow.trade.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/addresses")
@PreAuthorize("hasAuthority('ROLE_USER')")
public class ShippingAddressController {

    private final ShippingAddressService service;

    public ShippingAddressController(ShippingAddressService service) {
        this.service = service;
    }

    @GetMapping
    ApiResponse<List<AddressDtos.AddressView>> list(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(service.list(userId(jwt)));
    }

    @PostMapping
    ApiResponse<AddressDtos.AddressView> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AddressDtos.SaveAddressRequest request
    ) {
        return ApiResponse.success(service.create(userId(jwt), request));
    }

    @PutMapping("/{addressId}")
    ApiResponse<AddressDtos.AddressView> update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long addressId,
            @Valid @RequestBody AddressDtos.SaveAddressRequest request
    ) {
        return ApiResponse.success(service.update(userId(jwt), addressId, request));
    }

    @PatchMapping("/{addressId}/default")
    ApiResponse<AddressDtos.AddressView> makeDefault(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long addressId
    ) {
        return ApiResponse.success(service.makeDefault(userId(jwt), addressId));
    }

    @DeleteMapping("/{addressId}")
    ApiResponse<Void> delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long addressId
    ) {
        service.delete(userId(jwt), addressId);
        return ApiResponse.success(null);
    }

    private static long userId(Jwt jwt) {
        return ((Number) jwt.getClaim("uid")).longValue();
    }
}
