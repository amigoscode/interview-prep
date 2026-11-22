-- Runs every time the application starts, so a change you make here is live on the next ./demo.
drop table if exists idempotency_key;
drop table if exists payment;
drop table if exists customer;

create table customer (
    id            bigint primary key,
    name          text   not null,
    balance_cents bigint not null check (balance_cents >= 0)
);

create table payment (
    id           bigserial primary key,
    customer_id  bigint      not null references customer (id),
    amount_cents bigint      not null check (amount_cents > 0),
    created_at   timestamptz not null default now()
);

-- One row per idempotency key: "this customer already sent this request, and here is the answer".
create table idempotency_key (
    id              bigserial primary key,
    customer_id     bigint      not null,
    idempotency_key text        not null,
    request_hash    text,
    payment_id      bigint references payment (id),
    response_status int,
    response_body   text,
    created_at      timestamptz not null default now(),
    expires_at      timestamptz not null default now() + interval '24 hours',
    -- A key is unique per customer. This is what makes "insert the key first" safe: of two
    -- requests with the same key, the database lets exactly one insert its row.
    constraint uq_idempotency_key unique (customer_id, idempotency_key)
);

insert into customer (id, name, balance_cents)
values (1, 'Maria', 10000),
       (2, 'Jamal', 10000);
