ALTER TABLE trade_order
    ADD COLUMN cancel_reason VARCHAR(500) NULL AFTER cancelled_at,
    ADD COLUMN refunded_at TIMESTAMP(6) NULL AFTER cancel_reason;

CREATE TABLE return_request (
    id BIGINT NOT NULL AUTO_INCREMENT,
    return_no VARCHAR(40) NOT NULL,
    order_id BIGINT NOT NULL,
    order_no VARCHAR(40) NOT NULL,
    user_id BIGINT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    status VARCHAR(24) NOT NULL,
    admin_note VARCHAR(500) NULL,
    requested_at TIMESTAMP(6) NOT NULL,
    processed_at TIMESTAMP(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_return_request_no UNIQUE (return_no),
    CONSTRAINT uk_return_request_order UNIQUE (order_id),
    CONSTRAINT fk_return_request_order FOREIGN KEY (order_id) REFERENCES trade_order (id),
    CONSTRAINT fk_return_request_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    INDEX idx_return_request_status_time (status, requested_at DESC),
    INDEX idx_return_request_user_time (user_id, requested_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE product_review (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    order_no VARCHAR(40) NOT NULL,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    variant_id BIGINT NULL,
    rating TINYINT NOT NULL,
    content VARCHAR(1000) NOT NULL,
    visible BOOLEAN NOT NULL DEFAULT TRUE,
    merchant_reply VARCHAR(500) NULL,
    replied_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_product_review_order UNIQUE (order_id),
    CONSTRAINT fk_product_review_order FOREIGN KEY (order_id) REFERENCES trade_order (id),
    CONSTRAINT fk_product_review_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_product_review_product FOREIGN KEY (product_id) REFERENCES product (id),
    CONSTRAINT fk_product_review_variant FOREIGN KEY (variant_id) REFERENCES product_variant (id),
    CONSTRAINT ck_product_review_rating CHECK (rating BETWEEN 1 AND 5),
    INDEX idx_product_review_product_visible_time (product_id, visible, created_at DESC),
    INDEX idx_product_review_user_time (user_id, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE FULLTEXT INDEX ft_product_search ON product (name, description);
