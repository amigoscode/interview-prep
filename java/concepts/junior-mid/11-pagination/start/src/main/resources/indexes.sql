-- The indexes of the orders table.
--
-- This file is the single source of truth. Every time the application, ./demo, ./inspect or
-- ./check starts, the database is made to match it: if the file changed, every index on orders
-- (except the primary key) is dropped and this file is run again.
-- So to add an index, add a statement here. To remove one, delete its statement.
--
-- One statement per index, each ending with a semicolon. Plain CREATE INDEX, nothing else.

-- These two were here before you joined.
CREATE INDEX idx_orders_created_at ON orders (created_at);
CREATE INDEX idx_orders_customer ON orders (customer_id);
