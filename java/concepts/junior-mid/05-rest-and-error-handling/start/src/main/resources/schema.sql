create table if not exists orders (
    id               bigserial primary key,
    customer         text           not null,
    product          text           not null,
    quantity         int            not null,
    total            numeric(10, 2) not null,
    status           text           not null,
    shipping_address text           not null,
    notes            text,
    contact_phone    text,
    internal_note    text
);
