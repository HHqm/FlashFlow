package com.flashflow.trade.config;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.flashflow.trade.auth.UserEntity;
import com.flashflow.trade.auth.UserRepository;
import com.flashflow.trade.campaign.CampaignCacheInitializer;
import com.flashflow.trade.campaign.CampaignEntity;
import com.flashflow.trade.campaign.CampaignRepository;
import com.flashflow.trade.campaign.CampaignStatus;
import com.flashflow.trade.category.CategoryEntity;
import com.flashflow.trade.category.CategoryRepository;
import com.flashflow.trade.product.ProductEntity;
import com.flashflow.trade.product.ProductRepository;
import com.flashflow.trade.product.ProductVariantEntity;
import com.flashflow.trade.product.ProductVariantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "flashflow.demo-data-enabled", havingValue = "true", matchIfMissing = true)
public class DemoDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final CampaignRepository campaignRepository;
    private final PasswordEncoder passwordEncoder;
    private final CampaignCacheInitializer cacheInitializer;

    public DemoDataInitializer(
            UserRepository userRepository,
            CategoryRepository categoryRepository,
            ProductRepository productRepository,
            ProductVariantRepository variantRepository,
            CampaignRepository campaignRepository,
            PasswordEncoder passwordEncoder,
            CampaignCacheInitializer cacheInitializer
    ) {
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.campaignRepository = campaignRepository;
        this.passwordEncoder = passwordEncoder;
        this.cacheInitializer = cacheInitializer;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedUser("demo", "Demo@123456", "USER");
        seedUser("admin", "Admin@123456", "ADMIN");

        ProductVariantEntity headphone = seedProduct(
                "数码影音",
                "FF-HEADPHONE-001",
                "FlashFlow Air 降噪耳机",
                "40mm 动圈单元，支持主动降噪、空间音频与多设备切换。",
                "/product-images/headphone.svg",
                List.of(
                        new VariantSeed("FF-HEADPHONE-001", "曜石黑", "标准版", "999.00", 500),
                        new VariantSeed("FF-HEADPHONE-002", "冰川银", "标准版", "999.00", 360),
                        new VariantSeed("FF-HEADPHONE-003", "曜石黑", "旗舰版", "1299.00", 240)));

        ProductVariantEntity watch = seedProduct(
                "智能穿戴",
                "FF-WATCH-001",
                "FlashFlow Watch Pro",
                "双频 GPS、全天候健康监测与 14 天长续航。",
                "/product-images/watch.svg",
                List.of(
                        new VariantSeed("FF-WATCH-001", "星空黑", "46mm 运动版", "1599.00", 260),
                        new VariantSeed("FF-WATCH-002", "月光银", "42mm 精致版", "1699.00", 180)));

        ProductVariantEntity coffee = seedProduct(
                "生活家电",
                "FF-COFFEE-001",
                "FlashFlow 智能咖啡机",
                "19Bar 萃取压力，支持研磨粗细与奶泡温度自定义。",
                "/product-images/coffee.svg",
                List.of(
                        new VariantSeed("FF-COFFEE-001", "深空灰", "基础款", "2299.00", 160),
                        new VariantSeed("FF-COFFEE-002", "奶油白", "奶咖套装", "2699.00", 120)));

        seedCampaign(headphone, new BigDecimal("399.00"), 100, 2);
        seedCampaign(watch, new BigDecimal("899.00"), 80, 2);
        seedCampaign(coffee, new BigDecimal("1399.00"), 60, 3);

        campaignRepository
                .findAllByStatusAndEndsAtAfterOrderByStartsAtAsc(CampaignStatus.ACTIVE, Instant.now())
                .forEach(campaign -> cacheInitializer.warm(campaign, false));
        log.info("Demo catalog ready, products={}, demoUser=demo", productRepository.count());
    }

    private ProductVariantEntity seedProduct(
            String categoryName,
            String primarySku,
            String name,
            String description,
            String imageUrl,
            List<VariantSeed> variants
    ) {
        CategoryEntity category = categoryRepository.findByName(categoryName)
                .orElseGet(() -> categoryRepository.save(new CategoryEntity(categoryName, 100)));
        ProductEntity product = productRepository.findBySkuCode(primarySku)
                .orElseGet(() -> productRepository.save(new ProductEntity(
                        category.getId(),
                        primarySku,
                        name,
                        description,
                        imageUrl,
                        new BigDecimal(variants.getFirst().price()))));
        for (VariantSeed seed : variants) {
            variantRepository.findBySkuCode(seed.skuCode()).orElseGet(() ->
                    variantRepository.save(new ProductVariantEntity(
                            product.getId(),
                            seed.skuCode(),
                            seed.color(),
                            seed.specification(),
                            new BigDecimal(seed.price()),
                            seed.stock())));
        }
        return variantRepository.findBySkuCode(primarySku)
                .orElseThrow(() -> new IllegalStateException("Seed SKU missing: " + primarySku));
    }

    private void seedCampaign(
            ProductVariantEntity variant,
            BigDecimal flashPrice,
            int totalStock,
            int purchaseLimit
    ) {
        if (campaignRepository.existsByProductIdAndStatusAndEndsAtAfter(
                variant.getProductId(),
                CampaignStatus.ACTIVE,
                Instant.now())) {
            return;
        }
        campaignRepository.save(new CampaignEntity(
                variant.getProductId(),
                variant.getId(),
                flashPrice,
                totalStock,
                purchaseLimit,
                Instant.now().minus(1, ChronoUnit.HOURS),
                Instant.now().plus(7, ChronoUnit.DAYS)));
    }

    private void seedUser(String username, String password, String role) {
        if (userRepository.findByUsername(username).isEmpty()) {
            userRepository.save(new UserEntity(username, passwordEncoder.encode(password), role));
        }
    }

    private record VariantSeed(
            String skuCode,
            String color,
            String specification,
            String price,
            int stock
    ) {
    }
}
