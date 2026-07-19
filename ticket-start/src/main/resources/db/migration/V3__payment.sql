ALTER TABLE orders
    ADD COLUMN refund_required BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE processed_webhook (
    sepay_txn_id BIGINT      NOT NULL,
    received_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (sepay_txn_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE payment_transaction (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    sepay_txn_id     BIGINT       NOT NULL,
    order_id         BIGINT       NOT NULL,
    gateway          VARCHAR(64),
    transfer_type    VARCHAR(8),
    amount           BIGINT       NOT NULL,
    content          VARCHAR(500),
    reference_code   VARCHAR(64),
    transaction_date VARCHAR(32),
    status           VARCHAR(20)  NOT NULL,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_payment_transaction_sepay_txn UNIQUE (sepay_txn_id),
    CONSTRAINT fk_payment_transaction_order FOREIGN KEY (order_id) REFERENCES orders (id),
    INDEX idx_payment_transaction_order (order_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
