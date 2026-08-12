package com.flashflow.trade.address;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Optional;

import com.flashflow.trade.common.BusinessException;
import com.flashflow.trade.common.ErrorCode;
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

    private ShippingAddressService service;

    @BeforeEach
    void setUp() {
        service = new ShippingAddressService(repository);
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

    @Test
    void addressSnapshotIsCachedAndStillChecksOwnershipOnFirstRead() {
        ShippingAddressEntity address = new ShippingAddressEntity(7L, request(true));
        setId(address, 21L);
        when(repository.findByIdAndUserId(21L, 7L)).thenReturn(Optional.of(address));

        ShippingAddressSnapshot first = service.resolveSnapshot(7L, 21L);
        ShippingAddressSnapshot second = service.resolveSnapshot(7L, 21L);

        assertThat(first).isSameAs(second);
        assertThat(first.recipientName()).isEqualTo("Test User");
        verify(repository, times(1)).findByIdAndUserId(21L, 7L);
    }

    @Test
    void anotherUsersAddressIsNotVisible() {
        when(repository.findByIdAndUserId(21L, 8L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveSnapshot(8L, 21L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.ADDRESS_NOT_FOUND));
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

    private static void setId(ShippingAddressEntity address, long id) {
        try {
            Field field = ShippingAddressEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(address, id);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
