package com.flashflow.trade.aftersale;

import java.util.List;

import com.flashflow.trade.common.ApiResponse;
import com.flashflow.trade.order.OrderDtos;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AfterSaleController {

    private final AfterSaleService afterSaleService;

    public AfterSaleController(AfterSaleService afterSaleService) {
        this.afterSaleService = afterSaleService;
    }

    @PostMapping("/orders/{orderNo}/cancel")
    @PreAuthorize("hasAuthority('ROLE_USER')")
    ApiResponse<OrderDtos.OrderView> cancel(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String orderNo,
            @Valid @RequestBody(required = false) ReturnDtos.CancelOrderRequest request
    ) {
        return ApiResponse.success(afterSaleService.cancelPending(
                userId(jwt),
                orderNo,
                request == null ? null : request.reason()));
    }

    @PostMapping("/orders/{orderNo}/returns")
    @PreAuthorize("hasAuthority('ROLE_USER')")
    ApiResponse<ReturnDtos.ReturnView> requestReturn(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String orderNo,
            @Valid @RequestBody ReturnDtos.CreateReturnRequest request
    ) {
        return ApiResponse.success(afterSaleService.requestReturn(userId(jwt), orderNo, request));
    }

    @GetMapping("/returns")
    @PreAuthorize("hasAuthority('ROLE_USER')")
    ApiResponse<List<ReturnDtos.ReturnView>> listMine(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(afterSaleService.listMine(userId(jwt)));
    }

    @GetMapping("/admin/returns")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    ApiResponse<List<ReturnDtos.ReturnView>> listAll() {
        return ApiResponse.success(afterSaleService.listAll());
    }

    @PatchMapping("/admin/returns/{returnId}/decision")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    ApiResponse<ReturnDtos.ReturnView> decide(
            @PathVariable long returnId,
            @Valid @RequestBody ReturnDtos.ReturnDecisionRequest request
    ) {
        return ApiResponse.success(afterSaleService.decide(returnId, request));
    }

    private static long userId(Jwt jwt) {
        Number userId = jwt.getClaim("uid");
        return userId.longValue();
    }
}
