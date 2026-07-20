CREATE TABLE e_tickets (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    order_id       BIGINT       NOT NULL,
    ticket_type_id BIGINT       NOT NULL,
    ticket_code    VARCHAR(40)  NOT NULL,
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_e_tickets_code UNIQUE (ticket_code),
    CONSTRAINT fk_e_tickets_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_e_tickets_ticket_type FOREIGN KEY (ticket_type_id) REFERENCES ticket_types (id),
    INDEX idx_e_tickets_order (order_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
