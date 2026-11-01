package com.flashflow.trade.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import com.flashflow.trade.address.ShippingAddressService;
import com.flashflow.trade.address.ShippingAddressSnapshot;
import com.flashflow.trade.campaign.CampaignStatus;
import com.flashflow.trade.campaign.ReservationResult;
import com.flashflow.trade.campaign.SeckillRedisRepository;
import com.flashflow.trade.common.BusinessException;
import com.flashflow.trade.common.ErrorCode;
import com.flashflow.trade.inventory.StockCompensationService;
import com.flashflow.trade.inventory.StockReservation;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final long USER_ID = 7L;
    private static final long CAMPAIGN_ID = 10L;
    private static final String REQUEST_KEY = "request_12345678";
    private static final String ORDER_NO = "FF1234567890";
    private static final long ADDRESS_ID = 21L;
    private static final ShippingAddressSnapshot SHIPPING_ADDRESS = new ShippingAddressSnapshot(
            ADDRESS_ID, "Test User", "13800000000", "Shanghai Pudong Test Road 1");
    private static final OrderProductSnapshot PRODUCT_SNAPSHOT = new OrderProductSnapshot(
            3L, 4L, "测试商品", "黑色 · 标准版");

    @Mock
    private OrderCampaignContextService campaignContextService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderNumberGenerator orderNumberGenerator;

    @Mock
    private SeckillRedisRepository redisRepository;

    @Mock
    private OrderWriteCoordinator writeCoordinator;

    @Mock
    private ShippingAddressService shippingAddressService;

    @Mock
    private StockCompensationService stockCompensationService;

    private OrderService orderService;
    private OrderCampaignContext campaign;

    @BeforeEach
    void setUp() {
        campaign = new OrderCampaignContext(
                CAMPAIGN_ID,
                new BigDecimal("399.00"),
                1,
                Instant.now().minus(1, ChronoUnit.HOURS),
                Instant.now().plus(1, ChronoUnit.HOURS),
                CampaignStatus.ACTIVE,
                PRODUCT_SNAPSHOT);
        orderService = new OrderService(
                campaignContextService,
                orderRepository,
                orderNumberGenerator,
                redisRepository,
                writeCoordinator,
                shippingAddressService,
                stockCompensationService,
                new SimpleMeterRegistry());
        when(campaignContextService.resolve(CAMPAIGN_ID)).thenReturn(campaign);
        when(shippingAddressService.resolveSnapshot(USER_ID, ADDRESS_ID)).thenReturn(SHIPPING_ADDRESS);
    }

    @Test
    void createsOrderAfterRedisReservation() {
        OrderEntity persisted = new OrderEntity(
                ORDER_NO,
                USER_ID,
                CAMPAIGN_ID,
                3L,
                new BigDecimal("399.00"),
                REQUEST_KEY);
        when(orderNumberGenerator.next()).thenReturn(ORDER_NO);
        when(redisRepository.reserve(
                CAMPAIGN_ID,
                USER_ID,
                REQUEST_KEY,
                ORDER_NO,
                campaign.endsAt(),
                1,
                campaign.purchaseLimit()))
                .thenReturn(ReservationResult.reserved(ORDER_NO));
        OrderWriteCommand command = new OrderWriteCommand(
                USER_ID, campaign, REQUEST_KEY, ORDER_NO, 1, PRODUCT_SNAPSHOT, SHIPPING_ADDRESS);
        when(writeCoordinator.persist(command))
                .thenReturn(persisted);

        OrderDtos.OrderView result = orderService.placeOrder(
                USER_ID, CAMPAIGN_ID, REQUEST_KEY, 1, ADDRESS_ID);

        assertThat(result.orderNo()).isEqualTo(ORDER_NO);
        assertThat(result.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(result.replayed()).isFalse();
        verify(writeCoordinator).persist(command);
    }

    @Test
    void compensatesRedisWhenDatabaseTransactionFails() {
        when(orderNumberGenerator.next()).thenReturn(ORDER_NO);
        when(redisRepository.reserve(
                CAMPAIGN_ID,
                USER_ID,
                REQUEST_KEY,
                ORDER_NO,
                campaign.endsAt(),
                1,
                campaign.purchaseLimit()))
                .thenReturn(ReservationResult.reserved(ORDER_NO));
        OrderWriteCommand command = new OrderWriteCommand(
                USER_ID, campaign, REQUEST_KEY, ORDER_NO, 1, PRODUCT_SNAPSHOT, SHIPPING_ADDRESS);
        when(writeCoordinator.persist(command))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertThatThrownBy(() -> orderService.placeOrder(
                USER_ID, CAMPAIGN_ID, REQUEST_KEY, 1, ADDRESS_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");
        verify(redisRepository).compensate(CAMPAIGN_ID, USER_ID, REQUEST_KEY, ORDER_NO, 1);
        verify(stockCompensationService, never()).recordFailedWrite(any(), any());
    }

    @Test
    void defersCompensationToDurableTaskWhenRedisIsAlsoDown() {
        when(orderNumberGenerator.next()).thenReturn(ORDER_NO);
        when(redisRepository.reserve(
                CAMPAIGN_ID,
                USER_ID,
                REQUEST_KEY,
                ORDER_NO,
                campaign.endsAt(),
                1,
                campaign.purchaseLimit()))
                .thenReturn(ReservationResult.reserved(ORDER_NO));
        OrderWriteCommand command = new OrderWriteCommand(
                USER_ID, campaign, REQUEST_KEY, ORDER_NO, 1, PRODUCT_SNAPSHOT, SHIPPING_ADDRESS);
        IllegalStateException writeFailure = new IllegalStateException("database unavailable");
        when(writeCoordinator.persist(command)).thenThrow(writeFailure);
        when(redisRepository.compensate(CAMPAIGN_ID, USER_ID, REQUEST_KEY, ORDER_NO, 1))
                .thenThrow(new IllegalStateException("redis unavailable"));

        assertThatThrownBy(() -> orderService.placeOrder(
                USER_ID, CAMPAIGN_ID, REQUEST_KEY, 1, ADDRESS_ID))
                .isSameAs(writeFailure);

        verify(stockCompensationService).recordFailedWrite(
                new StockReservation(CAMPAIGN_ID, USER_ID, REQUEST_KEY, ORDER_NO, 1),
                writeFailure);
        assertThat(writeFailure.getSuppressed()).hasSize(1);
    }

    @Test
    void replaysExistingOrderForDuplicateRequest() {
        OrderEntity existing = new OrderEntity(
                ORDER_NO,
                USER_ID,
                CAMPAIGN_ID,
                3L,
                new BigDecimal("399.00"),
                REQUEST_KEY);
        when(orderNumberGenerator.next()).thenReturn("IGNORED_NEW_ORDER");
        when(redisRepository.reserve(
                CAMPAIGN_ID,
                USER_ID,
                REQUEST_KEY,
                "IGNORED_NEW_ORDER",
                campaign.endsAt(),
                1,
                campaign.purchaseLimit()))
                .thenReturn(ReservationResult.duplicate(ORDER_NO));
        when(orderRepository.findByOrderNo(ORDER_NO)).thenReturn(Optional.of(existing));

        OrderDtos.OrderView result = orderService.placeOrder(
                USER_ID, CAMPAIGN_ID, REQUEST_KEY, 1, ADDRESS_ID);

        assertThat(result.orderNo()).isEqualTo(ORDER_NO);
        assertThat(result.replayed()).isTrue();
        verify(writeCoordinator, never()).persist(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsSoldOutResultWithoutWritingDatabase() {
        when(orderNumberGenerator.next()).thenReturn(ORDER_NO);
        when(redisRepository.reserve(
                CAMPAIGN_ID,
                USER_ID,
                REQUEST_KEY,
                ORDER_NO,
                campaign.endsAt(),
                1,
                campaign.purchaseLimit()))
                .thenReturn(new ReservationResult(ReservationResult.Status.SOLD_OUT, null));

        assertThatThrownBy(() -> orderService.placeOrder(
                USER_ID, CAMPAIGN_ID, REQUEST_KEY, 1, ADDRESS_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.SOLD_OUT));
        verify(writeCoordinator, never()).persist(org.mockito.ArgumentMatchers.any());
    }
}
