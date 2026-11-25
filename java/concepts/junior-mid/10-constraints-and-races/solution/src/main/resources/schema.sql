-- This file is run every time the application starts (./demo, ./check, ./mvnw verify).
-- It drops the tables and creates them again, so the database always has exactly the
-- constraints written here. To add a constraint: edit this file and run the command again.

drop table if exists subscriptions, user_settings, users cascade;

create table users (
    id           bigserial primary key,
    -- NOT NULL: a UNIQUE rule lets any number of rows have NULL in the column.
    email        text        not null,
    display_name text,
    created_at   timestamptz not null default now(),
    -- Two rows may not have the same email. The name is what the application looks for.
    constraint users_email_key unique (email)
);

-- Ana@Example.com and ana@example.com are the same address. A UNIQUE constraint compares the
-- text as it is, so this rule needs a unique INDEX on an expression.
-- (It makes users_email_key above redundant. It is kept so that ./inspect shows both.)
create unique index users_email_lower_key on users (lower(email));

create table subscriptions (
    id          bigserial primary key,
    -- FOREIGN KEY: no subscription for a user that does not exist.
    user_id     bigint      not null constraint subscriptions_user_id_fkey references users (id),
    plan        text        not null,
    -- CHECK: the values the application must never store.
    seats       int         not null constraint subscriptions_seats_check check (seats > 0),
    price_cents int         not null constraint subscriptions_price_cents_check check (price_cents >= 0),
    status      text        not null constraint subscriptions_status_check check (status in ('ACTIVE', 'CANCELLED')),
    started_at  timestamptz not null default now()
);

-- One ACTIVE subscription per user. The WHERE makes it a partial index: only ACTIVE rows are
-- in it, so a user can have any number of CANCELLED rows.
create unique index subscriptions_one_active_per_user on subscriptions (user_id) where status = 'ACTIVE';

create table user_settings (
    user_id    bigint primary key references users (id),
    theme      text        not null default 'light',
    updated_at timestamptz not null default now()
);
