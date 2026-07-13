DELETE FROM order_items;
DELETE FROM orders;
DELETE FROM order_queue;
DELETE FROM outbox_event;
DELETE FROM idempotency_key;

INSERT INTO users (id, email, password_hash, role, status, created_at, updated_at)
VALUES (1, 'organizer@demo.local', '$2a$10$7EqJtq98hPqEX7fNZaFWoOa8Rn.L4hBW.uV7bkYm9v3sUq1cU3H1u', 'ORGANIZER', 'ACTIVE', NOW(6), NOW(6))
ON DUPLICATE KEY UPDATE email = VALUES(email);

INSERT INTO events (id, organizer_id, title, description, venue, city, start_time, end_time, status, banner_url, created_at, updated_at)
VALUES (1, 1, 'Neon Nights Music Festival', 'An open-air electronic music festival.', 'Riverside Park', 'Da Nang',
        TIMESTAMPADD(DAY, 30, NOW(6)), TIMESTAMPADD(DAY, 31, NOW(6)), 'PUBLISHED', NULL, NOW(6), NOW(6))
ON DUPLICATE KEY UPDATE title = VALUES(title);

INSERT INTO ticket_types (id, event_id, name, description, price, stock_initial, stock_available, sale_start_time, sale_end_time, status, created_at, updated_at)
VALUES (1, 1, 'Standard', 'General admission.', 500000, 1000, 1000,
        NOW(6), TIMESTAMPADD(DAY, 29, NOW(6)), 'ON_SALE', NOW(6), NOW(6))
ON DUPLICATE KEY UPDATE stock_initial = 1000, stock_available = 1000;
