CREATE TABLE shipping_address (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    recipient_name VARCHAR(80) NOT NULL,
    phone VARCHAR(32) NOT NULL,
    province VARCHAR(80) NOT NULL,
    city VARCHAR(80) NOT NULL,
    district VARCHAR(80) NOT NULL,
    detail_address VARCHAR(240) NOT NULL,
    postal_code VARCHAR(20) NULL,
    default_address BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_shipping_address_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    INDEX idx_shipping_address_user_default (user_id, default_address, updated_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE trade_order
    ADD COLUMN recipient_name VARCHAR(80) NULL AFTER idempotency_key,
    ADD COLUMN recipient_phone VARCHAR(32) NULL AFTER recipient_name,
    ADD COLUMN shipping_address VARCHAR(500) NULL AFTER recipient_phone,
    ADD COLUMN carrier VARCHAR(80) NULL AFTER shipping_address,
    ADD COLUMN tracking_no VARCHAR(80) NULL AFTER carrier,
    ADD COLUMN shipped_at TIMESTAMP(6) NULL AFTER tracking_no,
    ADD COLUMN received_at TIMESTAMP(6) NULL AFTER shipped_at,
    ADD COLUMN refund_source_status VARCHAR(32) NULL AFTER received_at;

UPDATE trade_order
SET recipient_name = '历史订单用户',
    recipient_phone = '未填写',
    shipping_address = '历史订单未保存收货地址'
WHERE recipient_name IS NULL;

ALTER TABLE trade_order
    MODIFY recipient_name VARCHAR(80) NOT NULL,
    MODIFY recipient_phone VARCHAR(32) NOT NULL,
    MODIFY shipping_address VARCHAR(500) NOT NULL;

CREATE INDEX idx_trade_order_fulfillment ON trade_order (status, paid_at, shipped_at);
