package com.flashflow.trade.order;

import java.util.List;

import com.flashflow.trade.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class OrderController {

    private final OrderService orderService;
    private final OrderCommandService commandService;

    public OrderController(OrderService orderService, OrderCommandService commandService) {
        this.orderService = orderService;
        this.commandService = commandService;
    }

    @PostMapping("/campaigns/{campaignId}/orders")
    @PreAuthorize("hasAuthority('ROLE_USER')")
    ApiResponse<OrderDtos.OrderView> placeOrder(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long campaignId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody(required = false) OrderDtos.PlaceOrderRequest request
    ) {
        validateIdempotencyKey(idempotencyKey);
        int quantity = request == null ? 1 : request.quantity();
        return ApiResponse.success(orderService.placeOrder(
                userId(jwt), campaignId, idempotencyKey, quantity));
    }

    @GetMapping("/orders")
    @PreAuthorize("hasAuthority('ROLE_USER')")
    ApiResponse<List<OrderDtos.OrderView>> listMyOrders(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(orderService.listMyOrders(userId(jwt)));
    }

    @GetMapping("/admin/orders")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    ApiResponse<List<OrderDtos.OrderView>> listAllOrders() {
        return ApiResponse.success(orderService.listAllOrders());
    }

    @GetMapping("/orders/{orderNo}")
    @PreAuthorize("hasAuthority('ROLE_USER')")
    ApiResponse<OrderDtos.OrderView> getOrder(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String orderNo
    ) {
        return ApiResponse.success(orderService.getOrder(userId(jwt), orderNo, false));
    }

    @PostMapping("/orders/{orderNo}/pay")
    @PreAuthorize("hasAuthority('ROLE_USER')")
    ApiResponse<OrderDtos.OrderView> pay(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String orderNo
    ) {
        return ApiResponse.success(commandService.pay(userId(jwt), orderNo));
    }

    private static long userId(Jwt jwt) {
        Number userId = jwt.getClaim("uid");
        return userId.longValue();
    }

    private static void validateIdempotencyKey(String key) {
        if (key == null || !key.matches("[A-Za-z0-9_-]{8,64}")) {
            throw new com.flashflow.trade.common.BusinessException(
                    com.flashflow.trade.common.ErrorCode.VALIDATION_FAILED,
                    "Idempotency-Key 必须为 8-64 位字母、数字、下划线或连字符");
        }
    }
}
