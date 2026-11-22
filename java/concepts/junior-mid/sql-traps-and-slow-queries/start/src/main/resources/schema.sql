-- The tables of the shop. You do not change this file.
--
-- Two things are unusual, and both are there so that the numbers in the guide match yours:
--
-- 1. autovacuum_enabled = false. Normally a background worker (autovacuum) runs ANALYZE for you
--    when enough rows have changed. Here it is switched off, so statistics are only gathered when
--    ./demo or a test says so. Never do this in production.
-- 2. SET STATISTICS 2000 on orders.status. ANALYZE normally looks at a random sample of 30,000
--    rows. With this setting it reads every row of orders, so the planner's estimates are the
--    same on every run.

create table if not exists products (
    id          bigint primary key,
    name        text   not null,
    price_cents int    not null
) with (autovacuum_enabled = false);

create table if not exists customers (
    id      bigint primary key,
    name    text   not null,
    email   text   not null,
    country text   not null
) with (autovacuum_enabled = false);

-- customer_id can be NULL: a guest checkout has no customer account.
create table if not exists orders (
    id               bigint    primary key,
    customer_id      bigint    references customers (id),
    status           text      not null,
    total_cents      int       not null,
    created_at       timestamp not null,
    shipping_address text      not null,
    notes            text      not null
) with (autovacuum_enabled = false);

create table if not exists order_items (
    id               bigint primary key,
    order_id         bigint not null references orders (id),
    product_id       bigint not null references products (id),
    quantity         int    not null,
    unit_price_cents int    not null
) with (autovacuum_enabled = false);

alter table orders alter column status set statistics 2000;

-- The only index besides the primary keys. Indexes have their own project (../indexes):
-- do not add any here.
create index if not exists idx_orders_created_at on orders (created_at);

-- Bookkeeping for DatabaseSetup: which data set is loaded (small or big).
create table if not exists setup_state (
    key   text primary key,
    value text not null
);
