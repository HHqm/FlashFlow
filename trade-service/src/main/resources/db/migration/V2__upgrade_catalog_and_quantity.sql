CREATE TABLE product_category (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(80) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_product_category_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO product_category (name, sort_order, active, created_at, updated_at)
VALUES
    ('数码影音', 10, TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    ('智能穿戴', 20, TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    ('生活家电', 30, TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6));

ALTER TABLE product
    ADD COLUMN category_id BIGINT NULL AFTER id,
    ADD COLUMN description VARCHAR(500) NOT NULL DEFAULT '' AFTER name,
    ADD COLUMN image_url VARCHAR(300) NOT NULL DEFAULT '' AFTER description,
    ADD COLUMN updated_at TIMESTAMP(6) NULL AFTER created_at;

UPDATE product
SET category_id = (SELECT id FROM product_category WHERE name = '数码影音'),
    description = '40mm 动圈单元，支持主动降噪与多设备切换',
    image_url = '/product-images/headphone.svg',
    updated_at = created_at;

ALTER TABLE product
    MODIFY COLUMN updated_at TIMESTAMP(6) NOT NULL,
    ADD CONSTRAINT fk_product_category
        FOREIGN KEY (category_id) REFERENCES product_category (id);

CREATE TABLE product_variant (
    id BIGINT NOT NULL AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    sku_code VARCHAR(64) NOT NULL,
    color_name VARCHAR(40) NOT NULL,
    specification VARCHAR(120) NOT NULL,
    price DECIMAL(12, 2) NOT NULL,
    stock INT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_product_variant_sku_code UNIQUE (sku_code),
    CONSTRAINT fk_variant_product FOREIGN KEY (product_id) REFERENCES product (id),
    CONSTRAINT ck_variant_price_positive CHECK (price > 0),
    CONSTRAINT ck_variant_stock_non_negative CHECK (stock >= 0),
    INDEX idx_variant_product_active (product_id, active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO product_variant (
    product_id, sku_code, color_name, specification, price, stock, active, created_at, updated_at
)
SELECT id, sku_code, '曜石黑', '标准版', price, 500, active, created_at, updated_at
FROM product;

ALTER TABLE flash_campaign
    ADD COLUMN variant_id BIGINT NULL AFTER product_id,
    ADD COLUMN purchase_limit INT NOT NULL DEFAULT 1 AFTER remaining_stock;

UPDATE flash_campaign campaign
JOIN product_variant variant ON variant.product_id = campaign.product_id
SET campaign.variant_id = variant.id;

ALTER TABLE flash_campaign
    ADD CONSTRAINT fk_campaign_variant
        FOREIGN KEY (variant_id) REFERENCES product_variant (id),
    ADD CONSTRAINT ck_campaign_purchase_limit
        CHECK (purchase_limit > 0 AND purchase_limit <= total_stock);

ALTER TABLE trade_order
    ADD COLUMN variant_id BIGINT NULL AFTER product_id,
    ADD COLUMN quantity INT NOT NULL DEFAULT 1 AFTER amount,
    ADD COLUMN product_name VARCHAR(160) NOT NULL DEFAULT '' AFTER quantity,
    ADD COLUMN variant_label VARCHAR(180) NOT NULL DEFAULT '' AFTER product_name;

UPDATE trade_order trade
JOIN product product ON product.id = trade.product_id
LEFT JOIN flash_campaign campaign ON campaign.id = trade.campaign_id
LEFT JOIN product_variant variant ON variant.id = campaign.variant_id
SET trade.variant_id = variant.id,
    trade.product_name = product.name,
    trade.variant_label = CONCAT_WS(' · ', variant.color_name, variant.specification);

ALTER TABLE trade_order
    ADD CONSTRAINT fk_trade_order_variant
        FOREIGN KEY (variant_id) REFERENCES product_variant (id),
    ADD CONSTRAINT ck_trade_order_quantity_positive CHECK (quantity > 0);
