-- The indexes of the orders table.
--
-- This file is the single source of truth. Every time the application, ./demo, ./inspect or
-- ./check starts, the database is made to match it: if the file changed, every index on orders
-- (except the primary key) is dropped and this file is run again.
-- So to add an index, add a statement here. To remove one, delete its statement.
--
-- One statement per index, each ending with a semicolon. Plain CREATE INDEX, nothing else.

-- Was here before you joined.
CREATE INDEX idx_orders_customer ON orders (customer_id);

-- Task 5: the feed is sorted by (created_at, id), so the index is too. It replaces the old index
-- on (created_at) alone: everything that one could do, this one can do as well.
CREATE INDEX idx_orders_created_at_id ON orders (created_at, id);
