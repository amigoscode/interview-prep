create table if not exists account (
    id      bigint primary key,
    owner   text   not null,
    name    text   not null,
    balance bigint not null
);

-- One customer, two accounts. The bank's rule: a single account may go below zero,
-- as long as the customer's accounts added together stay at zero or above.
insert into account (id, owner, name, balance)
values (1, 'alice', 'current', 500),
       (2, 'alice', 'savings', 500)
on conflict (id) do nothing;
