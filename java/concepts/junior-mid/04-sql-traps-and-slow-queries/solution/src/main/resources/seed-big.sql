-- The big shop: the small shop plus 20,000 customers, 300,000 orders and 600,000 order lines.
-- The rows are made inside Postgres with generate_series, which takes seconds. Every value is a
-- formula of the row number, so the data is the same on every machine. All ids start at 1001.

insert into products (id, name, price_cents)
select p, 'Product ' || p, 500 + p * 100
  from generate_series(6, 50) as p;

-- 50 of the 20,000 customers are in Ireland (IE): the ones whose id is a multiple of 400.
insert into customers (id, name, email, country)
select id,
       'Customer ' || id,
       'customer' || id || '@shop.test',
       case when id % 400 = 0 then 'IE'
            else (array['GB', 'US', 'DE', 'FR', 'ES', 'IT', 'NL'])[1 + id % 7] end
  from generate_series(1001, 21000) as id;

-- Every customer gets exactly 15 orders. About 60% are PAID. Exactly 90,000 (30%) are EXPIRED:
-- the customer never paid and the order timed out. The small shop has no EXPIRED order at all.
-- Customers whose id is a multiple of 2000 never paid for anything.
-- ("wheel" makes the status change from one order of a customer to the next.)
-- created_at is different for every order and is not in id order, like a table that is updated.
insert into orders (id, customer_id, status, total_cents, created_at, shipping_address, notes)
select 1000 + i,
       c.customer_id,
       case when c.wheel % 10 < 3 then 'EXPIRED'
            when c.customer_id % 2000 = 0 then 'CANCELLED'
            when c.wheel % 20 = 5 then 'CANCELLED'
            when c.wheel % 20 = 6 then 'PENDING'
            else 'PAID' end,
       (1 + i % 3) * (500 + (6 + i % 45) * 100) + (1 + (i + 1) % 3) * (500 + (6 + (i + 7) % 45) * 100),
       timestamp '2024-01-01 00:00:00' + ((i * 7919) % 300000) * interval '2 minutes',
       (1 + i % 900) || ' Example Street, Flat ' || (1 + i % 40) || ', Dublin, D' || lpad((1 + i % 24)::text, 2, '0'),
       'Leave with a neighbour if nobody is home. Ring the bell twice, the first one is broken. '
           || 'Gift wrap if possible. Reference ' || md5(i::text)
  from generate_series(1::bigint, 300000) as i
 cross join lateral (select 1001 + (i * 7919) % 20000 as customer_id,
                            i + (i - 1) / 20000 as wheel) as c;

-- Two lines per order. The order total above is the sum of these two lines.
insert into order_items (id, order_id, product_id, quantity, unit_price_cents)
select 1000 + (i - 1) * 2 + k + 1,
       1000 + i,
       6 + (i + k * 7) % 45,
       1 + (i + k) % 3,
       500 + (6 + (i + k * 7) % 45) * 100
  from generate_series(1::bigint, 300000) as i
 cross join generate_series(0, 1) as k;
