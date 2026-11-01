package com.flashflow.trade.integration;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;

import com.flashflow.trade.address.AddressDtos;
import com.flashflow.trade.address.ShippingAddressEntity;
import com.flashflow.trade.address.ShippingAddressRepository;
import com.flashflow.trade.auth.UserEntity;
import com.flashflow.trade.auth.UserRepository;
import com.flashflow.trade.campaign.CampaignCacheInitializer;
import com.flashflow.trade.campaign.CampaignDtos;
import com.flashflow.trade.campaign.CampaignService;
import com.flashflow.trade.product.ProductEntity;
import com.flashflow.trade.product.ProductRepository;
import com.flashflow.trade.product.ProductVariantEntity;
import com.flashflow.trade.product.ProductVariantRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.mysql.MySQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Boots the real trade-service against the same MySQL, Redis and Kafka images as
 * {@code compose.yml}. Containers are started once per JVM and shared by every IT class, so
 * Spring can reuse one application context.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "flashflow.demo-data-enabled=false",
                "flashflow.seckill.requests-per-second=10000",
                "flashflow.jobs.order-timeout-enabled=false",
                // Self-healing jobs are driven explicitly by the tests to keep them deterministic.
                "flashflow.jobs.stock-compensation-enabled=false",
                "flashflow.jobs.stock-reconciliation-enabled=false",
                "flashflow.stock-reconciliation.quiet-window=PT0S",
                "flashflow.kafka.order-events-topic=it.order-events.v1",
                "flashflow.kafka.partitions=3",
                "spring.datasource.hikari.minimum-idle=4"
        })
abstract class MiddlewareIntegrationTest {

    static final String REDIS_PASSWORD = "flashflow_redis";

    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("flashflow_trade")
            .withUsername("flashflow")
            .withPassword("flashflow_app");

    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:8.4.4-alpine")
            .withExposedPorts(6379)
            .withCommand("redis-server", "--requirepass", REDIS_PASSWORD);

    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.3.1");

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @DynamicPropertySource
    static void middleware(DynamicPropertyRegistry registry) {
        // Started here rather than in a static block so that disabledWithoutDocker is evaluated first.
        Startables.deepStart(MYSQL, REDIS, KAFKA).join();
        registry.add("spring.datasource.url", () -> MYSQL.getJdbcUrl()
                + "?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&rewriteBatchedStatements=true"
                + "&useLocalSessionState=true&cacheServerConfiguration=true");
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> REDIS_PASSWORD);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @LocalServerPort
    int port;

    @Autowired
    UserRepository userRepository;

    @Autowired
    ShippingAddressRepository addressRepository;

    @Autowired
    ProductRepository productRepository;

    @Autowired
    ProductVariantRepository variantRepository;

    @Autowired
    CampaignService campaignService;

    @Autowired
    StringRedisTemplate redisTemplate;

    @Autowired
    JwtEncoder jwtEncoder;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbcTemplate;

    record Buyer(long userId, long addressId, String token) {
    }

    List<Buyer> createBuyers(int count) {
        String batch = UUID.randomUUID().toString().substring(0, 8);
        List<UserEntity> users = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            // Password hashes are never checked: tokens are minted directly below.
            users.add(new UserEntity("it-" + batch + "-" + index, "{noop}unused", "USER"));
        }
        List<Buyer> buyers = new ArrayList<>(count);
        for (UserEntity user : userRepository.saveAll(users)) {
            ShippingAddressEntity address = addressRepository.save(new ShippingAddressEntity(
                    user.getId(),
                    new AddressDtos.SaveAddressRequest(
                            "集成测试", "13800000000", "上海市", "上海市", "浦东新区", "测试路 1 号", "200000", true)));
            buyers.add(new Buyer(user.getId(), address.getId(), token(user.getId(), user.getUsername(), "USER")));
        }
        return buyers;
    }

    long createCampaign(int stock, int purchaseLimit) {
        String sku = "IT-" + UUID.randomUUID().toString().substring(0, 12);
        ProductEntity product = productRepository.save(new ProductEntity(
                null, sku, "集成测试商品 " + sku, "Testcontainers", "", new BigDecimal("199.00")));
        ProductVariantEntity variant = variantRepository.save(new ProductVariantEntity(
                product.getId(), sku + "-BLK", "黑色", "标准版", new BigDecimal("199.00"), stock));
        // Goes through the real service so the after-commit Redis warm-up is exercised too.
        return campaignService.createCampaign(new CampaignDtos.CreateCampaignRequest(
                variant.getId(),
                new BigDecimal("99.00"),
                stock,
                purchaseLimit,
                Instant.now().minus(1, ChronoUnit.MINUTES),
                Instant.now().plus(1, ChronoUnit.HOURS))).id();
    }

    Integer redisStock(long campaignId) {
        String value = redisTemplate.opsForValue().get(CampaignCacheInitializer.stockKey(campaignId));
        return value == null ? null : Integer.valueOf(value);
    }

    int databaseStock(long campaignId) {
        return jdbcTemplate.queryForObject(
                "select remaining_stock from flash_campaign where id = ?", Integer.class, campaignId);
    }

    long countOrders(long campaignId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from trade_order where campaign_id = ?", Long.class, campaignId);
    }

    /** Releases every call at the same instant to maximise contention on the hot campaign. */
    static <T> List<ApiResult> runConcurrently(List<T> inputs, Function<T, ApiResult> call) {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<ApiResult>> futures = new ArrayList<>(inputs.size());
            for (T input : inputs) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return call.apply(input);
                }));
            }
            start.countDown();
            List<ApiResult> results = new ArrayList<>(futures.size());
            for (Future<ApiResult> future : futures) {
                results.add(future.get());
            }
            return results;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        } catch (java.util.concurrent.ExecutionException exception) {
            throw new IllegalStateException(exception.getCause());
        }
    }

    static void pause(GenericContainer<?> container) {
        container.getDockerClient().pauseContainerCmd(container.getContainerId()).exec();
    }

    static void unpause(GenericContainer<?> container) {
        container.getDockerClient().unpauseContainerCmd(container.getContainerId()).exec();
    }

    ApiResult placeOrder(Buyer buyer, long campaignId, String idempotencyKey) {
        return post(
                "/api/v1/campaigns/" + campaignId + "/orders",
                buyer.token(),
                idempotencyKey,
                "{\"quantity\":1,\"addressId\":" + buyer.addressId() + "}");
    }

    ApiResult cancelOrder(Buyer buyer, String orderNo) {
        return post("/api/v1/orders/" + orderNo + "/cancel", buyer.token(), null, "{\"reason\":\"集成测试取消\"}");
    }

    ApiResult post(String path, String token, String idempotencyKey, String body) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        try {
            HttpResponse<String> response = HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
            return new ApiResult(response.statusCode(), objectMapper.readTree(response.body()));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    static String newIdempotencyKey() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String token(long userId, String username, String role) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("flashflow-trade-service")
                .subject(username)
                .issuedAt(now)
                .expiresAt(now.plus(1, ChronoUnit.HOURS))
                .claim("uid", userId)
                .claim("roles", List.of(role))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    record ApiResult(int status, JsonNode body) {

        String code() {
            return body.path("code").asString();
        }

        String orderNo() {
            return body.path("data").path("orderNo").asString();
        }
    }
}
