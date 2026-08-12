package com.flashflow.trade.address;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ShippingAddressRepository extends JpaRepository<ShippingAddressEntity, Long> {

    List<ShippingAddressEntity> findAllByUserIdOrderByDefaultAddressDescUpdatedAtDesc(long userId);

    Optional<ShippingAddressEntity> findByIdAndUserId(long id, long userId);

    boolean existsByUserId(long userId);
}
