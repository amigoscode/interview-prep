-- The indexes of the shop database.
--
-- This file is the single source of truth. Every time the application, ./demo or ./check starts,
-- the database is made to match it: if the file changed, every index on orders and customers
-- (except the primary keys) is dropped and this file is run again.
-- So to add an index, add a statement here. To remove one, delete its statement.
--
-- One statement per index, each ending with a semicolon. Plain CREATE INDEX, nothing else.

-- These two were here before you joined.
CREATE INDEX idx_orders_created_customer ON orders (created_at, customer_id);
CREATE INDEX idx_customers_email ON customers (email);

-- TODO Task 2: GET /api/orders/track/{trackingNumber} reads every order to find one.

-- TODO Task 4: GET /api/customers/{id}/orders (the customer's 5 newest orders) walks an index
--              that is sorted by the wrong column first.

-- TODO Task 6: GET /api/customers?email=... is a Seq Scan although idx_customers_email exists.
--              (The other half of Task 6 is in ShopQueries.dailySales, not in this file.)

-- TODO Task 8: GET /api/orders/pending reads almost a million orders to return 50.
--              GET /api/customers/{id}/spend visits the table once per order.
