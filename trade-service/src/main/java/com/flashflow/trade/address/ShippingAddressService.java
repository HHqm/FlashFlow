package com.flashflow.trade.address;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

import com.flashflow.trade.common.BusinessException;
import com.flashflow.trade.common.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShippingAddressService {

    private final ShippingAddressRepository repository;
    private final ConcurrentMap<AddressCacheKey, ShippingAddressSnapshot> snapshotCache =
            new ConcurrentHashMap<>();
    private final ConcurrentMap<AddressCacheKey, ReentrantLock> snapshotLocks =
            new ConcurrentHashMap<>();

    public ShippingAddressService(ShippingAddressRepository repository) {
        this.repository = repository;
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
        evictUser(userId);
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
        evictUser(userId);
        return AddressDtos.AddressView.from(address);
    }

    @Transactional
    public AddressDtos.AddressView makeDefault(long userId, long addressId) {
        ShippingAddressEntity address = ownedAddress(userId, addressId);
        clearDefault(userId);
        address.markDefault(true);
        evictUser(userId);
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
        evictUser(userId);
    }

    @Transactional(readOnly = true)
    public ShippingAddressSnapshot resolveSnapshot(long userId, long addressId) {
        AddressCacheKey cacheKey = new AddressCacheKey(userId, addressId);
        ShippingAddressSnapshot cached = snapshotCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        ReentrantLock lock = snapshotLocks.computeIfAbsent(cacheKey, ignored -> new ReentrantLock());
        lock.lock();
        try {
            cached = snapshotCache.get(cacheKey);
            if (cached != null) {
                return cached;
            }
            ShippingAddressSnapshot loaded = ShippingAddressSnapshot.from(ownedAddress(userId, addressId));
            snapshotCache.put(cacheKey, loaded);
            return loaded;
        } finally {
            lock.unlock();
            snapshotLocks.remove(cacheKey, lock);
        }
    }

    private void clearDefault(long userId) {
        repository.findAllByUserIdOrderByDefaultAddressDescUpdatedAtDesc(userId)
                .forEach(item -> item.markDefault(false));
    }

    private ShippingAddressEntity ownedAddress(long userId, long addressId) {
        return repository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ADDRESS_NOT_FOUND));
    }

    private void evictUser(long userId) {
        snapshotCache.keySet().removeIf(key -> key.userId() == userId);
        snapshotLocks.keySet().removeIf(key -> key.userId() == userId);
    }

    private static AddressDtos.SaveAddressRequest withDefault(
            AddressDtos.SaveAddressRequest request,
            boolean value
    ) {
        return new AddressDtos.SaveAddressRequest(
                request.recipientName(), request.phone(), request.province(), request.city(),
                request.district(), request.detailAddress(), request.postalCode(), value);
    }

    private record AddressCacheKey(long userId, long addressId) {
    }
}
