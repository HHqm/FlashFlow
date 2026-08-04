CREATE DATABASE IF NOT EXISTS flashflow_notification
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

GRANT ALL PRIVILEGES ON flashflow_notification.* TO 'flashflow'@'%';
FLUSH PRIVILEGES;
