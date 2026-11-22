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

-- Task 2: find an order by its tracking number.
CREATE INDEX idx_orders_tracking_number ON orders (tracking_number);

-- Task 4: a customer's orders, newest first. Equality column first, then the sort column.
-- Task 8: INCLUDE (total_cents) was added, so "what did this customer spend?" is answered from
--         the index alone, without visiting the table (Index Only Scan).
CREATE INDEX idx_orders_customer_created ON orders (customer_id, created_at) INCLUDE (total_cents);

-- Task 6: the query compares lower(email), so the index must store lower(email).
CREATE INDEX idx_customers_email_lower ON customers (lower(email));

-- Task 8: only the PENDING orders are in this index, already sorted by created_at.
CREATE INDEX idx_orders_pending ON orders (created_at) WHERE status = 'PENDING';
