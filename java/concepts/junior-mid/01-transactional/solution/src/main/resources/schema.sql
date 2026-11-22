create table if not exists accounts (
    id      bigint  primary key,
    owner   text    not null,
    balance bigint  not null check (balance >= 0),
    frozen  boolean not null default false
);

create table if not exists audit_log (
    id         bigserial   primary key,
    action     text        not null,
    detail     text        not null,
    created_at timestamptz not null default now()
);

insert into accounts (id, owner, balance, frozen)
values (1, 'Alice', 100, false),
       (2, 'Bob', 0, false),
       (3, 'Carol', 0, true),
       (4, 'Acme Ltd', 1000, false)
on conflict (id) do nothing;
