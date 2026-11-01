package com.flashflow.trade.inventory;

import java.util.List;

import com.flashflow.trade.common.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/inventory")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class InventoryController {

    private final StockReconciliationService reconciliationService;
    private final StockCompensationService compensationService;
    private final StockCompensationRepository compensationRepository;
    private final StockReconciliationLogRepository logRepository;

    public InventoryController(
            StockReconciliationService reconciliationService,
            StockCompensationService compensationService,
            StockCompensationRepository compensationRepository,
            StockReconciliationLogRepository logRepository
    ) {
        this.reconciliationService = reconciliationService;
        this.compensationService = compensationService;
        this.compensationRepository = compensationRepository;
        this.logRepository = logRepository;
    }

    @GetMapping("/reconciliation")
    ApiResponse<InventoryDtos.ReconciliationReport> inspect() {
        return ApiResponse.success(reconciliationService.inspect());
    }

    @PostMapping("/reconciliation")
    ApiResponse<InventoryDtos.ReconciliationReport> reconcileNow() {
        return ApiResponse.success(
                reconciliationService.reconcile(StockReconciliationLog.TriggerSource.MANUAL));
    }

    @GetMapping("/reconciliation/logs")
    ApiResponse<List<InventoryDtos.ReconciliationLogView>> listRepairs() {
        return ApiResponse.success(logRepository.findTop50ByOrderByCreatedAtDesc().stream()
                .map(InventoryDtos.ReconciliationLogView::from)
                .toList());
    }

    @GetMapping("/compensations")
    ApiResponse<List<InventoryDtos.CompensationTaskView>> listCompensations() {
        return ApiResponse.success(compensationRepository.findTop100ByOrderByCreatedAtDesc().stream()
                .map(InventoryDtos.CompensationTaskView::from)
                .toList());
    }

    @PostMapping("/compensations/{taskId}/retry")
    ApiResponse<InventoryDtos.CompensationTaskView> retryCompensation(@PathVariable long taskId) {
        return ApiResponse.success(
                InventoryDtos.CompensationTaskView.from(compensationService.retryNow(taskId)));
    }
}
