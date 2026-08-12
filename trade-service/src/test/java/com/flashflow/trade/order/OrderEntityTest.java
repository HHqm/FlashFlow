package com.flashflow.trade.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;

import com.flashflow.trade.common.BusinessException;
import com.flashflow.trade.common.ErrorCode;
import org.junit.jupiter.api.Test;

class OrderEntityTest {

    @Test
    void allowsOnlyOneTerminalTransition() {
        OrderEntity order = new OrderEntity(
                "FF-1",
                1L,
                2L,
                3L,
                new BigDecimal("10.00"),
                "request_12345678");

        order.pay(Instant.now());

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThatThrownBy(() -> order.pay(Instant.now()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.ORDER_STATUS_INVALID));
        assertThat(order.cancelIfPending(Instant.now())).isFalse();
    }

    @Test
    void cancelsPendingOrderIdempotently() {
        OrderEntity order = new OrderEntity(
                "FF-2",
                1L,
                2L,
                3L,
                new BigDecimal("10.00"),
                "request_12345679");

        assertThat(order.cancelIfPending(Instant.now())).isTrue();
        assertThat(order.cancelIfPending(Instant.now())).isFalse();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void supportsRefundApprovalStateMachine() {
        OrderEntity order = new OrderEntity(
                "FF-3",
                1L,
                2L,
                3L,
                new BigDecimal("20.00"),
                "request_12345680");

        order.pay(Instant.now());
        order.requestRefund(Instant.now());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUND_REQUESTED);

        order.approveRefund(Instant.now());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(order.getRefundedAt()).isNotNull();
    }

    @Test
    void rejectedRefundReturnsOrderToPaid() {
        OrderEntity order = new OrderEntity(
                "FF-4",
                1L,
                2L,
                3L,
                new BigDecimal("20.00"),
                "request_12345681");

        order.pay(Instant.now());
        order.requestRefund(Instant.now());
        order.rejectRefund(Instant.now());

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void completesPaidToShippedToReceivedLifecycle() {
        OrderEntity order = new OrderEntity(
                "FF-5", 1L, 2L, 3L, new BigDecimal("88.00"), "request_12345682");

        order.pay(Instant.now());
        order.ship("SF Express", "SF123456789", Instant.now());

        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(order.getCarrier()).isEqualTo("SF Express");
        assertThat(order.getTrackingNo()).isEqualTo("SF123456789");
        assertThatThrownBy(() -> order.ship("Other", "NO-2", Instant.now()))
                .isInstanceOf(BusinessException.class);

        order.receive(Instant.now());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.RECEIVED);
        assertThat(order.getReceivedAt()).isNotNull();
    }

    @Test
    void rejectedRefundRestoresOriginalFulfillmentStatus() {
        OrderEntity order = new OrderEntity(
                "FF-6", 1L, 2L, 3L, new BigDecimal("66.00"), "request_12345683");

        order.pay(Instant.now());
        order.ship("JD Logistics", "JD123", Instant.now());
        order.requestRefund(Instant.now());
        order.rejectRefund(Instant.now());

        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        order.receive(Instant.now());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.RECEIVED);
    }
}
