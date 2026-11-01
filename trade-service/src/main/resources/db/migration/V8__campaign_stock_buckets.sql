-- Split each campaign's stock across bucket rows so several writers can commit in parallel
-- instead of queueing on one hot flash_campaign row. Remaining stock becomes the bucket sum.
CREATE TABLE campaign_stock_bucket (
    campaign_id BIGINT NOT NULL,
    bucket_no INT NOT NULL,
    allocated_stock INT NOT NULL,
    remaining_stock INT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (campaign_id, bucket_no),
    CONSTRAINT fk_stock_bucket_campaign FOREIGN KEY (campaign_id) REFERENCES flash_campaign (id),
    CONSTRAINT ck_stock_bucket_range CHECK (remaining_stock >= 0 AND remaining_stock <= allocated_stock)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Existing campaigns keep one bucket holding all of their stock.
INSERT INTO campaign_stock_bucket (campaign_id, bucket_no, allocated_stock, remaining_stock, version)
SELECT id, 0, total_stock, remaining_stock, version FROM flash_campaign;

ALTER TABLE flash_campaign
    ADD COLUMN stock_buckets INT NOT NULL DEFAULT 1 AFTER total_stock,
    DROP CHECK ck_campaign_stock;

ALTER TABLE flash_campaign
    DROP COLUMN remaining_stock,
    ADD CONSTRAINT ck_campaign_total_stock CHECK (total_stock > 0 AND stock_buckets > 0);

-- Orders remember which bucket they drew from so cancellations and refunds return it there.
ALTER TABLE trade_order
    ADD COLUMN stock_bucket INT NOT NULL DEFAULT 0 AFTER quantity;
