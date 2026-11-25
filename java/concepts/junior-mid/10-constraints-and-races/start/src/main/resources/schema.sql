-- This file is run every time the application starts (./demo, ./check, ./mvnw verify).
-- It drops the tables and creates them again, so the database always has exactly the
-- constraints written here. To add a constraint: edit this file and run the command again.

drop table if exists subscriptions, user_settings, users cascade;

create table users (
    id           bigserial primary key,
    email        text,
    display_name text,
    created_at   timestamptz not null default now()
    -- TODO Task 2: two rows may not have the same email.
    -- TODO Task 7: a user must have an email.
);

-- TODO Task 4: Ana@Example.com and ana@example.com are the same address.

create table subscriptions (
    id          bigserial primary key,
    user_id     bigint      not null,
    plan        text        not null,
    seats       int         not null,
    price_cents int         not null,
    status      text        not null constraint subscriptions_status_check check (status in ('ACTIVE', 'CANCELLED')),
    started_at  timestamptz not null default now()
    -- TODO Task 7: no subscription for a user that does not exist, no seats below 1, no negative price.
);

-- TODO Task 5: one ACTIVE subscription per user. Cancelled ones may repeat.

create table user_settings (
    user_id    bigint primary key references users (id),
    theme      text        not null default 'light',
    updated_at timestamptz not null default now()
);
