CREATE TABLE outbox_event (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    aggregate_id VARCHAR(64) NOT NULL,
    event_type   VARCHAR(64) NOT NULL,
    payload      TEXT        NOT NULL,
    status       TINYINT     NOT NULL,
    created_at   DATETIME(6) NOT NULL,
    published_at DATETIME(6),
    PRIMARY KEY (id),
    INDEX idx_outbox_event_status_created (status, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE order_queue (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    token          VARCHAR(64) NOT NULL,
    user_id        BIGINT      NOT NULL,
    ticket_type_id BIGINT      NOT NULL,
    quantity       INT         NOT NULL,
    status         TINYINT     NOT NULL,
    order_number   VARCHAR(50),
    message        VARCHAR(255),
    created_at     DATETIME(6) NOT NULL,
    updated_at     DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_order_queue_token UNIQUE (token),
    INDEX idx_order_queue_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE idempotency_key (
    token      VARCHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    PRIMARY KEY (token),
    INDEX idx_idempotency_key_expires (expires_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
