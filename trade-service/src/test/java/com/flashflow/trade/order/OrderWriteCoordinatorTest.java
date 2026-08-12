package com.flashflow.trade.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import com.flashflow.trade.address.ShippingAddressSnapshot;
import com.flashflow.trade.campaign.CampaignStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderWriteCoordinatorTest {

    @Mock
    private OrderTransactionalService transactionalService;

    @Test
    void combinesConcurrentOrdersForTheSameCampaignIntoOneTransaction() throws Exception {
        OrderCampaignContext campaign = new OrderCampaignContext(
                10L,
                new BigDecimal("99.00"),
                100,
                Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(3600),
                CampaignStatus.ACTIVE,
                new OrderProductSnapshot(3L, 4L, "商品", "黑色 · 标准版"));
        when(transactionalService.persistReservedOrders(anyList()))
                .thenAnswer(invocation -> {
                    List<OrderWriteCommand> commands = invocation.getArgument(0);
                    return commands.stream()
                            .map(command -> new OrderEntity(
                                    command.orderNo(),
                                    command.userId(),
                                    command.campaign().campaignId(),
                                    command.productSnapshot().productId(),
                                    command.campaign().flashPrice(),
                                    command.idempotencyKey()))
                            .toList();
                });

        OrderWriteCoordinator coordinator = new OrderWriteCoordinator(
                transactionalService,
                1,
                64,
                64,
                Duration.ofMillis(30),
                new SimpleMeterRegistry());
        coordinator.start();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            CountDownLatch startGate = new CountDownLatch(1);
            List<java.util.concurrent.Future<OrderEntity>> results = java.util.stream.IntStream.range(0, 10)
                    .mapToObj(index -> executor.submit(() -> {
                        startGate.await();
                        return coordinator.persist(new OrderWriteCommand(
                                index + 1L,
                                campaign,
                                "request_" + index,
                                "FF" + index,
                                1,
                                campaign.productSnapshot(),
                                new ShippingAddressSnapshot(
                                        index + 100L,
                                        "User " + index,
                                        "13800000000",
                                        "Test address " + index)));
                    }))
                    .toList();
            startGate.countDown();
            for (var result : results) {
                assertThat(result.get().getCampaignId()).isEqualTo(10L);
            }
        } finally {
            coordinator.stop();
        }

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<OrderWriteCommand>> captor = ArgumentCaptor.forClass(List.class);
        verify(transactionalService).persistReservedOrders(captor.capture());
        assertThat(captor.getValue()).hasSize(10);
    }
}
