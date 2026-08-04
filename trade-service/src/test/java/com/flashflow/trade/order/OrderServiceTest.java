package com.flashflow.trade.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import com.flashflow.trade.campaign.CampaignEntity;
import com.flashflow.trade.campaign.CampaignRepository;
import com.flashflow.trade.campaign.ReservationResult;
import com.flashflow.trade.campaign.SeckillRedisRepository;
import com.flashflow.trade.common.BusinessException;
import com.flashflow.trade.common.ErrorCode;
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

    @Mock
    private CampaignRepository campaignRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderNumberGenerator orderNumberGenerator;

    @Mock
    private SeckillRedisRepository redisRepository;

    @Mock
    private OrderTransactionalService transactionalService;

    @Mock
    private CampaignEntity campaign;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(
                campaignRepository,
                orderRepository,
                orderNumberGenerator,
                redisRepository,
                transactionalService,
                new SimpleMeterRegistry());
        when(campaignRepository.findById(CAMPAIGN_ID)).thenReturn(Optional.of(campaign));
        when(campaign.getEndsAt()).thenReturn(Instant.now().plus(1, ChronoUnit.HOURS));
        when(campaign.hasStarted(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        when(campaign.hasEnded(org.mockito.ArgumentMatchers.any())).thenReturn(false);
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
                campaign.getEndsAt()))
                .thenReturn(ReservationResult.reserved(ORDER_NO));
        when(transactionalService.persistReservedOrder(
                USER_ID,
                campaign,
                REQUEST_KEY,
                ORDER_NO))
                .thenReturn(persisted);

        OrderDtos.OrderView result = orderService.placeOrder(USER_ID, CAMPAIGN_ID, REQUEST_KEY);

        assertThat(result.orderNo()).isEqualTo(ORDER_NO);
        assertThat(result.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(result.replayed()).isFalse();
        verify(redisRepository).checkRateLimit(USER_ID);
        verify(transactionalService).persistReservedOrder(
                USER_ID,
                campaign,
                REQUEST_KEY,
                ORDER_NO);
    }

    @Test
    void compensatesRedisWhenDatabaseTransactionFails() {
        when(campaign.getId()).thenReturn(CAMPAIGN_ID);
        when(orderNumberGenerator.next()).thenReturn(ORDER_NO);
        when(redisRepository.reserve(
                CAMPAIGN_ID,
                USER_ID,
                REQUEST_KEY,
                ORDER_NO,
                campaign.getEndsAt()))
                .thenReturn(ReservationResult.reserved(ORDER_NO));
        when(transactionalService.persistReservedOrder(
                USER_ID,
                campaign,
                REQUEST_KEY,
                ORDER_NO))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertThatThrownBy(() -> orderService.placeOrder(USER_ID, CAMPAIGN_ID, REQUEST_KEY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");
        verify(redisRepository).compensate(CAMPAIGN_ID, USER_ID, REQUEST_KEY, ORDER_NO);
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
                campaign.getEndsAt()))
                .thenReturn(ReservationResult.duplicate(ORDER_NO));
        when(orderRepository.findByOrderNo(ORDER_NO)).thenReturn(Optional.of(existing));

        OrderDtos.OrderView result = orderService.placeOrder(USER_ID, CAMPAIGN_ID, REQUEST_KEY);

        assertThat(result.orderNo()).isEqualTo(ORDER_NO);
        assertThat(result.replayed()).isTrue();
        verify(transactionalService, never()).persistReservedOrder(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void rejectsSoldOutResultWithoutWritingDatabase() {
        when(orderNumberGenerator.next()).thenReturn(ORDER_NO);
        when(redisRepository.reserve(
                CAMPAIGN_ID,
                USER_ID,
                REQUEST_KEY,
                ORDER_NO,
                campaign.getEndsAt()))
                .thenReturn(new ReservationResult(ReservationResult.Status.SOLD_OUT, null));

        assertThatThrownBy(() -> orderService.placeOrder(USER_ID, CAMPAIGN_ID, REQUEST_KEY))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.SOLD_OUT));
        verify(transactionalService, never()).persistReservedOrder(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }
}
