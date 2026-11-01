package com.flashflow.trade.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.flashflow.common.event.EventTypes;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import tools.jackson.databind.JsonNode;

class SeckillConcurrencyIT extends MiddlewareIntegrationTest {

    @Value("${flashflow.kafka.order-events-topic}")
    String orderEventsTopic;

    @Test
    void concurrentBuyersNeverOversellAndEveryOrderReachesKafka() {
        int stock = 30;
        long campaignId = createCampaign(stock, 1);
        List<Buyer> buyers = createBuyers(120);

        List<ApiResult> results = runConcurrently(
                buyers, buyer -> placeOrder(buyer, campaignId, newIdempotencyKey()));

        List<String> orderNos = results.stream()
                .filter(result -> result.status() == 200)
                .map(ApiResult::orderNo)
                .toList();
        assertThat(orderNos).hasSize(stock).doesNotHaveDuplicates();
        assertThat(results)
                .filteredOn(result -> result.status() != 200)
                .allSatisfy(result -> {
                    assertThat(result.status()).isEqualTo(409);
                    assertThat(result.code()).isEqualTo("SOLD_OUT");
                });

        assertThat(databaseStock(campaignId)).isZero();
        assertThat(redisStock(campaignId)).isZero();
        assertThat(countOrders(campaignId)).isEqualTo(stock);

        // Outbox -> Kafka is at-least-once, so the topic must contain at least every created order.
        assertThat(consumeCreatedOrderNos(Set.copyOf(orderNos), Duration.ofSeconds(60)))
                .containsAll(orderNos);
    }

    @Test
    void concurrentReplaysOfOneRequestCreateExactlyOneOrder() {
        long campaignId = createCampaign(10, 1);
        Buyer buyer = createBuyers(1).getFirst();
        String idempotencyKey = newIdempotencyKey();

        List<ApiResult> results = runConcurrently(
                Collections.nCopies(40, buyer), same -> placeOrder(same, campaignId, idempotencyKey));

        Set<String> orderNos = results.stream()
                .filter(result -> result.status() == 200)
                .map(ApiResult::orderNo)
                .collect(Collectors.toSet());
        assertThat(orderNos).hasSize(1);
        // Replays that arrive while the first write is still in the batch queue are told to poll.
        assertThat(results)
                .filteredOn(result -> result.status() != 200)
                .allSatisfy(result -> assertThat(result.code()).isEqualTo("REQUEST_PROCESSING"));

        ApiResult lateReplay = placeOrder(buyer, campaignId, idempotencyKey);
        assertThat(lateReplay.status()).isEqualTo(200);
        assertThat(lateReplay.orderNo()).isEqualTo(orderNos.iterator().next());
        assertThat(lateReplay.body().path("data").path("replayed").asBoolean()).isTrue();

        assertThat(countOrders(campaignId)).isEqualTo(1);
        assertThat(databaseStock(campaignId)).isEqualTo(9);
        assertThat(redisStock(campaignId)).isEqualTo(9);
    }

    private Set<String> consumeCreatedOrderNos(Set<String> expected, Duration timeout) {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        Set<String> seen = new HashSet<>();
        long deadline = System.nanoTime() + timeout.toNanos();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties)) {
            consumer.subscribe(List.of(orderEventsTopic));
            while (!seen.containsAll(expected) && System.nanoTime() < deadline) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    JsonNode event = objectMapper.readTree(record.value());
                    if (EventTypes.ORDER_CREATED.equals(event.path("eventType").asString())) {
                        // The partition key must be the order number so per-order events stay ordered.
                        assertThat(record.key()).isEqualTo(event.path("aggregateId").asString());
                        seen.add(record.key());
                    }
                }
            }
        }
        return seen;
    }
}
