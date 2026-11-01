package com.flashflow.trade.inventory;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StockReconciliationLogRepository extends JpaRepository<StockReconciliationLog, Long> {

    List<StockReconciliationLog> findTop50ByOrderByCreatedAtDesc();

    List<StockReconciliationLog> findAllByCampaignIdOrderByCreatedAtDesc(long campaignId);
}
