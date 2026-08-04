CREATE TABLE processed_message (
    event_id BINARY(16) NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    processed_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (event_id),
    INDEX idx_processed_message_time (processed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE notification (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_id BINARY(16) NOT NULL,
    user_id BIGINT NOT NULL,
    channel VARCHAR(20) NOT NULL,
    template_code VARCHAR(64) NOT NULL,
    content VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_notification_event UNIQUE (event_id),
    INDEX idx_notification_user_time (user_id, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
