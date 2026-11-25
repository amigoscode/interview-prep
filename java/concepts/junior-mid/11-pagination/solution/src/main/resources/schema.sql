-- The table. You do not change this file. Indexes go in indexes.sql.

create table if not exists orders (
    id          bigint      primary key,
    customer_id bigint      not null,
    status      text        not null,
    total_cents int         not null,
    created_at  timestamptz not null
);

-- Bookkeeping for DatabaseSetup: how many rows were seeded, and which indexes.sql was applied.
create table if not exists setup_state (
    key   text primary key,
    value text not null
);
