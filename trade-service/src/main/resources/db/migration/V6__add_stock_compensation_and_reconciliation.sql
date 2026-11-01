CREATE TABLE stock_compensation_task (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_no VARCHAR(40) NOT NULL,
    campaign_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    idempotency_key VARCHAR(64) NOT NULL,
    quantity INT NOT NULL,
    reason VARCHAR(32) NOT NULL,
    status VARCHAR(20) NOT NULL,
    outcome VARCHAR(20) NULL,
    retry_count INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP(6) NOT NULL,
    last_error VARCHAR(500) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    completed_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_stock_compensation_order UNIQUE (order_no),
    CONSTRAINT fk_stock_compensation_campaign FOREIGN KEY (campaign_id) REFERENCES flash_campaign (id),
    CONSTRAINT ck_stock_compensation_quantity CHECK (quantity > 0),
    INDEX idx_stock_compensation_dispatch (status, next_attempt_at),
    INDEX idx_stock_compensation_campaign (campaign_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE stock_reconciliation_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    campaign_id BIGINT NOT NULL,
    database_stock INT NOT NULL,
    redis_stock_before INT NOT NULL,
    redis_stock_after INT NOT NULL,
    drift INT NOT NULL,
    trigger_source VARCHAR(20) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_stock_reconciliation_campaign FOREIGN KEY (campaign_id) REFERENCES flash_campaign (id),
    INDEX idx_stock_reconciliation_created (created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
