INSERT INTO transactions (merchant_id, reference, amount, fee, currency, captured_amount, refunded_amount, settled_amount, status, card_number, card_holder, cvv, created_at)
VALUES ('merchant-a', 'order-1001', 49.99, 1.75, 'EUR', 0, 0, 0, 0, '4111111111111111', 'Test Holder One', '123', '2026-11-01 09:15:00'),
       ('merchant-a', 'order-1002', 0.30, 0.31, 'EUR', 0.30, 0, 0, 1, '4111111111111111', 'Test Holder One', '123', '2026-11-02 10:30:00'),
       ('merchant-a', 'order-1003', 120.00, 3.78, 'EUR', 120.00, 0, 0, 1, '5555555555554444', 'Test Holder Two', '456', '2026-11-03 14:05:00'),
       ('merchant-b', 'order-1001', 250.00, 7.55, 'EUR', 250.00, 0, 0, 1, '4000056655665556', 'Test Holder Three', '789', '2026-11-03 16:45:00'),
       ('merchant-b', 'order-2002', 15000, 435.30, 'JPY', 0, 0, 0, 0, '4000056655665556', 'Test Holder Three', '789', '2026-11-04 08:20:00');
