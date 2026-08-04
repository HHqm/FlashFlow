package com.flashflow.trade.aftersale;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReturnRequestRepository extends JpaRepository<ReturnRequestEntity, Long> {

    boolean existsByOrderId(long orderId);

    Optional<ReturnRequestEntity> findByOrderId(long orderId);

    List<ReturnRequestEntity> findTop100ByUserIdOrderByRequestedAtDesc(long userId);

    List<ReturnRequestEntity> findTop200ByOrderByRequestedAtDesc();
}
