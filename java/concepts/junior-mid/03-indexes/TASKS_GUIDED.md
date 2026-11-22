# Guided Tasks — Indexes

The same ten tasks as [`INTERVIEW_TASKS.md`](INTERVIEW_TASKS.md), step by step: the command to run,
the output you should see, why it happens, and the change to make.

Commands are for macOS and Linux. On Windows type `.\demo`, `.\inspect` and `.\check`.

All output below was copied from real runs. **Timings differ on every machine and on every run.**
The scan type, the row counts and the `RESULT` line should match. Page counts and sizes can be off
by a few.

**Before you start**

```bash
cd start
docker compose up -d
```

**Two things to know about the tools**

- You add an index by adding a `CREATE INDEX` line to `src/main/resources/indexes.sql`. The next
  `./demo` or `./check` sees that the file changed and rebuilds the indexes. You will see
  `indexes.sql changed: rebuilding 3 indexes...` when that happens.
- The demo switches parallel query off, so a sequential scan is one plain `Seq Scan` line. In
  production the same plan often shows as `Gather` over `Parallel Seq Scan`. It is the same thing
  done by several workers.

---

## Task 1: See the Sequential Scan

No code in this task. You are collecting evidence.

### Run it

```bash
./demo
```

The very first run prints `Seeding 1,000,000 orders for 100,000 customers (first run only)...`. It
takes between 10 and 30 seconds.

### What you should see

```
Query: track     GET /api/orders/track/{trackingNumber}
Find one order by its tracking number.

    SELECT id, customer_id, status, total_cents, created_at
      FROM orders
     WHERE tracking_number = ?
    -- ? = 'TRKF63F4FBC9F8C'

Table orders: 1,000,000 rows, 80.5 MB.
Indexes on orders:
    orders_pkey                    (id)
    idx_orders_created_customer    (created_at, customer_id)

EXPLAIN ANALYZE
    Seq Scan on orders  (cost=0.00..22810.00 rows=1 width=37) (actual time=148.260..165.245 rows=1 loops=1)
      Filter: (tracking_number = 'TRKF63F4FBC9F8C'::text)
      Rows Removed by Filter: 999999
    Planning Time: 0.131 ms
    Execution Time: 165.264 ms

How the rows were found:    Seq Scan on orders
Rows returned:              1
Rows read:                  1,000,000
Rows read and thrown away:  999,999
Pages read (8 kB each):     10,310
Time in the database:       165.264 ms

RESULT: SEQ SCAN. Postgres read 1,000,000 rows to return 1.
```

One row was wanted. One million were read.

### How to read EXPLAIN ANALYZE

`EXPLAIN` asks Postgres for its plan. `EXPLAIN ANALYZE` also **runs** the query and reports what
really happened. Take the first line apart:

```
Seq Scan on orders  (cost=0.00..22810.00 rows=1 width=37) (actual time=148.260..165.245 rows=1 loops=1)
```

| Part | Meaning |
|---|---|
| `Seq Scan on orders` | How the rows are found. A sequential scan reads the table from the first page to the last |
| `cost=0.00..22810.00` | The planner's **estimate**, in its own units (not milliseconds). First number: cost before the first row comes out. Second: cost to finish |
| `rows=1` (first bracket) | How many rows the planner **expected** |
| `actual time=148.260..165.245` | Real milliseconds: until the first row, and until the last |
| `rows=1` (second bracket) | How many rows really came out |
| `Filter: (tracking_number = ...)` | A condition tested on every row **after** reading it |
| `Rows Removed by Filter: 999999` | Rows that were read, tested and thrown away |

The line to look for is `Rows Removed by Filter`. A big number there means the database did a lot
of work for nothing.

### Pages

```bash
./demo --buffers
```

```
    Seq Scan on orders  (cost=0.00..22810.00 rows=1 width=37) (actual time=57.820..74.185 rows=1 loops=1)
      Filter: (tracking_number = 'TRKF63F4FBC9F8C'::text)
      Rows Removed by Filter: 999999
      Buffers: shared hit=10310
```

Postgres stores a table in pages of 8 kB. `Buffers: shared hit=10310` means it touched 10,310
pages: 10,310 x 8 kB = 80.5 MB. That is the whole table. (`hit` means the page was already in
memory. `read` would mean it came from disk, which is much slower.)

Pages are a better measure than milliseconds. Time changes with the machine and the load. The
number of pages does not.

### What the database has

```bash
./inspect
```

```
TABLES (rows = the planner's estimate, refreshed by ANALYZE)
  table      rows       table_size  all_indexes_size  indexes  pages_all_visible
  ---------  ---------  ----------  ----------------  -------  -----------------
  orders     1,000,000  81 MB       52 MB             2        10310 of 10310
  customers  100,000    7480 kB     6200 kB           2        935 of 935

INDEXES (scans = how many times a query used the index since it was built)
  table      index                        columns                    size     scans    kind
  ---------  ---------------------------  -------------------------  -------  -------  -----------
  orders     orders_pkey                  (id)                       21 MB    17       primary key
  orders     idx_orders_created_customer  (created_at, customer_id)  30 MB    0
  customers  customers_pkey               (id)                       2208 kB  2000000  primary key
  customers  idx_customers_email          (email)                    3992 kB  0
```

`orders` has two indexes: the primary key on `id`, and one on `(created_at, customer_id)`. Nothing
on `tracking_number`. So Postgres has exactly one way to find a tracking number: look at every row.

(Your `scans` numbers will differ. `customers_pkey` is high because seeding the orders checked the
foreign key once per order.)

### What just happened

The query in `ShopQueries.trackOrder` is fine. It is the simplest query there is. The problem is
that the table is a heap: rows are stored in the order they arrived, not sorted by tracking number.
Without an index the database cannot know where a tracking number is, so it checks them all.

That is why this gets slower every month: the work grows with the table. And it is why nobody
noticed in development: with a thousand rows, a sequential scan takes a fraction of a millisecond.

### Check

```bash
./check 1
```

```
Task 1: See the sequential scan
  [PASS] No index: finding one order by its tracking number is a Seq Scan that reads all 200,000 rows (this documents the problem)
  1 of 1 pass
```

This test passes on purpose. It pins the problem down so you can see what an index changes. (The
tests use a database of 200,000 orders, started by Testcontainers. The demo uses one million.)

---

## Task 2: Fix It With a B-tree Index

**The idea:** give the database a sorted copy of the tracking numbers, each with a pointer to its
row. In a sorted structure you can search instead of scan.

### Before

```bash
./check 2
```

```
Task 2: Fix it with a B-tree index
  [FAIL] B-tree: indexes.sql creates an index on orders (tracking_number)
           [indexes on orders. Add CREATE INDEX ... ON orders (tracking_number) to indexes.sql]
           Expecting any elements of:
           ["(id)", "(created_at, customer_id)"]
           to match given predicate but none did.
  [FAIL] B-tree: finding an order by its tracking number is an Index Scan that reads 1 row, not a Seq Scan
           [plan still has a Seq Scan: Seq Scan on orders]
           Expecting value to be false but was true
  0 of 2 pass
```

### The change

Add one line to `src/main/resources/indexes.sql`:

```sql
CREATE INDEX idx_orders_tracking_number ON orders (tracking_number);
```

`CREATE INDEX` makes a B-tree unless you ask for something else. The name is yours to choose;
`idx_<table>_<columns>` is a common convention.

### After

```bash
./demo
```

```
indexes.sql changed: rebuilding 3 indexes...
Indexes ready in 3.2 s.

...
Indexes on orders:
    orders_pkey                    (id)
    idx_orders_created_customer    (created_at, customer_id)
    idx_orders_tracking_number     (tracking_number)

EXPLAIN ANALYZE
    Index Scan using idx_orders_tracking_number on orders  (cost=0.42..8.44 rows=1 width=37) (actual time=0.029..0.031 rows=1 loops=1)
      Index Cond: (tracking_number = 'TRKF63F4FBC9F8C'::text)
    Planning Time: 0.058 ms
    Execution Time: 0.058 ms

How the rows were found:    Index Scan using idx_orders_tracking_number on orders
Rows returned:              1
Rows read:                  1
Rows read and thrown away:  0
Pages read (8 kB each):     4
Time in the database:       0.058 ms

RESULT: OK. Index Scan using idx_orders_tracking_number on orders. Postgres read 1 row to return 1.
```

`Seq Scan` became `Index Scan`. One row read instead of a million. 4 pages instead of 10,310.

```bash
./check 2
```

```
Task 2: Fix it with a B-tree index
  [PASS] B-tree: indexes.sql creates an index on orders (tracking_number)
  [PASS] B-tree: finding an order by its tracking number is an Index Scan that reads 1 row, not a Seq Scan
  2 of 2 pass
```

---

## Task 3: Inspect the B-tree Index

### Step 1 and 2: 4 pages, and Index Cond

```bash
./demo --buffers
```

```
    Index Scan using idx_orders_tracking_number on orders  (cost=0.42..8.44 rows=1 width=37) (actual time=0.052..0.053 rows=1 loops=1)
      Index Cond: (tracking_number = 'TRKF63F4FBC9F8C'::text)
      Buffers: shared hit=4
```

A B-tree is a sorted tree of pages:

```
                    [ root page ]                 "TRKA... go left, TRKM... go right"
                   /             \
        [ inner page ]      [ inner page ]        narrower ranges
         /    |    \          /    |    \
     [leaf] [leaf] [leaf] [leaf] [leaf] [leaf]    sorted values, each with the location of its row
```

To find one value Postgres reads the root, one inner page and one leaf: 3 pages. The leaf says
where the row is in the table. Reading that table page is the 4th. That is the whole lookup.

`Filter` and `Index Cond` look alike and are opposites:

- `Filter`: read the row first, then test it. Rows that fail were read for nothing.
- `Index Cond`: the condition is answered by walking the tree. Rows that do not match are never
  touched.

### Step 3: what the planner compares

```bash
./demo --no-indexes --buffers
```

```
--no-indexes: for this run only, every index except the primary keys is hidden.
...
    Seq Scan on orders  (cost=0.00..22810.00 rows=1 width=37) (actual time=50.918..66.623 rows=1 loops=1)
      Filter: (tracking_number = 'TRKF63F4FBC9F8C'::text)
      Rows Removed by Filter: 999999
      Buffers: shared hit=10310
```

| Plan | Estimated cost | Pages |
|---|---|---|
| Seq Scan | 22810.00 | 10,310 |
| Index Scan | 8.44 | 4 |

For every query the planner lists the ways it could run it, estimates a cost for each, and picks
the cheapest. It does not "prefer indexes". It prefers the lowest number. Here the index wins by a
factor of 2,700. In Task 10 you will see a case where the index loses.

(`--no-indexes` drops the indexes inside a transaction and rolls it back. In Postgres even
`DROP INDEX` can be rolled back. Nothing is rebuilt.)

### Step 4: what it costs

```bash
./inspect
```

```
  table      rows       table_size  all_indexes_size  indexes  pages_all_visible
  orders     1,000,000  81 MB       82 MB             3        10310 of 10310

  table      index                        columns                    size     scans    kind
  orders     orders_pkey                  (id)                       21 MB    23       primary key
  orders     idx_orders_created_customer  (created_at, customer_id)  30 MB    0
  orders     idx_orders_tracking_number   (tracking_number)          30 MB    6
```

The new index is 30 MB, for an 81 MB table. With three indexes, the indexes together (82 MB) are
already bigger than the table. An index is a second copy of the column, kept sorted. You pay in
disk, in memory, and on every write (Task 10).

### Step 5: scans

`scans` goes up each time a query uses the index. In production this is how you find indexes that
nobody uses: an index with `scans = 0` after a month of real traffic costs you writes and disk and
gives nothing back. (Here the counters restart whenever `indexes.sql` changes, because the indexes
are rebuilt.)

### Step 6: 100 times more rows

About 5 pages instead of 4. Each page of the tree points to hundreds of pages below it, so one more
level multiplies the capacity by hundreds. The depth grows with the logarithm of the row count. A
sequential scan would read 100 times more pages. That difference is the whole point of an index.

### What to take away

- A B-tree is a sorted tree. A lookup reads one page per level, 3 or 4 for millions of rows.
- Because it is sorted, it serves `=`, `<`, `>`, `BETWEEN`, `ORDER BY` and `LIKE 'abc%'` (with a
  footnote, see Task 7). It cannot serve `LIKE '%abc'`.
- `Index Cond` good, `Filter` with many rows removed bad.
- The planner picks the plan with the lowest estimated cost.

---

## Task 4: Fix It With a Composite Index

**The idea:** when a query filters on one column and sorts on another, one index on both columns
can do both jobs. But only if the columns are in the right order.

### Before

```bash
./demo --query latest-orders
```

```
    SELECT id, status, total_cents, created_at
      FROM orders
     WHERE customer_id = ?
     ORDER BY created_at DESC
     LIMIT 5
    -- ? = 4242 (Long)

EXPLAIN ANALYZE
    Limit  (cost=0.42..11456.35 rows=5 width=29) (actual time=0.407..7.845 rows=5 loops=1)
      ->  Index Scan Backward using idx_orders_created_customer on orders  (cost=0.42..22912.27 rows=10 width=29) (actual time=0.406..7.842 rows=5 loops=1)
            Index Cond: (customer_id = '4242'::bigint)
    Planning Time: 0.119 ms
    Execution Time: 7.919 ms

How the rows were found:    Index Scan using idx_orders_created_customer on orders
Rows returned:              5
Rows read:                  5
Rows read and thrown away:  0
Pages read (8 kB each):     1,607
Time in the database:       7.919 ms

RESULT: INDEX USED, BUT WALKED FROM THE END, NOT SEARCHED. Postgres read 1,607 pages to find 5 rows.
```

This one is sneaky. It says `Index Scan`. It says `Index Cond`. It read 5 rows to return 5. It
looks healthy. The giveaway is the last number: **1,607 pages for 5 rows**. Task 2 needed 4.

A plan is read from the inside out: the indented `Index Scan` runs first and feeds the `Limit`
above it.

### What it is doing

`idx_orders_created_customer` is sorted by `created_at` first. So all the orders of customer 4242
are scattered across the whole index, one here in January, one there in March. Postgres cannot jump
to "customer 4242". What it can do is start at the newest end and walk backward through every index
entry, newest order first, checking each one: is this customer 4242? No. No. No. It stops when it
has found 5. Customer 4242 has 10 orders in two years, so it walks about half the index.

### The change

Add to `indexes.sql`:

```sql
CREATE INDEX idx_orders_customer_created ON orders (customer_id, created_at);
```

Keep `idx_orders_created_customer`. Task 6 needs it.

### After

```bash
./demo --query latest-orders
```

```
EXPLAIN ANALYZE
    Limit  (cost=0.42..22.51 rows=5 width=29) (actual time=0.069..0.077 rows=5 loops=1)
      ->  Index Scan Backward using idx_orders_customer_created on orders  (cost=0.42..44.60 rows=10 width=29) (actual time=0.068..0.075 rows=5 loops=1)
            Index Cond: (customer_id = '4242'::bigint)
    Execution Time: 0.127 ms

How the rows were found:    Index Scan using idx_orders_customer_created on orders
Rows returned:              5
Rows read:                  5
Rows read and thrown away:  0
Pages read (8 kB each):     8
Time in the database:       0.127 ms

RESULT: OK. Index Scan using idx_orders_customer_created on orders. Postgres read 5 rows to return 5.
```

8 pages instead of 1,607. The same two columns, in the other order.

```bash
./check 4
```

```
Task 4: Fix it with a composite index
  [PASS] Composite: the rows come out of the index already newest first: 5 rows read, no Sort step
  [PASS] Composite: a customer's 5 newest orders are found by reading fewer than 100 pages, not by walking an index from the end
  [PASS] Composite: indexes.sql creates an index on orders (customer_id, created_at), in that column order
  3 of 3 pass
```

---

## Task 5: Inspect the Composite Index

### Step 1: Backward, and no Sort

`(customer_id, created_at)` is sorted by customer, and **within one customer** by date. So all of
customer 4242's entries sit next to each other, oldest to newest. Postgres jumps to the end of that
group and reads backward: newest first, which is exactly `ORDER BY created_at DESC`. It stops after
5. No `Sort` step is needed because the index already has the rows in order.

That is why you do not need to write `created_at DESC` in the index: a B-tree can be read in both
directions.

### Step 2 and 3: the phone book

A phone book is an index on `(last_name, first_name)`.

| You want | Can you search? | Why |
|---|---|---|
| Everyone called Smith | Yes | The book is sorted by last name |
| Anna Smith | Yes | Go to Smith, then Anna within Smith |
| Everyone called Anna | No | Annas are on every page. You read the whole book |

Now map it:

| Query | Index `(customer_id, created_at)` | Index `(created_at, customer_id)` |
|---|---|---|
| `customer_id = ?` | search | read everything |
| `customer_id = ? ORDER BY created_at` | search, already sorted | read everything |
| `created_at >= ? AND created_at < ?` | read everything | search |

This is the **leftmost-prefix rule**: an index on `(a, b)` can be searched by `a`, or by `a` and
`b`. It cannot be searched by `b` alone.

Both plans showed `Index Cond: (customer_id = ...)`. In the old index that condition was on the
second column, so Postgres could only check it entry by entry while walking. In the new one it is
on the first column, so Postgres can descend the tree straight to it. Same text in the plan, very
different work. Look at the pages.

### Step 4: one column of two

```bash
./demo --query spend --customer 4242
```

```
    Aggregate  (cost=43.74..43.75 rows=1 width=16) (actual time=0.059..0.059 rows=1 loops=1)
      ->  Bitmap Heap Scan on orders  (cost=4.50..43.69 rows=10 width=4) (actual time=0.035..0.054 rows=10 loops=1)
            Recheck Cond: (customer_id = '4242'::bigint)
            Heap Blocks: exact=10
            ->  Bitmap Index Scan on idx_orders_customer_created  (cost=0.00..4.50 rows=10 width=0) (actual time=0.028..0.029 rows=10 loops=1)
                  Index Cond: (customer_id = '4242'::bigint)

Pages read (8 kB each):     13
```

The query filters on `customer_id` only, and it uses the composite index: `customer_id` is its
leftmost column. You do not need a separate index on `(customer_id)`.

(A `Bitmap Index Scan` plus `Bitmap Heap Scan` is an index scan in two steps: first collect the
locations of all matching rows from the index, then fetch them from the table in physical order.
Postgres chooses it when it expects more than a handful of rows. The `RESULT` line complains that
the table is still visited. That is Task 8.)

Before Task 4 the same query was a `Seq Scan` reading 1,000,000 rows: the only index containing
`customer_id` had it in second place.

### Step 5: the other index

`./demo --query daily-sales` filters on `created_at` only, so the index that could serve it is
`idx_orders_created_customer`, the one that was useless for Task 4. An index is not "right" or
"wrong". It fits some queries and not others. (It is still a Seq Scan, for a different reason. Next
task.)

### Step 6: why customer 1 was fast with the wrong index

Before Task 4:

```bash
./demo --query latest-orders --customer 1
```

```
    Limit  (cost=0.42..123.32 rows=5 width=29) (actual time=0.043..0.140 rows=5 loops=1)
      ->  Index Scan Backward using idx_orders_created_customer on orders  (cost=0.42..24579.80 rows=1000 width=29) (actual time=0.043..0.138 rows=5 loops=1)
            Index Cond: (customer_id = '1'::bigint)

Pages read (8 kB each):     27
RESULT: OK. Index Scan using idx_orders_created_customer on orders. Postgres read 5 rows to return 5.
```

27 pages, against 1,607 for customer 4242. Customer 1 is a big account with 1,000 orders, one in
every thousand. Walking backward from the newest order, Postgres meets 5 of theirs almost at once.
Customer 4242's fifth-newest order is a year back.

This is how these problems hide. A developer tests with their favourite customer, which is usually
a big, active one, and the page is fast. For most customers it is not. Same query, same index,
different data.

### Step 7: an index on (customer_id) alone

It would find the customer's rows by searching, so it is far better than the walk. But the rows
come out in no useful order, so the plan gets a `Sort` step: fetch **all** the customer's orders,
sort them, keep 5. Fine for a customer with 10 orders, wasteful for one with 100,000. With
`created_at` as the second column, Postgres reads 5 entries and stops.

### What to take away

- A composite index is sorted by its first column, then by the second within the first.
- Leftmost-prefix rule: `(a, b)` serves `a` and `a, b`. Not `b`.
- Column order: equality columns first, then the range or sort column.
- "The plan says Index Scan" is not enough. Check how many pages or rows it read.

---

## Task 6: Fix the Index That Exists but Is Skipped

**The idea:** an index stores the values of a column. If the query compares something else, such as
a function of the column, the index has nothing to offer.

### Before: login by email

```bash
./demo --query email
```

```
    SELECT id, email, name
      FROM customers
     WHERE lower(email) = lower(?)
    -- ? = 'CUSTOMER4242@SHOP.TEST'

Table customers: 100,000 rows, 7.3 MB.
Indexes on customers:
    customers_pkey                 (id)
    idx_customers_email            (email)

EXPLAIN ANALYZE
    Seq Scan on customers  (cost=0.00..2435.00 rows=500 width=45) (actual time=1.445..30.608 rows=1 loops=1)
      Filter: (lower(email) = 'customer4242@shop.test'::text)
      Rows Removed by Filter: 99999
    Execution Time: 30.622 ms

RESULT: SEQ SCAN. Postgres read 100,000 rows to return 1.
```

There is an index on `email`, listed right there, and Postgres does not use it. Read the `Filter`:
it compares `lower(email)`. The index is sorted by `email`. `Customer4242@Shop.test` and
`customer4242@shop.test` are far apart in that order, so the index cannot tell where the lowercase
version would be. Postgres has to compute `lower(email)` for every row.

### The change, part 1: index the expression

You cannot remove `lower()` from the query: emails are stored the way people typed them, some with
capitals. So make an index that stores exactly what the query compares. Add to `indexes.sql`:

```sql
CREATE INDEX idx_customers_email_lower ON customers (lower(email));
```

This is an **expression index**. Postgres computes `lower(email)` once per row, when the row is
written, and keeps those values sorted.

```bash
./demo --query email
```

```
    Index Scan using idx_customers_email_lower on customers  (cost=0.42..8.44 rows=1 width=45) (actual time=0.114..0.115 rows=1 loops=1)
      Index Cond: (lower(email) = 'customer4242@shop.test'::text)
    Execution Time: 0.180 ms

RESULT: OK. Index Scan using idx_customers_email_lower on customers. Postgres read 1 row to return 1.
```

The query did not change. The expression in the query must match the expression in the index.

### Before: the daily sales report

```bash
./demo --query daily-sales
```

```
    SELECT count(*) AS orders, coalesce(sum(total_cents), 0) AS revenue_cents
      FROM orders
     WHERE date(created_at) = ?
    -- ? = '2025-06-15'

EXPLAIN ANALYZE
    Aggregate  (cost=25335.00..25335.01 rows=1 width=16) (actual time=78.314..78.315 rows=1 loops=1)
      ->  Seq Scan on orders  (cost=0.00..25310.00 rows=5000 width=4) (actual time=54.578..78.247 rows=1370 loops=1)
            Filter: (date(created_at) = '2025-06-15'::date)
            Rows Removed by Filter: 998630

RESULT: SEQ SCAN. Postgres read 1,000,000 rows to return 1.
```

Same disease. `idx_orders_created_customer` starts with `created_at`, but the query asks about
`date(created_at)`.

### The change, part 2: rewrite the query

You could add another expression index. But there is a better fix that costs nothing: ask the same
question without touching the column. "On 15 June" means "from 15 June 00:00, up to but not
including 16 June 00:00".

In `ShopQueries.dailySales`:

```java
public static Query dailySales(LocalDate day) {
    return new Query("""
            SELECT count(*) AS orders, coalesce(sum(total_cents), 0) AS revenue_cents
              FROM orders
             WHERE created_at >= ?
               AND created_at < ?
            """, day.atStartOfDay(), day.plusDays(1).atStartOfDay());
}
```

The column now stands alone on the left of each comparison. The work was moved to the other side,
to the parameters, where it is done once in Java and not once per row.

Use `>=` and `<`, a **half-open range**. `BETWEEN '2025-06-15 00:00:00' AND '2025-06-15 23:59:59'`
loses an order placed at 23:59:59.5, and `BETWEEN` two midnights counts an order placed exactly at
midnight in two days.

```bash
./demo --query daily-sales
```

```
    -- ? = '2025-06-15T00:00', '2025-06-16T00:00'

EXPLAIN ANALYZE
    Aggregate  (cost=2358.45..2358.46 rows=1 width=16) (actual time=0.384..0.385 rows=1 loops=1)
      ->  Index Scan using idx_orders_created_customer on orders  (cost=0.42..2351.47 rows=1396 width=4) (actual time=0.116..0.270 rows=1370 loops=1)
            Index Cond: ((created_at >= '2025-06-15 00:00:00'::timestamp without time zone) AND (created_at < '2025-06-16 00:00:00'::timestamp without time zone))
    Execution Time: 0.449 ms

How the rows were found:    Index Scan using idx_orders_created_customer on orders
Rows returned:              1
Rows read:                  1,370
Pages read (8 kB each):     25

RESULT: OK. Index Scan using idx_orders_created_customer on orders. Postgres read 1,370 rows to return 1.
```

1,370 rows read, which is the number of orders that day, and 25 pages. No new index: the old one
was good enough all along. This is a range scan: the tree is searched for the first midnight, then
the leaves are read in order until the second.

### Check

```bash
./check 6
```

```
Task 6: Fix the index that exists but is skipped
  [PASS] Rewrite: the range is half-open: from midnight of the day (included) to midnight of the next day (excluded)
  [PASS] Rewrite: the daily sales SQL compares created_at directly, with no function or cast around the column
  [PASS] Rewrite: the daily sales report reads only that day's orders through an index, not a Seq Scan
  [PASS] Expression index: finding a customer by lower(email) is an Index Scan that reads 1 row, not a Seq Scan
  4 of 4 pass
```

The first two are unit tests: they look at the SQL text and the bound values, with no database.

---

## Task 7: Inspect More Ways to Lose an Index

### Step 1: two fixes, when to use which

- **Rewrite the predicate** when you can express the same condition with the bare column. It is
  free: no new index to store and maintain. Dates and ranges are the usual case.
- **Expression index** when the function is the point, as with case-insensitive matching.
  It costs one more index.

A third option for emails: store them lowercased in the first place, and index the plain column.

### Step 2: LIKE with a leading wildcard

```bash
./demo --query like
```

```
    SELECT id, tracking_number
      FROM orders
     WHERE tracking_number LIKE ?
    -- ? = '%ABC'

EXPLAIN ANALYZE
    Seq Scan on orders  (cost=0.00..22810.00 rows=100 width=24) (actual time=1.573..216.098 rows=232 loops=1)
      Filter: (tracking_number ~~ '%ABC'::text)
      Rows Removed by Filter: 999768

RESULT: SEQ SCAN. Postgres read 1,000,000 rows to return 232.
```

(`~~` is how Postgres writes `LIKE` internally.)

`tracking_number` has the index from Task 2, and it is useless here. Phone book again: find
everyone whose name **ends** in "son". The book is sorted from the first letter. Knowing the last
letters tells you nothing about where to look.

For "contains" or "ends with" search you need a different kind of index: a GIN index with the
`pg_trgm` extension, or a full-text search index. A B-tree cannot do it.

### Step 3: LIKE with a prefix

```bash
./demo --query like --pattern 'TRKF63F%'
```

```
    Seq Scan on orders  (cost=0.00..22810.00 rows=100 width=24) (actual time=0.166..192.619 rows=17 loops=1)
      Filter: (tracking_number ~~ 'TRKF63F%'::text)
      Rows Removed by Filter: 999983

RESULT: SEQ SCAN. Postgres read 1,000,000 rows to return 17.
```

Most people expect an index scan here, and in principle a B-tree can serve a prefix: "everyone
whose name starts with Smi" is one stretch of the phone book.

It is a Seq Scan because of a Postgres detail. This database sorts text by language rules
(`en_US.utf8`), and under those rules "starts with" does not map cleanly to one stretch of the
index. A prefix `LIKE` can use a B-tree only if the database uses the `C` collation, or if the
index is built for pattern matching:

```sql
CREATE INDEX ... ON orders (tracking_number text_pattern_ops);
```

You do not need to remember the syntax. Remember the lesson: do not assume. Run `EXPLAIN`.

### Step 4 and 5: the wrong parameter type

```bash
./demo --query numeric-id
```

```
    SELECT count(*) AS orders, coalesce(sum(total_cents), 0) AS spent_cents
      FROM orders
     WHERE customer_id = ?
    -- ? = 1 (BigDecimal)

EXPLAIN ANALYZE
    Aggregate  (cost=25335.00..25335.01 rows=1 width=16) (actual time=96.762..96.763 rows=1 loops=1)
      ->  Seq Scan on orders  (cost=0.00..25310.00 rows=5000 width=4) (actual time=0.097..96.551 rows=1000 loops=1)
            Filter: ((customer_id)::numeric = '1'::numeric)
            Rows Removed by Filter: 999000

RESULT: SEQ SCAN. Postgres read 1,000,000 rows to return 1.
```

The SQL is `customer_id = ?`, with nothing around the column. But the value was bound as a
`BigDecimal`, which Postgres receives as `numeric`. The column is `bigint`. To compare them Postgres
converts the **column**: `(customer_id)::numeric`. That is a function on the column again, written
by the database this time.

The same query with a `long`:

```bash
./demo --query spend
```

```
      ->  Bitmap Heap Scan on orders  (cost=28.69..3146.62 rows=1067 width=4) (actual time=0.732..2.612 rows=1000 loops=1)
            Recheck Cond: (customer_id = '1'::bigint)
            Heap Blocks: exact=1000
            ->  Bitmap Index Scan on idx_orders_customer_created  (cost=0.00..28.43 rows=1067 width=0) (actual time=0.380..0.381 rows=1000 loops=1)
                  Index Cond: (customer_id = '1'::bigint)

Pages read (8 kB each):     1,006
```

1,000 rows through the index instead of 1,000,000 through the table.

The classic version of this bug: an id stored as `varchar` and queried with a number, or the other
way round. Make the Java type match the column type.

### Step 6: the list

An index exists and is not used when:

1. The column is inside a function or a cast: `lower(email)`, `date(created_at)`.
2. The parameter has a different type, so the database casts the column.
3. `LIKE` starts with a wildcard.
4. The column is not the leftmost column of the index (Task 5).
5. The condition matches too many rows, so a Seq Scan is cheaper (Task 10).
6. The statistics are out of date and the planner misjudges how many rows match (Task 10).

---

## Task 8: Fix It With a Partial Index and a Covering Index

### Before: the pending queue

```bash
./demo --query pending
```

```
    SELECT id, customer_id, total_cents, created_at
      FROM orders
     WHERE status = 'PENDING'
     ORDER BY created_at
     LIMIT 50

EXPLAIN ANALYZE
    Limit  (cost=0.42..727.80 rows=50 width=28) (actual time=258.984..258.996 rows=50 loops=1)
      ->  Index Scan using idx_orders_created_customer on orders  (cost=0.42..56735.99 rows=3900 width=28) (actual time=258.983..258.991 rows=50 loops=1)
            Filter: (status = 'PENDING'::text)
            Rows Removed by Filter: 996000
    Execution Time: 259.179 ms

How the rows were found:    Index Scan using idx_orders_created_customer on orders
Rows returned:              50
Rows read:                  996,050
Rows read and thrown away:  996,000
Pages read (8 kB each):     14,088
Time in the database:       259.179 ms

RESULT: INDEX USED, BUT WASTEFUL. Postgres read 996,050 rows and threw away 996,000 to return 50.
```

This is the slowest query of the project, and it uses an index. It read **more** pages than a
sequential scan (14,088 against 10,310).

The planner's reasoning: "The query wants rows in `created_at` order. I have an index in that
order. About 0.4% of orders are PENDING, so if I walk the index from the oldest order I should find
50 pending ones after about 12,500 rows." It assumed pending orders are spread evenly over time.
They are not: only the newest orders are pending. So it walked through 996,000 old, delivered
orders, fetching each one from the table to look at its status, before it met the first pending one.

The planner knows how common each status is. It does not know that status and date are related.

### The change, part 1: a partial index

Add to `indexes.sql`:

```sql
CREATE INDEX idx_orders_pending ON orders (created_at) WHERE status = 'PENDING';
```

A **partial index** only contains the rows that match its `WHERE` clause. This one holds the 4,000
pending orders, sorted by `created_at`, and ignores the other 996,000.

```bash
./demo --query pending
```

```
    Limit  (cost=0.28..2.21 rows=50 width=28) (actual time=0.092..0.108 rows=50 loops=1)
      ->  Index Scan using idx_orders_pending on orders  (cost=0.28..160.79 rows=4167 width=28) (actual time=0.090..0.101 rows=50 loops=1)
    Execution Time: 0.211 ms

How the rows were found:    Index Scan using idx_orders_pending on orders
Rows returned:              50
Rows read:                  50
Rows read and thrown away:  0
Pages read (8 kB each):     3

RESULT: OK. Index Scan using idx_orders_pending on orders. Postgres read 50 rows to return 50.
```

3 pages instead of 14,088.

### Before: what a customer spent

```bash
./demo --query spend
```

```
    SELECT count(*) AS orders, coalesce(sum(total_cents), 0) AS spent_cents
      FROM orders
     WHERE customer_id = ?
    -- ? = 1 (Long)

EXPLAIN ANALYZE
    Aggregate  (cost=3151.95..3151.96 rows=1 width=16) (actual time=2.989..2.990 rows=1 loops=1)
      ->  Bitmap Heap Scan on orders  (cost=28.69..3146.62 rows=1067 width=4) (actual time=0.732..2.612 rows=1000 loops=1)
            Recheck Cond: (customer_id = '1'::bigint)
            Heap Blocks: exact=1000
            ->  Bitmap Index Scan on idx_orders_customer_created  (cost=0.00..28.43 rows=1067 width=0) (actual time=0.380..0.381 rows=1000 loops=1)
                  Index Cond: (customer_id = '1'::bigint)

How the rows were found:    Bitmap Heap Scan on orders + Bitmap Index Scan using idx_orders_customer_created
Rows returned:              1
Rows read:                  1,000
Pages read (8 kB each):     1,006

RESULT: INDEX USED, BUT THE TABLE IS STILL VISITED. 1,000 rows were fetched from the table to add up one column.
```

This is not broken, it is just doing more than it has to. The index finds customer 1's 1,000 orders
in 6 pages. Then, for each one, Postgres goes to the table to read `total_cents`:
`Heap Blocks: exact=1000`. The orders of one customer are spread all over the table, so that is
1,000 different pages. ("Heap" is Postgres's word for the table itself.)

### The change, part 2: a covering index

If `total_cents` were in the index, there would be no reason to visit the table. **Change** the
Task 4 line in `indexes.sql`:

```sql
CREATE INDEX idx_orders_customer_created ON orders (customer_id, created_at) INCLUDE (total_cents);
```

`INCLUDE` stores extra columns in the leaves of the index. They are not part of the sort order, so
they cannot be searched. They are just carried along. An index that has every column a query needs
is called a **covering index** for that query.

```bash
./demo --query spend
```

```
    Aggregate  (cost=51.42..51.43 rows=1 width=16) (actual time=0.683..0.683 rows=1 loops=1)
      ->  Index Only Scan using idx_orders_customer_created on orders  (cost=0.42..45.42 rows=1200 width=4) (actual time=0.079..0.197 rows=1000 loops=1)
            Index Cond: (customer_id = '1'::bigint)
            Heap Fetches: 0

How the rows were found:    Index Only Scan using idx_orders_customer_created on orders
Rows returned:              1
Rows read:                  1,000
Heap fetches (table visits): 0
Pages read (8 kB each):     8

RESULT: OK. Index Only Scan using idx_orders_customer_created on orders. The table was not visited at all.
```

`Index Only Scan`, `Heap Fetches: 0`, and 8 pages instead of 1,006.

### Why change the index and not add one?

`CREATE INDEX ... ON orders (customer_id) INCLUDE (total_cents)` as a second index also passes the
tests. But then you have two indexes that start with `customer_id`, both written on every insert,
when one can do both jobs. Before adding an index, look for an existing one you can extend.

Task 4 still works, because the sort order did not change:

```bash
./demo --query latest-orders
```

```
      ->  Index Scan Backward using idx_orders_customer_created on orders  (cost=0.42..44.60 rows=10 width=29) (actual time=0.069..0.085 rows=5 loops=1)
RESULT: OK. Index Scan using idx_orders_customer_created on orders. Postgres read 5 rows to return 5.
```

### Check

```bash
./check 8
./check
```

```
Task 8: Fix it with a partial index and a covering index
  [PASS] Covering index: a customer's total spend is an Index Only Scan
  [PASS] Partial index: the pending queue is read from an index that only contains the PENDING orders
  [PASS] Partial index: the queue reads exactly the 50 rows it returns, and nothing is sorted or thrown away
  [PASS] Covering index: the table is never visited (Heap Fetches: 0)
  [PASS] Partial index: it is at least 20 times smaller than the primary key index, which covers every order
  5 of 5 pass
```

`./check` should now print 25 passes and no failures.

---

## Task 9: Inspect the Partial and the Covering Index

### Step 1: sizes

```bash
./inspect
```

```
INDEXES (scans = how many times a query used the index since it was built)
  table      index                        columns                                          size     scans    kind
  ---------  ---------------------------  -----------------------------------------------  -------  -------  -----------
  orders     orders_pkey                  (id)                                             21 MB    23       primary key
  orders     idx_orders_created_customer  (created_at, customer_id)                        30 MB    0
  orders     idx_orders_customer_created  (customer_id, created_at) INCLUDE (total_cents)  39 MB    6        covering
  orders     idx_orders_pending           (created_at) WHERE (status = 'PENDING')          104 kB   3        partial
  orders     idx_orders_tracking_number   (tracking_number)                                30 MB    0
  customers  customers_pkey               (id)                                             2208 kB  2000000  primary key
  customers  idx_customers_email          (email)                                          3992 kB  0
  customers  idx_customers_email_lower    (lower(email))                                   3992 kB  0        expression
```

The partial index is **104 kB**. Every other index on `orders` is between 21 and 39 MB, about 300
times bigger. It holds 4,000 entries instead of 1,000,000.

And it stays that size. When the shop has 100 million delivered orders, there are still only a few
thousand pending ones. An order enters the index when it is placed and leaves it when its status
changes. Writes to it are cheap for the same reason.

### Step 2: where did the conditions go

The plan was two lines: `Limit` over `Index Scan using idx_orders_pending`. No `Filter`, no
`Index Cond`, no `Sort`.

- `status = 'PENDING'` is not checked, because every entry in this index is pending by definition.
- `ORDER BY created_at` is not sorted, because the index is in that order.

The index is the answer to the query, already prepared.

### Step 3: when a partial index is used

Only when the query's `WHERE` implies the index's `WHERE`. A query for `status = 'DELIVERED'`
cannot use it: the delivered orders are not in there. This is the price: a partial index serves
one kind of question.

Use one when queries always ask for the same small, hot subset: unprocessed jobs, pending orders,
unread notifications, rows where `deleted_at IS NULL`.

### Step 4 and 5: covering

1,006 pages became 8. The 1,000 that disappeared were the table visits. Everything the query needs
(`customer_id` to find the rows, `total_cents` to add up) is in the index leaves, and those are
packed next to each other.

The price: the index grew from 30 MB to 39 MB. One more column in a million entries.

### Step 6: the visibility map

```bash
./demo --query spend --after-update
```

```
--after-update: 1,000 orders of customer 1 were just updated (to the same values). No VACUUM has run since.

EXPLAIN ANALYZE, right after the update
    Aggregate  (cost=59.45..59.46 rows=1 width=16) (actual time=0.391..0.392 rows=1 loops=1)
      ->  Index Only Scan using idx_orders_customer_created on orders  (cost=0.42..53.44 rows=1201 width=4) (actual time=0.086..0.320 rows=1000 loops=1)
            Index Cond: (customer_id = '1'::bigint)
            Heap Fetches: 1000

Heap fetches (table visits): 1,000

Now VACUUM orders has run.

EXPLAIN ANALYZE, after VACUUM
    Aggregate  (cost=51.42..51.43 rows=1 width=16) (actual time=0.241..0.242 rows=1 loops=1)
      ->  Index Only Scan using idx_orders_customer_created on orders  (cost=0.42..45.42 rows=1200 width=4) (actual time=0.064..0.176 rows=1000 loops=1)
            Index Cond: (customer_id = '1'::bigint)
            Heap Fetches: 0

Heap fetches (table visits): 0

RESULT: OK. Index Only Scan using idx_orders_customer_created on orders. The table was not visited at all.
```

Still called `Index Only Scan`, and it went to the table 1,000 times. Why?

When a row is updated, Postgres does not overwrite it. It writes a new version and keeps the old
one for transactions that started earlier and must still see it. Which version a transaction may
see is recorded **in the table, not in the index**. So an index entry alone cannot say "this row is
valid for you".

To avoid checking the table every time, Postgres keeps a **visibility map**: one bit per table page
meaning "every row on this page is visible to everyone". If the bit is set, the index entry can be
trusted and the table is skipped. If the page was changed recently, the bit is off and Postgres
must fetch the row to check. That is a heap fetch.

**VACUUM** is what sets the bits again. It normally runs by itself (autovacuum). In `./inspect`,
`pages_all_visible: 10310 of 10310` is the visibility map.

What to remember: an index-only scan is only "index only" on pages that have not changed since the
last VACUUM. On a table with constant updates you will see heap fetches, and the benefit shrinks.

### Step 7: latest-orders is not index-only

It selects `id` and `status`, which are not in the index, so it must visit the table for each of
the 5 rows. You could `INCLUDE` them too. For 5 rows it is not worth it: every included column
makes the index bigger and every insert slower.

This is also why `SELECT *` rules out an index-only scan: no index contains every column. Select
the columns you need.

### What to take away

- Partial index: only the rows matching a `WHERE`. Tiny, cheap to maintain, serves one question.
- Covering index: contains every column the query needs. `INCLUDE` adds payload columns.
- `Index Only Scan` with `Heap Fetches: 0` is the goal. Heap fetches come from pages changed since
  the last VACUUM.

---

## Task 10: The Cost — Why Not Index Everything?

### Step 1 and 2: what an index costs on a write

```bash
./demo --write-cost
```

```
Write cost: the same 200,000 orders are inserted into two empty copies of the orders table.
One copy has no index. The other has these six:

    CREATE UNIQUE INDEX scratch_pk ON scratch_indexed (id)
    CREATE INDEX scratch_tracking ON scratch_indexed (tracking_number)
    CREATE INDEX scratch_customer_created ON scratch_indexed (customer_id, created_at)
    CREATE INDEX scratch_created_customer ON scratch_indexed (created_at, customer_id)
    CREATE INDEX scratch_status ON scratch_indexed (status)
    CREATE INDEX scratch_total ON scratch_indexed (total_cents)

                               no index      6 indexes
  Rows inserted                 200,000        200,000
  Time to insert                 823 ms       8,144 ms
  Size of the rows              16.1 MB        16.1 MB
  Size of the indexes            0.0 MB        31.4 MB

RESULT: SIX INDEXES MADE THE SAME INSERT 9.9x SLOWER and added 31.4 MB of indexes to 16.1 MB of rows.
        Every INSERT has to add one entry to every index. So does every UPDATE of an indexed column.
```

The same rows, about ten times slower to insert, and the indexes take twice the space of the data.
(These times were measured on a busy machine. Yours will be different, and the ratio may be
anywhere from 5x to 15x. The sizes will match.)

Inserting a row into a table means appending it to a page. Inserting it into a B-tree means finding
the right leaf, in sorted order, and sometimes splitting a full page in two. Six indexes, six times.

```bash
./demo --write-cost --rows 1000000
```

```
                               no index      6 indexes
  Rows inserted               1,000,000      1,000,000
  Time to insert               3,137 ms      30,649 ms
  Size of the rows              80.5 MB        80.5 MB
  Size of the indexes            0.0 MB       183.1 MB
```

It does not get better with size.

### Step 3: an index the planner refuses to use

```bash
./demo --query by-status --status DELIVERED
```

```
For this run only, the table also has: CREATE INDEX idx_tmp_orders_status ON orders (status)

EXPLAIN ANALYZE
    Aggregate  (cost=27489.67..27489.67 rows=1 width=16) (actual time=328.690..328.690 rows=1 loops=1)
      ->  Seq Scan on orders  (cost=0.00..22820.00 rows=933933 width=4) (actual time=0.462..222.040 rows=931000 loops=1)
            Filter: (status = 'DELIVERED'::text)
            Rows Removed by Filter: 69000

RESULT: SEQ SCAN, AND THAT IS THE RIGHT PLAN. status = 'DELIVERED' matches 931,000 of 1,000,000 rows (93.1%).
        Reading the whole table once is cheaper than visiting that many rows through the index.
```

There is an index on exactly the column in the `WHERE` clause, and Postgres ignores it. It is
right to. An index scan means: find an entry, jump to the table page, find the next entry, jump
again. For 931,000 rows that is 931,000 jumps. Reading the table straight through, 10,320 pages in
order, is far cheaper.

An index helps you **skip** rows. If a condition skips almost nothing, the index has nothing to
give. How much a condition narrows things down is called its **selectivity**.

### Step 4 and 5: the tipping point

```bash
./demo --query by-status --status CANCELLED
./demo --query by-status --status SHIPPED
./demo --query by-status --status PENDING
```

| Status | Share of rows | Plan | Pages read |
|---|---|---|---|
| DELIVERED | 93.1% | Seq Scan | 10,320 |
| CANCELLED | 4.9% | Index Scan using idx_tmp_orders_status | 10,158 |
| SHIPPED | 1.6% | Index Scan using idx_tmp_orders_status | 183 |
| PENDING | 0.4% | Index Scan using idx_orders_pending | 61 |

Somewhere between 5% and 93% the planner switches. There is no fixed number: it depends on the size
of the rows and on how the matching rows are spread over the table.

Look at `CANCELLED`. The index was used, and the query still read 10,158 pages, nearly the whole
table. Every 20th order is cancelled, and a page holds about 97 orders, so **every page** contains
a few cancelled ones. The index found them, and finding them meant visiting every page anyway.
`SHIPPED` matches a third as many rows and needed 55 times fewer pages, because shipped orders are
all recent and sit together at the end of the table.

So "few distinct values" is not quite the rule. What matters is how many rows match and how many
pages they are on. A status, a boolean or a gender column is usually a bad candidate for a plain
index. If one rare value is the interesting one, that is a job for a partial index (Task 8). Notice
that `PENDING` used yours.

### Step 6: unused indexes

Run each query once, then look:

```bash
./demo --query track
./demo --query latest-orders
./demo --query email
./demo --query daily-sales
./demo --query pending
./demo --query spend
./inspect
```

```
  table      index                        columns                                          size     scans    kind
  ---------  ---------------------------  -----------------------------------------------  -------  -------  -----------
  orders     orders_pkey                  (id)                                             21 MB    28       primary key
  orders     idx_orders_created_customer  (created_at, customer_id)                        30 MB    3
  orders     idx_orders_customer_created  (customer_id, created_at) INCLUDE (total_cents)  39 MB    19       covering
  orders     idx_orders_pending           (created_at) WHERE (status = 'PENDING')          104 kB   10       partial
  orders     idx_orders_tracking_number   (tracking_number)                                30 MB    3
  customers  customers_pkey               (id)                                             2208 kB  2000000  primary key
  customers  idx_customers_email          (email)                                          3992 kB  0
  customers  idx_customers_email_lower    (lower(email))                                   3992 kB  3        expression
```

`idx_customers_email` has 0 scans. No query in the shop compares the bare `email` column: login
compares `lower(email)`. That index was added by someone who thought it would help, it never did,
and every new customer has paid for it since. Delete its line from `indexes.sql` and run `./check`:
25 of 25, nothing breaks.

Now delete `idx_orders_created_customer` instead: `./check 6` drops to 3 of 4, because the daily
sales report is a Seq Scan again. Put it back.

In production you do the same with `pg_stat_user_indexes` over weeks of real traffic, and you check
the replicas as well, because they count separately.

### Step 7: where the planner's numbers come from

```
WHAT THE PLANNER KNOWS ABOUT orders.status (from pg_stats, a sample taken by ANALYZE)
  status     share_of_rows  estimated_rows
  ---------  -------------  --------------
  DELIVERED  93.4%          933,933
  CANCELLED  4.7%           46,667
  SHIPPED    1.5%           15,233
  PENDING    0.4%           4,167
```

`ANALYZE` reads a sample of the table and stores, per column, the most common values and how often
they occur. The planner uses that to guess how many rows a condition matches, and from that the
cost of each plan. The numbers are estimates (the real share of `DELIVERED` is 93.1%).

If the statistics are stale, for example right after a big import, the planner works from a picture
of the table that is no longer true and can choose a Seq Scan where an index was right, or the
reverse. Autovacuum runs `ANALYZE` for you. After a bulk load, run it yourself.

This is one more answer to "fast in dev, slow in prod": different data means different statistics,
which means a different plan for the same SQL.

### Check

```bash
./check 10
```

```
Task 10: The cost of an index
  [PASS] Cost: the same index IS used for SHIPPED (1.6% of the rows)
  [PASS] Cost: an index on status is NOT used for DELIVERED (93% of the rows): the planner prefers a Seq Scan
  [PASS] Cost: six indexes on 20,000 orders take more disk space than the orders themselves
  3 of 3 pass
```

These pass from the start. They document the cost.

### When to add an index, and when not to

| Add an index when | Think twice when |
|---|---|
| A frequent or slow query filters, joins or sorts on the column | The table is small: a Seq Scan of a few pages is already instant |
| The condition matches a small share of the rows | The condition matches a large share of the rows |
| It is a foreign key you join or filter on (Postgres does not index those for you) | The table is written far more often than it is read |
| An existing index cannot be extended to do the job | An existing index already starts with the same columns |

### All five indexes, side by side

| Index | Statement | Serves | Before (pages) | After (pages) |
|---|---|---|---|---|
| B-tree | `(tracking_number)` | `tracking_number = ?` | 10,310 | 4 |
| Composite | `(customer_id, created_at)` | `customer_id = ? ORDER BY created_at DESC LIMIT 5` | 1,607 | 8 |
| Expression | `(lower(email))` | `lower(email) = lower(?)` | 935 | 4 |
| Partial | `(created_at) WHERE status = 'PENDING'` | the pending queue | 14,088 | 3 |
| Covering | `... INCLUDE (total_cents)` | `sum(total_cents) WHERE customer_id = ?` | 1,006 | 8 |
| (no index, a rewrite) | `created_at >= ? AND created_at < ?` | the daily report | 10,310 | 25 |

### How to say it in an interview

> "When a query is slow I first look at its plan with EXPLAIN ANALYZE, on data the size of
> production. I look for a sequential scan on a big table, for a large 'rows removed by filter',
> and for a sort. If the column in the WHERE clause has no index, I add a B-tree index: a sorted
> tree, so a lookup reads three or four pages instead of the whole table. If the query filters on
> one column and sorts on another, I use a composite index with the equality column first, because
> an index can only be searched by its leftmost columns. If there is an index and it is not used, I
> check for a function or a cast on the column, a type mismatch, or a leading wildcard, and I
> either rewrite the predicate or create an expression index. For a small hot subset I use a
> partial index, and if the query only needs a few columns I include them so it becomes an
> index-only scan. I do not index everything: every index slows down writes and takes space, and
> the planner will not use an index on a value that matches most of the table. Afterwards I run
> EXPLAIN ANALYZE again to prove the plan changed."

Then be ready for the follow-ups in "Questions to Ask Afterwards" at the end of
[`INTERVIEW_TASKS.md`](INTERVIEW_TASKS.md). The one that catches people: "how would you add that
index to a 500-million-row table in production?" (`CREATE INDEX CONCURRENTLY`, so that writes are
not blocked while it builds.)

---

## Clean up

```bash
docker compose down
```

The data is gone with the container. The next `docker compose up -d` and `./demo` seed it again.
