package com.flashflow.trade.order;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.flashflow.trade.address.ShippingAddressSnapshot;
import com.flashflow.trade.campaign.CampaignStockRepository;
import com.flashflow.trade.campaign.CampaignStatus;
import com.flashflow.trade.common.BusinessException;
import com.flashflow.trade.common.ErrorCode;
import com.flashflow.trade.outbox.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderTransactionalServiceTest {

    private static final ShippingAddressSnapshot SHIPPING_ADDRESS = new ShippingAddressSnapshot(
            21L, "Test User", "13800000000", "Shanghai Pudong Test Road 1");

    @Mock
    private CampaignStockRepository stockRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OutboxService outboxService;

    private OrderTransactionalService service;
    private OrderCampaignContext campaign;

    @BeforeEach
    void setUp() {
        service = new OrderTransactionalService(stockRepository, orderRepository, outboxService);
        campaign = new OrderCampaignContext(
                10L,
                new BigDecimal("99.00"),
                2,
                Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(3600),
                CampaignStatus.ACTIVE,
                new OrderProductSnapshot(3L, 4L, "商品", "黑色 · 标准版"));
    }

    @Test
    void acquiresInventoryBeforeWritingOrderToKeepForeignKeyLockOrderStable() {
        when(stockRepository.decrement(10L, 0, 1)).thenReturn(1);
        when(orderRepository.saveAll(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.persistReservedOrder(7L, campaign, "request_12345678", "FF123", campaign.productSnapshot());

        InOrder sequence = inOrder(orderRepository, outboxService, stockRepository);
        sequence.verify(stockRepository).decrement(10L, 0, 1);
        sequence.verify(orderRepository).saveAll(anyList());
        sequence.verify(outboxService).appendAll(anyList());
    }

    @Test
    void rejectsOrderWhenDatabaseInventoryCannotBeReserved() {
        when(stockRepository.decrement(10L, 0, 1)).thenReturn(0);

        assertThatThrownBy(() -> service.persistReservedOrder(
                7L, campaign, "request_12345678", "FF123", campaign.productSnapshot()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.SOLD_OUT));
    }

    @Test
    void combinesInventoryDeductionForOrdersInTheSameBatch() {
        when(stockRepository.decrement(10L, 0, 3)).thenReturn(1);
        when(orderRepository.saveAll(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        List<OrderWriteCommand> commands = List.of(
                new OrderWriteCommand(
                        7L, campaign, "request_1", "FF1", 1,
                        campaign.productSnapshot(), SHIPPING_ADDRESS),
                new OrderWriteCommand(
                        8L, campaign, "request_2", "FF2", 2,
                        campaign.productSnapshot(), SHIPPING_ADDRESS));

        List<OrderEntity> orders = service.persistReservedOrders(commands);

        org.assertj.core.api.Assertions.assertThat(orders).hasSize(2);
        verify(stockRepository).decrement(10L, 0, 3);
        verify(orderRepository).saveAll(anyList());
        verify(outboxService).appendAll(anyList());
    }
}
