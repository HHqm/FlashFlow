package com.flashflow.trade.review;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<ReviewEntity, Long> {

    boolean existsByOrderId(long orderId);

    List<ReviewEntity> findTop100ByProductIdAndVisibleTrueOrderByCreatedAtDesc(long productId);

    List<ReviewEntity> findTop100ByUserIdOrderByCreatedAtDesc(long userId);

    List<ReviewEntity> findTop200ByOrderByCreatedAtDesc();

    List<ReviewEntity> findAllByProductIdInAndVisibleTrue(Collection<Long> productIds);
}
