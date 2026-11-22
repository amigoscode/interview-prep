-- The tables. You do not change this file. Indexes go in indexes.sql.

create table if not exists customers (
    id    bigint primary key,
    email text   not null,
    name  text   not null
);

-- Note: Postgres creates an index for a PRIMARY KEY, but NOT for a foreign key column.
create table if not exists orders (
    id              bigint    primary key,
    customer_id     bigint    not null references customers (id),
    status          text      not null,
    total_cents     int       not null,
    tracking_number text      not null,
    created_at      timestamp not null
);

-- Bookkeeping for DatabaseSetup: how many rows were seeded, and which indexes.sql was applied.
create table if not exists setup_state (
    key   text primary key,
    value text not null
);
