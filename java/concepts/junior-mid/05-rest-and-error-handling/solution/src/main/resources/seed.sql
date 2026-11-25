-- The known state. ./demo, ./inspect and every test start from exactly these six orders.
truncate table orders restart identity;

insert into orders (customer, product, quantity, total, status, shipping_address, notes, contact_phone, internal_note)
values ('Maria Jones', 'MONITOR', 1, 200.00, 'PENDING', '12 Mill Lane, Leeds', 'Ring the bell twice', '0113 496 0001', 'Card was declined once in May'),
       ('Ahmed Khan', 'KEYBOARD', 2, 100.00, 'PENDING', '4 Park Row, Bristol', null, null, null),
       ('Sofia Rossi', 'MOUSE', 3, 60.00, 'PENDING', '77 High Street, York', null, null, null),
       ('Liam Brown', 'MONITOR', 2, 400.00, 'PAID', '9 Castle Road, Cardiff', 'Gift. No invoice in the box', '029 2018 0002', 'Buys for resale. Margin 12 percent'),
       ('Chen Wei', 'KEYBOARD', 1, 50.00, 'SHIPPED', '31 Dock Street, Hull', null, null, null),
       ('Olga Novak', 'MOUSE', 1, 20.00, 'CANCELLED', '2 Abbey Close, Bath', null, null, null);
