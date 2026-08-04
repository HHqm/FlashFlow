package com.flashflow.trade.product;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductVariantRepository extends JpaRepository<ProductVariantEntity, Long> {

    List<ProductVariantEntity> findAllByProductIdInOrderByIdAsc(Collection<Long> productIds);

    List<ProductVariantEntity> findAllByProductIdOrderByIdAsc(long productId);

    Optional<ProductVariantEntity> findBySkuCode(String skuCode);
}
