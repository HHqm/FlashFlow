package com.flashflow.trade.product;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<ProductEntity, Long> {

    List<ProductEntity> findAllByActiveTrueOrderByIdDesc();

    List<ProductEntity> findAllByOrderByIdDesc();

    Optional<ProductEntity> findBySkuCode(String skuCode);
}
