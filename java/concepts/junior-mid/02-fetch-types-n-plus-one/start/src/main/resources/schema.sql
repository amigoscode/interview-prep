create table if not exists customer (
    id    bigint primary key,
    name  text not null,
    email text not null,
    city  text not null
);

create table if not exists product (
    id    bigint primary key,
    name  text           not null,
    price numeric(10, 2) not null
);

create table if not exists orders (
    id          bigint primary key,
    customer_id bigint      not null references customer (id),
    status      text        not null,
    placed_at   timestamptz not null
);

create table if not exists order_item (
    id         bigserial primary key,
    order_id   bigint not null references orders (id),
    product_id bigint not null references product (id),
    quantity   int    not null check (quantity > 0)
);

create index if not exists order_item_order_id on order_item (order_id);
create index if not exists orders_customer_id on orders (customer_id);
