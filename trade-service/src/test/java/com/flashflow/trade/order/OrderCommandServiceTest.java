package com.flashflow.trade.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import com.flashflow.common.event.DomainEvent;
import com.flashflow.trade.campaign.CampaignStockRepository;
import com.flashflow.trade.common.BusinessException;
import com.flashflow.trade.common.ErrorCode;
import com.flashflow.trade.inventory.StockCompensationReason;
import com.flashflow.trade.inventory.StockCompensationService;
import com.flashflow.trade.outbox.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderCommandServiceTest {

    private static final long USER_ID = 7L;
    private static final String ORDER_NO = "FF1234567890";

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CampaignStockRepository stockRepository;

    @Mock
    private OutboxService outboxService;

    @Mock
    private StockCompensationService stockCompensationService;

    private OrderCommandService commandService;

    @BeforeEach
    void setUp() {
        commandService = new OrderCommandService(
                orderRepository,
                stockRepository,
                outboxService,
                stockCompensationService,
                Duration.ofMinutes(15));
    }

    @Test
    void paysOrderInsidePaymentWindow() {
        OrderEntity order = orderCreatedMinutesAgo(1);
        when(orderRepository.findByOrderNo(ORDER_NO)).thenReturn(Optional.of(order));

        OrderDtos.OrderView view = commandService.pay(USER_ID, ORDER_NO);

        assertThat(view.status()).isEqualTo(OrderStatus.PAID);
        verify(outboxService).append(ArgumentMatchers.any(DomainEvent.class));
    }

    @Test
    void rejectsPaymentAfterDeadlineEvenIfTimeoutJobHasNotRunYet() {
        OrderEntity order = orderCreatedMinutesAgo(16);
        when(orderRepository.findByOrderNo(ORDER_NO)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> commandService.pay(USER_ID, ORDER_NO))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.ORDER_STATUS_INVALID));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        verify(outboxService, never()).append(ArgumentMatchers.any(DomainEvent.class));
    }

    @Test
    void rejectsPaymentFromAnotherUser() {
        OrderEntity order = orderCreatedMinutesAgo(1);
        when(orderRepository.findByOrderNo(ORDER_NO)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> commandService.pay(USER_ID + 1, ORDER_NO))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void adminShipsAndOwnerConfirmsReceipt() {
        OrderEntity order = orderCreatedMinutesAgo(1);
        order.pay(Instant.now());
        when(orderRepository.findByOrderNo(ORDER_NO)).thenReturn(Optional.of(order));

        OrderDtos.OrderView shipped = commandService.ship(
                ORDER_NO, new OrderDtos.ShipOrderRequest("SF Express", "SF987654321"));
        OrderDtos.OrderView received = commandService.receive(USER_ID, ORDER_NO);

        assertThat(shipped.status()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(shipped.trackingNo()).isEqualTo("SF987654321");
        assertThat(received.status()).isEqualTo(OrderStatus.RECEIVED);
        verify(outboxService, org.mockito.Mockito.times(2))
                .append(ArgumentMatchers.any(DomainEvent.class));
    }

    @Test
    void anotherUserCannotConfirmReceipt() {
        OrderEntity order = orderCreatedMinutesAgo(1);
        order.pay(Instant.now());
        order.ship("SF Express", "SF987654321", Instant.now());
        when(orderRepository.findByOrderNo(ORDER_NO)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> commandService.receive(USER_ID + 1, ORDER_NO))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
    }

    @Test
    void timeoutCancellationRestoresMysqlStockAndSchedulesRedisCompensation() {
        OrderEntity order = orderCreatedMinutesAgo(16);
        when(orderRepository.findByOrderNo(ORDER_NO)).thenReturn(Optional.of(order));

        boolean cancelled = commandService.cancelIfExpired(
                ORDER_NO, Instant.now().minus(15, ChronoUnit.MINUTES));

        assertThat(cancelled).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(stockRepository).increment(10L, 0, 1);
        verify(stockCompensationService).scheduleAfterCommit(
                order.stockReservation(), StockCompensationReason.PAYMENT_TIMEOUT);
    }

    @Test
    void timeoutCancellationSkipsOrdersThatWerePaidMeanwhile() {
        OrderEntity order = orderCreatedMinutesAgo(16);
        order.pay(Instant.now());
        when(orderRepository.findByOrderNo(ORDER_NO)).thenReturn(Optional.of(order));

        boolean cancelled = commandService.cancelIfExpired(
                ORDER_NO, Instant.now().minus(15, ChronoUnit.MINUTES));

        assertThat(cancelled).isFalse();
        verify(stockRepository, never()).increment(10L, 0, 1);
        verify(stockCompensationService, never()).scheduleAfterCommit(
                ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    private static OrderEntity orderCreatedMinutesAgo(int minutes) {
        OrderEntity order = new OrderEntity(
                ORDER_NO,
                USER_ID,
                10L,
                3L,
                new BigDecimal("399.00"),
                "request_12345678");
        backdate(order, Instant.now().minus(minutes, ChronoUnit.MINUTES));
        return order;
    }

    private static void backdate(OrderEntity order, Instant createdAt) {
        try {
            Field field = OrderEntity.class.getDeclaredField("createdAt");
            field.setAccessible(true);
            field.set(order, createdAt);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
