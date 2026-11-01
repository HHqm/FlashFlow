package com.flashflow.trade.address;

import java.util.List;

import com.flashflow.trade.common.BusinessException;
import com.flashflow.trade.common.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShippingAddressService {

    private final ShippingAddressRepository repository;
    private final ShippingAddressSnapshotReader snapshotReader;

    public ShippingAddressService(
            ShippingAddressRepository repository,
            ShippingAddressSnapshotReader snapshotReader
    ) {
        this.repository = repository;
        this.snapshotReader = snapshotReader;
    }

    @Transactional(readOnly = true)
    public List<AddressDtos.AddressView> list(long userId) {
        return repository.findAllByUserIdOrderByDefaultAddressDescUpdatedAtDesc(userId)
                .stream().map(AddressDtos.AddressView::from).toList();
    }

    @Transactional
    public AddressDtos.AddressView create(long userId, AddressDtos.SaveAddressRequest request) {
        boolean makeDefault = request.defaultAddress() || !repository.existsByUserId(userId);
        if (makeDefault) {
            clearDefault(userId);
        }
        ShippingAddressEntity address = new ShippingAddressEntity(
                userId,
                withDefault(request, makeDefault));
        ShippingAddressEntity saved = repository.save(address);
        return AddressDtos.AddressView.from(saved);
    }

    @Transactional
    public AddressDtos.AddressView update(
            long userId,
            long addressId,
            AddressDtos.SaveAddressRequest request
    ) {
        ShippingAddressEntity address = ownedAddress(userId, addressId);
        boolean makeDefault = request.defaultAddress() || address.isDefaultAddress();
        if (makeDefault) {
            clearDefault(userId);
        }
        address.update(withDefault(request, makeDefault));
        return AddressDtos.AddressView.from(address);
    }

    @Transactional
    public AddressDtos.AddressView makeDefault(long userId, long addressId) {
        ShippingAddressEntity address = ownedAddress(userId, addressId);
        clearDefault(userId);
        address.markDefault(true);
        return AddressDtos.AddressView.from(address);
    }

    @Transactional
    public void delete(long userId, long addressId) {
        ShippingAddressEntity address = ownedAddress(userId, addressId);
        boolean wasDefault = address.isDefaultAddress();
        repository.delete(address);
        repository.flush();
        if (wasDefault) {
            repository.findAllByUserIdOrderByDefaultAddressDescUpdatedAtDesc(userId).stream()
                    .findFirst().ifPresent(item -> item.markDefault(true));
        }
    }

    public ShippingAddressSnapshot resolveSnapshot(long userId, long addressId) {
        return snapshotReader.read(userId, addressId);
    }

    private void clearDefault(long userId) {
        repository.findAllByUserIdOrderByDefaultAddressDescUpdatedAtDesc(userId)
                .forEach(item -> item.markDefault(false));
    }

    private ShippingAddressEntity ownedAddress(long userId, long addressId) {
        return repository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ADDRESS_NOT_FOUND));
    }

    private static AddressDtos.SaveAddressRequest withDefault(
            AddressDtos.SaveAddressRequest request,
            boolean value
    ) {
        return new AddressDtos.SaveAddressRequest(
                request.recipientName(), request.phone(), request.province(), request.city(),
                request.district(), request.detailAddress(), request.postalCode(), value);
    }
}
