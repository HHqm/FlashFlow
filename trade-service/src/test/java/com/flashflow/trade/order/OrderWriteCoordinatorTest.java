package com.flashflow.trade.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import com.flashflow.trade.address.ShippingAddressSnapshot;
import com.flashflow.trade.campaign.CampaignStatus;
import com.flashflow.trade.common.BusinessException;
import com.flashflow.trade.common.ErrorCode;
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

    @Test
    void splitsAnOversizedBatchSoOrdersThatStillFitAreCommitted() throws Exception {
        OrderCampaignContext campaign = campaign();
        AtomicInteger databaseStock = new AtomicInteger(5);
        // Mirrors the conditional UPDATE: the whole batch fails when its total exceeds the stock.
        when(transactionalService.persistReservedOrders(anyList()))
                .thenAnswer(invocation -> {
                    List<OrderWriteCommand> commands = invocation.getArgument(0);
                    int total = commands.stream().mapToInt(OrderWriteCommand::quantity).sum();
                    if (databaseStock.get() < total) {
                        throw new BusinessException(ErrorCode.SOLD_OUT);
                    }
                    databaseStock.addAndGet(-total);
                    return commands.stream().map(OrderWriteCoordinatorTest::entity).toList();
                });
        OrderWriteCoordinator coordinator = new OrderWriteCoordinator(
                transactionalService, 1, 64, 64, Duration.ofMillis(30), new SimpleMeterRegistry());

        List<Future<OrderEntity>> results;
        coordinator.start();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            CountDownLatch startGate = new CountDownLatch(1);
            results = IntStream.range(0, 8)
                    .mapToObj(index -> executor.submit(() -> {
                        startGate.await();
                        return coordinator.persist(command(campaign, index));
                    }))
                    .toList();
            startGate.countDown();
            long committed = 0;
            long soldOut = 0;
            for (Future<OrderEntity> result : results) {
                try {
                    result.get();
                    committed++;
                } catch (ExecutionException exception) {
                    assertThat(exception.getCause()).isInstanceOfSatisfying(BusinessException.class,
                            business -> assertThat(business.errorCode()).isEqualTo(ErrorCode.SOLD_OUT));
                    soldOut++;
                }
            }
            assertThat(committed).isEqualTo(5);
            assertThat(soldOut).isEqualTo(3);
        } finally {
            coordinator.stop();
        }
        assertThat(databaseStock).hasValue(0);
    }

    @Test
    void soldOutBatchCostsLogarithmicTransactionsNotOnePerOrder() throws Exception {
        OrderCampaignContext campaign = campaign();
        AtomicInteger transactions = new AtomicInteger();
        when(transactionalService.persistReservedOrders(anyList()))
                .thenAnswer(invocation -> {
                    transactions.incrementAndGet();
                    throw new BusinessException(ErrorCode.SOLD_OUT);
                });
        OrderWriteCoordinator coordinator = new OrderWriteCoordinator(
                transactionalService, 1, 64, 64, Duration.ofMillis(200), new SimpleMeterRegistry());

        coordinator.start();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            CountDownLatch startGate = new CountDownLatch(1);
            List<Future<OrderEntity>> results = IntStream.range(0, 64)
                    .mapToObj(index -> executor.submit(() -> {
                        startGate.await();
                        return coordinator.persist(command(campaign, index));
                    }))
                    .toList();
            startGate.countDown();
            for (Future<OrderEntity> result : results) {
                assertThatThrownBy(result::get).hasCauseInstanceOf(BusinessException.class);
            }
        } finally {
            coordinator.stop();
        }
        // One batch of 64 walks 64 -> 32 -> ... -> 1 (7 attempts); a plain split would need 127.
        assertThat(transactions.get()).isLessThan(30);
    }

    private static OrderCampaignContext campaign() {
        return new OrderCampaignContext(
                10L,
                new BigDecimal("99.00"),
                100,
                Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(3600),
                CampaignStatus.ACTIVE,
                new OrderProductSnapshot(3L, 4L, "商品", "黑色 · 标准版"));
    }

    private static OrderWriteCommand command(OrderCampaignContext campaign, int index) {
        return new OrderWriteCommand(
                index + 1L,
                campaign,
                "request_" + index,
                "FF" + index,
                1,
                campaign.productSnapshot(),
                new ShippingAddressSnapshot(index + 100L, "User " + index, "13800000000", "Test address " + index));
    }

    private static OrderEntity entity(OrderWriteCommand command) {
        return new OrderEntity(
                command.orderNo(),
                command.userId(),
                command.campaign().campaignId(),
                command.productSnapshot().productId(),
                command.campaign().flashPrice(),
                command.idempotencyKey());
    }
}
