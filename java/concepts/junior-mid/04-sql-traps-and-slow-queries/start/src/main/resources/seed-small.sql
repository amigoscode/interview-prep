-- The small shop: 8 customers, 5 products, 12 orders, 18 order lines. Every row is written out by
-- hand so that you (and the tests) know exactly what every report should say.
-- Money is stored in cents: 15000 is 150.00.

insert into products (id, name, price_cents) values
    (1, 'Keyboard', 5000),
    (2, 'Mouse',    2000),
    (3, 'Monitor', 15000),
    (4, 'Cable',    1000),
    (5, 'Webcam',   4000);

insert into customers (id, name, email, country) values
    (1, 'Ada',      'ada@shop.test',      'PT'),
    (2, 'Grace',    'grace@shop.test',    'PT'),
    (3, 'Linus',    'linus@shop.test',    'PT'),
    (4, 'Margaret', 'margaret@shop.test', 'PT'),
    (5, 'Dennis',   'dennis@shop.test',   'PT'),
    (6, 'Barbara',  'barbara@shop.test',  'PT'),
    (7, 'Alan',     'alan@shop.test',     'PT'),
    (8, 'Edsger',   'edsger@shop.test',   'PT');

-- Margaret (4) and Alan (7) have no orders at all.
-- Linus (3) and Dennis (5) have orders, but none of them is PAID.
-- Order 12 is a guest checkout: its customer_id is NULL.
insert into orders (id, customer_id, status, total_cents, created_at, shipping_address, notes) values
    (1,  1,    'PAID',      15000, '2023-01-05 10:00:00', '1 Rua Augusta, Lisboa',   ''),
    (2,  1,    'PAID',       3000, '2023-01-19 16:30:00', '1 Rua Augusta, Lisboa',   ''),
    (3,  2,    'PAID',       9000, '2023-01-20 09:15:00', '7 Rua do Carmo, Lisboa',  'Leave at the front desk'),
    (4,  2,    'CANCELLED',  4000, '2023-02-01 12:00:00', '7 Rua do Carmo, Lisboa',  ''),
    (5,  3,    'PENDING',    5000, '2023-02-03 18:45:00', '3 Rua da Prata, Lisboa',  ''),
    (6,  5,    'CANCELLED', 15000, '2023-02-10 08:20:00', '9 Rua Garrett, Lisboa',   ''),
    (7,  6,    'PAID',       4000, '2023-02-11 11:00:00', '12 Rua Aurea, Lisboa',    ''),
    (8,  6,    'PAID',       4000, '2023-02-18 11:00:00', '12 Rua Aurea, Lisboa',    ''),
    (9,  6,    'PAID',       4000, '2023-02-25 11:00:00', '12 Rua Aurea, Lisboa',    'Ring twice'),
    (10, 8,    'PAID',       9000, '2023-03-02 14:10:00', '5 Rua do Ouro, Lisboa',   ''),
    (11, 8,    'PENDING',    1000, '2023-03-09 19:30:00', '5 Rua do Ouro, Lisboa',   ''),
    (12, null, 'PAID',       3000, '2023-03-10 20:00:00', '2 Rua da Madalena, Lisboa', 'Guest checkout');

-- Every order total is the sum of its lines (quantity x unit price).
insert into order_items (id, order_id, product_id, quantity, unit_price_cents) values
    (1,  1,  3, 1, 15000),
    (2,  2,  2, 1,  2000),
    (3,  2,  4, 1,  1000),
    (4,  3,  1, 1,  5000),
    (5,  3,  2, 1,  2000),
    (6,  3,  4, 2,  1000),
    (7,  4,  5, 1,  4000),
    (8,  5,  1, 1,  5000),
    (9,  6,  3, 1, 15000),
    (10, 7,  5, 1,  4000),
    (11, 8,  2, 2,  2000),
    (12, 9,  4, 2,  1000),
    (13, 9,  2, 1,  2000),
    (14, 10, 1, 1,  5000),
    (15, 10, 5, 1,  4000),
    (16, 11, 4, 1,  1000),
    (17, 12, 2, 1,  2000),
    (18, 12, 4, 1,  1000);
