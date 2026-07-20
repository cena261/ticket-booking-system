SET @orders = 100;
SET @amount = 500000;

DELETE FROM payment_transaction;
DELETE FROM processed_webhook;
DELETE FROM order_items;
DELETE FROM orders;

INSERT INTO users (id, email, password_hash, role, status, created_at, updated_at)
VALUES (1, 'organizer@demo.local', '$2a$10$7EqJtq98hPqEX7fNZaFWoOa8Rn.L4hBW.uV7bkYm9v3sUq1cU3H1u', 'ORGANIZER', 'ACTIVE', NOW(6), NOW(6))
ON DUPLICATE KEY UPDATE email = VALUES(email);

INSERT INTO events (id, organizer_id, title, description, venue, city, start_time, end_time, status, banner_url, created_at, updated_at)
VALUES (1, 1, 'Neon Nights Music Festival', 'An open-air electronic music festival.', 'Riverside Park', 'Da Nang',
        TIMESTAMPADD(DAY, 30, NOW(6)), TIMESTAMPADD(DAY, 31, NOW(6)), 'PUBLISHED', NULL, NOW(6), NOW(6))
ON DUPLICATE KEY UPDATE title = VALUES(title);

INSERT INTO ticket_types (id, event_id, name, description, price, stock_initial, stock_available, sale_start_time, sale_end_time, status, created_at, updated_at)
VALUES (1, 1, 'Standard', 'General admission.', 500000, 1000, 900,
        NOW(6), TIMESTAMPADD(DAY, 29, NOW(6)), 'ON_SALE', NOW(6), NOW(6))
ON DUPLICATE KEY UPDATE price = 500000;

INSERT INTO orders (order_number, user_id, event_id, status, total_amount, payment_ref, reserved_at, expires_at, created_at, updated_at)
WITH RECURSIVE seq (n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < @orders
)
SELECT CONCAT('ORD-STORM', LPAD(n, 7, '0')),
       1, 1, 'PENDING', @amount,
       CONCAT('TKTSTORM', LPAD(n, 7, '0')),
       NOW(6), TIMESTAMPADD(MINUTE, 30, NOW(6)), NOW(6), NOW(6)
FROM seq;

INSERT INTO order_items (order_id, ticket_type_id, unit_price, quantity, subtotal)
SELECT o.id, 1, @amount, 1, @amount
FROM orders o
WHERE o.payment_ref LIKE 'TKTSTORM%';
