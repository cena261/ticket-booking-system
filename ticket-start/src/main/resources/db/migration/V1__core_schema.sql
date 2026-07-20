CREATE TABLE users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE events (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    organizer_id BIGINT       NOT NULL,
    title        VARCHAR(200)  NOT NULL,
    description  VARCHAR(2000),
    venue        VARCHAR(255),
    city         VARCHAR(100),
    start_time   DATETIME(6)  NOT NULL,
    end_time     DATETIME(6)  NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    banner_url   VARCHAR(500),
    created_at   DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_events_organizer FOREIGN KEY (organizer_id) REFERENCES users (id),
    INDEX idx_events_status (status),
    INDEX idx_events_start_time (start_time),
    INDEX idx_events_city (city)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE ticket_types (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    event_id        BIGINT       NOT NULL,
    name            VARCHAR(150)  NOT NULL,
    description     VARCHAR(2000),
    price           BIGINT       NOT NULL,
    stock_initial   INT          NOT NULL,
    stock_available INT          NOT NULL,
    sale_start_time DATETIME(6)  NOT NULL,
    sale_end_time   DATETIME(6)  NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_ticket_types_event FOREIGN KEY (event_id) REFERENCES events (id),
    CONSTRAINT ck_ticket_types_price CHECK (price >= 0),
    CONSTRAINT ck_ticket_types_stock CHECK (stock_available >= 0 AND stock_available <= stock_initial),
    INDEX idx_ticket_types_event (event_id),
    INDEX idx_ticket_types_sale_window (sale_start_time, sale_end_time),
    INDEX idx_ticket_types_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE orders (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    order_number VARCHAR(50) NOT NULL,
    user_id      BIGINT      NOT NULL,
    event_id     BIGINT      NOT NULL,
    status       VARCHAR(20) NOT NULL,
    total_amount BIGINT      NOT NULL,
    payment_ref  VARCHAR(50) NOT NULL,
    reserved_at  DATETIME(6),
    expires_at   DATETIME(6),
    paid_at      DATETIME(6),
    created_at   DATETIME(6) NOT NULL,
    updated_at   DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_orders_order_number UNIQUE (order_number),
    CONSTRAINT uk_orders_payment_ref UNIQUE (payment_ref),
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_orders_event FOREIGN KEY (event_id) REFERENCES events (id),
    INDEX idx_orders_user (user_id),
    INDEX idx_orders_status_expires (status, expires_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE order_items (
    id             BIGINT NOT NULL AUTO_INCREMENT,
    order_id       BIGINT NOT NULL,
    ticket_type_id BIGINT NOT NULL,
    unit_price     BIGINT NOT NULL,
    quantity       INT    NOT NULL,
    subtotal       BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_order_items_ticket_type FOREIGN KEY (ticket_type_id) REFERENCES ticket_types (id),
    INDEX idx_order_items_order (order_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
