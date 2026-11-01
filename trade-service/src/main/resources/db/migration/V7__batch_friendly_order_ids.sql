-- Pooled id blocks for trade_order so Hibernate can batch order inserts. The starting value
-- leaves a gap above existing ids that is larger than one allocation block.
CREATE TABLE id_generator (
    name VARCHAR(255) NOT NULL,
    next_val BIGINT NOT NULL,
    PRIMARY KEY (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO id_generator (name, next_val)
SELECT 'trade_order', COALESCE(MAX(id), 0) + 10000 FROM trade_order;
