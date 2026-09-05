CREATE TABLE media (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    category_id  BIGINT       NOT NULL,
    title        VARCHAR(200) NOT NULL,
    publish_time DATETIME(3)  NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    created_at   DATETIME(3)  NOT NULL,
    updated_at   DATETIME(3)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT chk_media_status CHECK (status IN ('PUBLISHED', 'OFFLINE')),
    INDEX idx_media_category_status_publish_id
        (category_id, status, publish_time DESC, id DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
