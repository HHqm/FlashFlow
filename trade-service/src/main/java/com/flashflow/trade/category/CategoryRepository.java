package com.flashflow.trade.category;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<CategoryEntity, Long> {

    List<CategoryEntity> findAllByActiveTrueOrderBySortOrderAscIdAsc();

    List<CategoryEntity> findAllByOrderBySortOrderAscIdAsc();

    Optional<CategoryEntity> findByName(String name);
}
