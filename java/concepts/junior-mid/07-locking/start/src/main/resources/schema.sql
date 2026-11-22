create table if not exists product (
    id      bigint primary key,
    name    text   not null,
    stock   int    not null,
    version bigint not null default 0
);

create table if not exists orders (
    id         bigserial primary key,
    product_id bigint      not null references product (id),
    quantity   int         not null check (quantity > 0),
    created_at timestamptz not null default now()
);

insert into product (id, name, stock)
values (1, 'PS5', 1)
on conflict (id) do nothing;
