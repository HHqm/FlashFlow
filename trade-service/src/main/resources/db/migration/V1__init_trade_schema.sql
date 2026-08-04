CREATE TABLE app_user (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(128) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(32) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_app_user_username UNIQUE (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE product (
    id BIGINT NOT NULL AUTO_INCREMENT,
    sku_code VARCHAR(64) NOT NULL,
    name VARCHAR(160) NOT NULL,
    price DECIMAL(12, 2) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_product_sku_code UNIQUE (sku_code),
    CONSTRAINT ck_product_price_positive CHECK (price > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE flash_campaign (
    id BIGINT NOT NULL AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    flash_price DECIMAL(12, 2) NOT NULL,
    total_stock INT NOT NULL,
    remaining_stock INT NOT NULL,
    starts_at TIMESTAMP(6) NOT NULL,
    ends_at TIMESTAMP(6) NOT NULL,
    status VARCHAR(20) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_campaign_product FOREIGN KEY (product_id) REFERENCES product (id),
    CONSTRAINT ck_campaign_stock CHECK (
        total_stock > 0 AND remaining_stock >= 0 AND remaining_stock <= total_stock
    ),
    CONSTRAINT ck_campaign_time CHECK (ends_at > starts_at),
    INDEX idx_campaign_status_time (status, starts_at, ends_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE trade_order (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_no VARCHAR(40) NOT NULL,
    user_id BIGINT NOT NULL,
    campaign_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    amount DECIMAL(12, 2) NOT NULL,
    status VARCHAR(32) NOT NULL,
    idempotency_key VARCHAR(64) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    paid_at TIMESTAMP(6) NULL,
    cancelled_at TIMESTAMP(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_trade_order_no UNIQUE (order_no),
    CONSTRAINT uk_trade_order_idempotency UNIQUE (user_id, idempotency_key),
    CONSTRAINT fk_trade_order_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_trade_order_campaign FOREIGN KEY (campaign_id) REFERENCES flash_campaign (id),
    CONSTRAINT fk_trade_order_product FOREIGN KEY (product_id) REFERENCES product (id),
    INDEX idx_trade_order_user_created (user_id, created_at DESC),
    INDEX idx_trade_order_timeout (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE outbox_event (
    event_id BINARY(16) NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    aggregate_id VARCHAR(80) NOT NULL,
    payload LONGTEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP(6) NOT NULL,
    last_error VARCHAR(500) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    published_at TIMESTAMP(6) NULL,
    PRIMARY KEY (event_id),
    INDEX idx_outbox_dispatch (status, next_attempt_at, created_at),
    INDEX idx_outbox_aggregate (aggregate_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
