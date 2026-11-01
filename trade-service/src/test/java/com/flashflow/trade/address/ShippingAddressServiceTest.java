package com.flashflow.trade.address;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ShippingAddressServiceTest {

    @Mock
    private ShippingAddressRepository repository;

    @Mock
    private ShippingAddressSnapshotReader snapshotReader;

    private ShippingAddressService service;

    @BeforeEach
    void setUp() {
        service = new ShippingAddressService(repository, snapshotReader);
    }

    @Test
    void firstAddressAutomaticallyBecomesDefault() {
        when(repository.existsByUserId(7L)).thenReturn(false);
        when(repository.save(any(ShippingAddressEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AddressDtos.AddressView created = service.create(7L, request(false));

        assertThat(created.defaultAddress()).isTrue();
        ArgumentCaptor<ShippingAddressEntity> captor =
                ArgumentCaptor.forClass(ShippingAddressEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().isDefaultAddress()).isTrue();
    }

    private static AddressDtos.SaveAddressRequest request(boolean defaultAddress) {
        return new AddressDtos.SaveAddressRequest(
                "Test User",
                "13800000000",
                "Shanghai",
                "Shanghai",
                "Pudong",
                "Test Road 1",
                "201210",
                defaultAddress);
    }
}
