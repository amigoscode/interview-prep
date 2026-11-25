# Guided Tasks — SQL Traps and Slow Queries

The same ten tasks as [`INTERVIEW_TASKS.md`](INTERVIEW_TASKS.md), step by step: the command to run,
the output you should see, why it happens, and the change to make.

Commands are for macOS and Linux. On Windows type `.\demo`, `.\inspect` and `.\check`.

All output below was copied from real runs, and shortened to the lines that matter. **Timings
differ on every machine and on every run.** The rows, the row counts, the join types and the
`RESULT` line should match. Page counts can be off by a few.

**Before you start**

```bash
cd start
docker compose up -d
```

**About the tools**

- Every change you make is in `src/main/java/com/amigoscode/sqltraps/shop/ShopQueries.java`.
- `./inspect "SELECT ..."` runs any read-only SELECT (in double quotes) and shows its rows, its
  plan and the facts pulled out of the plan. It runs against the shop `./demo` loaded last.
- Plans are taken with parallel query and JIT switched off, so your plan looks like the one here.
- How to read `EXPLAIN ANALYZE` line by line is Task 1 of [Indexes](../03-indexes/TASKS_GUIDED.md).
  Here every plan comes with a "WHAT STANDS OUT" block that names the facts you need.

---

# Part A: wrong rows, no error

Part A runs on the small shop. Read `src/main/resources/seed-small.sql` first: 8 customers, 12
orders. Margaret (4) and Alan (7) have no orders. Linus (3) and Dennis (5) have one order that is
not PAID. Order 12 is a guest checkout with no customer.

## Task 1: See the Report Drop Customers

No code in this task. You are collecting evidence.

```bash
./demo
```
```
    SELECT c.id, c.name, COUNT(o.id) AS paid_orders
      FROM customers c
      LEFT JOIN orders o ON o.customer_id = c.id
     WHERE o.status = 'PAID'
     GROUP BY c.id, c.name
     ORDER BY c.id

WHAT THE QUERY RETURNED (4 rows)
  id  name     paid_orders
  --  -------  -----------
  1   Ada      2
  2   Grace    1
  6   Barbara  3
  8   Edsger   1

THE DIFFERENCE
  missing:  3 Linus (paid_orders 0)
  missing:  4 Margaret (paid_orders 0)
  missing:  5 Dennis (paid_orders 0)
  missing:  7 Alan (paid_orders 0)

RESULT: WRONG ROWS, NO ERROR. The report shows 4 customers. There are 8. 4 customers without a PAID order are missing: Linus, Margaret, Dennis, Alan.
```

The query is valid SQL. It ran. The numbers it shows are correct. It is missing half the customers,
and exactly the ones Sales wanted: the ones with zero. No error, no warning, and a test that checks
"the report returns rows" passes.

### The join without the WHERE

```bash
./inspect "SELECT c.id, c.name, o.id AS order_id, o.status FROM customers c LEFT JOIN orders o ON o.customer_id = c.id ORDER BY c.id, o.id"
```
```
ROWS (13)
  id  name      order_id  status
  --  --------  --------  ---------
  1   Ada       1         PAID
  1   Ada       2         PAID
  2   Grace     3         PAID
  2   Grace     4         CANCELLED
  3   Linus     5         PENDING
  4   Margaret  NULL      NULL
  5   Dennis    6         CANCELLED
  6   Barbara   7         PAID
  6   Barbara   8         PAID
  6   Barbara   9         PAID
  7   Alan      NULL      NULL
  8   Edsger    10        PAID
  8   Edsger    11        PENDING
```

This is what a `LEFT JOIN` does. Every customer is there. Margaret and Alan have no order, so each
gets **one row with NULL in every order column**.

Now apply `WHERE o.status = 'PAID'` to these 13 rows in your head:

- Linus: `'PENDING' = 'PAID'` is false. Row removed, and Linus with it.
- Dennis: `'CANCELLED' = 'PAID'` is false. Gone.
- Margaret and Alan: `NULL = 'PAID'` is not true (Task 6 says what it is). Gone.

`WHERE` runs **after** the join, on the joined rows. It removes every row that is not a PAID order,
and with them every customer who has none.

### What Postgres made of it

```bash
./demo --plan
```
```
            ->  Hash Join  (cost=1.18..2.36 rows=8 width=22) (actual time=0.019..0.022 rows=7 loops=1)
                  Hash Cond: (o.customer_id = c.id)
...
  Join:                      Hash Join, join type Inner: a row needs a match on both sides, rows without one are dropped
```

You wrote `LEFT JOIN`. Postgres ran a plain `Hash Join`: an **inner** join. That is not a planner
mistake. It worked out that your `WHERE` throws away every NULL-filled row, so producing them would
be wasted work. The plan tells the truth about your query.

`./check 1` prints 1 of 1. That test passes on purpose: it has its own copy of the broken query and
documents the trap, so you can always compare.

---

## Task 2: Fix the LEFT JOIN That Became an Inner Join

**The idea:** the condition `o.status = 'PAID'` is right. It is in the wrong place. In `WHERE` it
decides which joined rows survive. In `ON` it decides which orders **match** a customer, and a
customer with no matching order still comes out, with NULLs.

`./check 2` starts at 0 of 3. The first failure shows the ids: `[1L, 2L, 6L, 8L]`, not 1 to 8.

### The change

In `ShopQueries.paidOrdersPerCustomer`, move the condition from `WHERE` into the `ON` clause:

```java
return new Query("""
        SELECT c.id, c.name, COUNT(o.id) AS paid_orders
          FROM customers c
          LEFT JOIN orders o ON o.customer_id = c.id
                            AND o.status = 'PAID'
         GROUP BY c.id, c.name
         ORDER BY c.id
        """);
```

The `WHERE` line is gone. Nothing else changes. Keep `COUNT(o.id)`.

### After

Run `./demo` and `./check 2`. The demo now lists all 8 customers (Linus, Margaret, Dennis and Alan with 0) and ends with
`RESULT: OK. The report returns exactly the 8 rows it should.` `./check 2` prints 3 of 3.

---

## Task 3: Inspect the Join

### Step 1: the join type in the plan

```bash
./demo --plan
```
```
            ->  Hash Right Join  (cost=1.18..2.36 rows=8 width=22) (actual time=0.025..0.029 rows=11 loops=1)
                  Hash Cond: (o.customer_id = c.id)
                  ->  Seq Scan on orders o  ...
                        Filter: (status = 'PAID'::text)
                        Rows Removed by Filter: 4
...
  Join:                      Hash Join, join type Right: an outer join, rows without a match are kept and filled with NULL
```

`Hash Right Join`, not `Hash Left Join`. Postgres may swap the two sides of a join, and here it
reads `orders` first. A left join with the tables swapped is a right join: **the same outer join**.
What matters is that the word `Left` or `Right` is there. A plain `Hash Join`, `Nested Loop` or
`Merge Join` is an inner join.

Look where the status condition went: a `Filter` on the scan of `orders`, **below** the join.
Orders that are not PAID are dropped before they are matched. Customers are not touched.

### Step 2: the rows before grouping

```bash
./inspect "SELECT c.id, c.name, o.id AS order_id, o.status FROM customers c LEFT JOIN orders o ON o.customer_id = c.id AND o.status = 'PAID' ORDER BY c.id, o.id"
```
```
ROWS (11)
  id  name      order_id  status
  --  --------  --------  ------
  2   Grace     3         PAID
  3   Linus     NULL      NULL
  4   Margaret  NULL      NULL
  5   Dennis    NULL      NULL
  ...
```

Linus has an order (5, PENDING) and so has Dennis (6, CANCELLED). Neither order passes the `ON`
condition, so for this join they have **no matching order**, just like Margaret and Alan. All four
get a NULL-filled row, and `COUNT(o.id)` counts the non-NULL `o.id` values: 0.

### Steps 3 and 4: the condition in WHERE, and an inner join

Run the statement of step 2 twice more: once with the status moved to `WHERE`
(`... ON o.customer_id = c.id WHERE o.status = 'PAID' ...`), once with `JOIN` in place of
`LEFT JOIN`.

Both print `ROWS (7)`: the same 7 rows, the same plan, `join type Inner`. For an **inner** join it
makes no difference whether a condition is in `ON` or in `WHERE`. That is why many developers
believe the two are interchangeable. They are, until the join is an outer join.

| | Rows without a match | `ON` vs `WHERE` |
|---|---|---|
| `JOIN` (inner) | dropped | same result |
| `LEFT JOIN` | kept, right-hand columns are NULL | `ON` chooses the matches. `WHERE` filters the joined rows, the NULL-filled ones included |

### Step 5: COUNT(*) after a LEFT JOIN

```bash
./inspect "SELECT c.id, c.name, COUNT(*) AS all_rows, COUNT(o.id) AS orders FROM customers c LEFT JOIN orders o ON o.customer_id = c.id AND o.status = 'PAID' GROUP BY c.id, c.name ORDER BY c.id"
```
```
  id  name      all_rows  orders
  --  --------  --------  ------
  1   Ada       2         2
  2   Grace     1         1
  3   Linus     1         0
  4   Margaret  1         0
```

`COUNT(*)` counts rows. Margaret's group has one row, the NULL-filled one, so `COUNT(*)` says 1.
`COUNT(o.id)` counts the rows where `o.id` is not NULL: 0. After a `LEFT JOIN`, count a column of
the right table, never `*`. The second test of Task 2 exists to catch this.

### Step 6: the WHERE that is useful

```bash
./inspect "SELECT c.id, c.name FROM customers c LEFT JOIN orders o ON o.customer_id = c.id AND o.status = 'PAID' WHERE o.id IS NULL ORDER BY c.id"
```

It returns 4 rows: Linus, Margaret, Dennis, Alan. `WHERE o.id IS NULL` keeps **only** the rows that
found no match. This pattern is called an anti-join. It is the one `WHERE` on the right table that
does not turn a left join into an inner join, because it is the one condition that is true for
NULL.

---

## Task 4: Fix NOT IN With a NULL

```bash
./demo --scenario never-ordered
```
```
     WHERE c.id NOT IN (SELECT o.customer_id
                          FROM orders o)

WHAT THE QUERY RETURNED (0 rows)
  (no rows)

RESULT: WRONG ROWS, NO ERROR. The report is empty: it says every customer has ordered. 2 customers never ordered: Margaret, Alan.
```

The query reads like English: "customers whose id is not in the list of customer ids that have
orders". It was correct for a year. Then order 12 arrived: a guest checkout, `customer_id` NULL.

### Why one NULL empties the result

The subquery returns `1, 1, 2, 2, 3, 5, 6, 6, 6, 8, 8, NULL`. For Margaret, `NOT IN` asks:

```
4 <> 1  AND  4 <> 2  AND  4 <> 3  AND ... AND  4 <> 8  AND  4 <> NULL
 true        true         true                  true        unknown
```

NULL means "there is no value here". So "is 4 different from it?" has no yes or no answer: it is
**unknown**. `true AND unknown` is unknown. `WHERE` keeps a row only when the condition is **true**,
so Margaret is dropped. The same happens to every customer. Zero rows.

`./check 4` starts at 1 of 3. The passing test documents the trap: it runs the `NOT IN` query and
gets 0 rows, deletes the guest order in a transaction that is rolled back, and gets 2 rows.

### The change

In `ShopQueries.customersWhoNeverOrdered`, replace `NOT IN` with `NOT EXISTS`:

```java
return new Query("""
        SELECT c.id, c.name
          FROM customers c
         WHERE NOT EXISTS (SELECT 1
                             FROM orders o
                            WHERE o.customer_id = c.id)
         ORDER BY c.id
        """);
```

`EXISTS` asks "does the subquery return at least one row?". That is always yes or no, never
unknown. For Margaret the subquery is "orders where `customer_id = 4`": no rows, so `NOT EXISTS` is
true. The guest order matches nobody (`NULL = 4` is not true), so it is simply not found.
`SELECT 1` is a convention: `EXISTS` ignores what is selected.

### After

`./demo --scenario never-ordered` prints `RESULT: OK. The report returns exactly the 2 rows it
should.` and `./check 4` prints 3 of 3.

Adding `WHERE customer_id IS NOT NULL` to the `NOT IN` subquery also works, and breaks again the
day someone copies the query without that filter. `NOT EXISTS` cannot be broken by a NULL.

---

## Task 5: Fix the Revenue That Is Counted Twice, and the Big Spenders

### Fix 1: the revenue report

```bash
./demo --scenario revenue
```
```
           COUNT(*)           AS orders,
           SUM(o.total_cents) AS revenue_cents,
           SUM(i.quantity)    AS units
      FROM customers c
      JOIN orders o ON o.customer_id = c.id
      JOIN order_items i ON i.order_id = o.id
...
THE DIFFERENCE
  wrong:    2 Grace: orders is 3, should be 1
  wrong:    2 Grace: revenue_cents is 27000, should be 9000
  ...
RESULT: WRONG ROWS, NO ERROR. The report adds up to 820.00 of revenue. The PAID orders of these customers add up to 480.00. 340.00 was counted more than once.
```

Look at Grace. She has one PAID order, order 3, for 90.00, with three lines. See what the join does
to it:

```bash
./inspect "SELECT o.id AS order_id, o.total_cents, i.id AS line_id, i.quantity FROM orders o JOIN order_items i ON i.order_id = o.id WHERE o.customer_id = 2 AND o.status = 'PAID'"
```
```
ROWS (3)
  order_id  total_cents  line_id  quantity
  --------  -----------  -------  --------
  3         9000         4        1
  3         9000         5        1
  3         9000         6        2
```

One order, three rows. The join produces one row per **line**, and the order's columns are repeated
on each of them. `COUNT(*)` counts 3. `SUM(o.total_cents)` adds 9000 three times: 27000.
`SUM(i.quantity)` is right, 4, because quantity really is a per-line value. This is **row
multiplication** (also called fan-out): an aggregate over a column of the "one" side, after a join
to the "many" side.

**The change.** Make `order_items` one row per order before joining it. In
`ShopQueries.revenuePerCustomer`:

```java
return new Query("""
        SELECT c.id, c.name,
               COUNT(o.id)        AS orders,
               SUM(o.total_cents) AS revenue_cents,
               SUM(i.units)       AS units
          FROM customers c
          JOIN orders o ON o.customer_id = c.id
          JOIN (SELECT order_id, SUM(quantity) AS units
                  FROM order_items
                 GROUP BY order_id) i ON i.order_id = o.id
         WHERE o.status = 'PAID'
         GROUP BY c.id, c.name
         ORDER BY c.id
        """);
```

The subquery adds up the lines per order. The join now finds exactly one row per order, so nothing
is repeated. Rule of thumb: **aggregate first, then join**.

> **The other correct fix: stay at the line level.** Keep the original joins and only count and add
> things that exist once per line: `COUNT(DISTINCT o.id) AS orders`,
> `SUM(i.quantity * i.unit_price_cents) AS revenue_cents`. It passes the tests, because here an
> order total is always the sum of its lines. It stops being right the day a total includes
> shipping or a discount. Never "fix" this with `SELECT DISTINCT` or `SUM(DISTINCT o.total_cents)`:
> Barbara has three different orders of 40.00, and `SUM(DISTINCT ...)` makes them 40.00.

### Fix 2: the big spenders

```bash
./demo --scenario big-spenders
```
```
     WHERE o.status = 'PAID'
       AND o.total_cents > ?
     GROUP BY c.id, c.name
     ORDER BY c.id
    -- ? = 10000

WHAT THE QUERY RETURNED (1 row)
  id  name  spent_cents
  --  ----  -----------
  1   Ada   15000

RESULT: WRONG ROWS, NO ERROR. The report shows 1 of the 2 customers who spent more than 100.00. Missing: Barbara. Ada is shown with 150.00 and spent 180.00.
```

The question is about a **total**: "customers whose orders add up to more than 100.00". The query
tests each **order**. Barbara's three orders of 40.00 all fail that test, so she never reaches the
grouping. Ada's 150.00 order passes and her 30.00 order does not, so her "total" is 150.00.

**The change.** In `ShopQueries.bigSpenders`, move the amount condition to `HAVING`:

```java
return new Query("""
        SELECT c.id, c.name, SUM(o.total_cents) AS spent_cents
          FROM customers c
          JOIN orders o ON o.customer_id = c.id
         WHERE o.status = 'PAID'
         GROUP BY c.id, c.name
        HAVING SUM(o.total_cents) > ?
         ORDER BY c.id
        """, minCents);
```

`WHERE` filters rows **before** they are grouped. `HAVING` filters the groups **after** the totals
are computed. `status = 'PAID'` is about one order, so it stays in `WHERE`.

(If you write `WHERE SUM(o.total_cents) > ?`, Postgres refuses: "aggregate functions are not allowed
in WHERE". That mistake is loud. The quiet one is filtering single rows by the same number.)

### After

Both demos print `RESULT: OK` and `./check 5` prints 5 of 5. Run `./check` too: Tasks 2 and 4 must
still be green.

---

## Task 6: Inspect NULL and Aggregates

### Step 1: comparing with NULL

```bash
./inspect "SELECT 1 = NULL AS a, NULL = NULL AS b, 1 <> NULL AS c, NULL IS NULL AS d, (1 = NULL) IS NULL AS e"
```
```
  a     b     c     d  e
  ----  ----  ----  -  -
  NULL  NULL  NULL  t  t
```

SQL has three truth values: true, false and **unknown**, which is shown as NULL. Any comparison
with NULL is unknown, even `NULL = NULL`. `WHERE`, `ON` and `HAVING` keep a row only when the
condition is true, so unknown behaves like false there. The only way to test for NULL is `IS NULL`
and `IS NOT NULL` (columns `d` and `e`).

### Step 2: IN and NOT IN

```bash
./inspect "SELECT 4 IN (1, 2) AS a, 4 NOT IN (1, 2) AS b, 4 NOT IN (1, 2, NULL) AS c, 1 NOT IN (1, 2, NULL) AS d"
```
```
  a  b  c     d
  -  -  ----  -
  f  t  NULL  f
```

Column `c` is Task 4 in one cell: with a NULL in the list, `NOT IN` is false (`d`) or unknown
(`c`), never true. `IN` is not hurt in the same way: `1 IN (1, 2, NULL)` is true as soon as one
value matches, and a row that matches nothing is dropped either way.

### Step 3: the plan of NOT EXISTS

```bash
./demo --scenario never-ordered --plan
```
```
      ->  Hash Anti Join  (cost=1.27..2.41 rows=2 width=14) (actual time=0.017..0.018 rows=2 loops=1)
            Hash Cond: (c.id = o.customer_id)
```

Postgres turns `NOT EXISTS` into an **anti join**: a join that keeps the customers for which no
order is found. Now `./inspect` the old query (`... WHERE c.id NOT IN (SELECT o.customer_id FROM
orders o) ...`). Its plan has no join at all, only this on the scan of `customers`:

```
          Filter: (NOT (ANY (id = (hashed SubPlan 1).col1)))
          Rows Removed by Filter: 8
```

A filter that removed all 8 customers. Postgres cannot turn `NOT IN` into an anti join, exactly
because of the NULL rule. On big tables that also makes `NOT IN` the slower one.

### Step 4: three kinds of COUNT

```bash
./inspect "SELECT COUNT(*), COUNT(customer_id), COUNT(DISTINCT customer_id) FROM orders"
```

The row is `12  11  6`.

| Expression | Counts | Here |
|---|---|---|
| `COUNT(*)` | rows | 12 orders |
| `COUNT(customer_id)` | rows where `customer_id` is not NULL | 11: the guest order is skipped |
| `COUNT(DISTINCT customer_id)` | different non-NULL values | 6 customers have ordered |

### Step 5: SUM and AVG skip NULL

```bash
./inspect "SELECT AVG(x) AS avg, SUM(x) AS sum, COUNT(x) AS count_x, COUNT(*) AS count_rows FROM (VALUES (10), (NULL), (20)) AS v(x)"
```
```
  avg                  sum  count_x  count_rows
  -------------------  ---  -------  ----------
  15.0000000000000000  30   2        3
```

Three rows, and the average is 15: 30 divided by the **2** values that are not NULL. Every
aggregate except `COUNT(*)` ignores NULL. If "no value" should count as zero, say so:
`AVG(COALESCE(x, 0))` is 10.

### Step 6: row multiplication, once more

You ran this in Task 5: order 3 appears three times. The rule: after a join to a table with several
rows per parent, only aggregate columns of that table. For the parent's columns, aggregate first.

### Step 7: WHERE and HAVING in the plan

```bash
./demo --scenario big-spenders --plan
```
```
      ->  HashAggregate  (cost=2.40..2.50 rows=3 width=22) (actual time=0.023..0.024 rows=2 loops=1)
            Group Key: c.id
            Filter: (sum(o.total_cents) > '10000'::bigint)
            Rows Removed by Filter: 2
            ->  Hash Join  ...
                  ->  Seq Scan on orders o  ...
                        Filter: (status = 'PAID'::text)
                        Rows Removed by Filter: 4
```

Read a plan from the inside out. First the scan of `orders` applies the `WHERE`: 4 orders that are
not PAID are removed. Then the join, then the grouping. Last, the `HashAggregate` applies the
`HAVING`: 2 **groups** (Grace and Edsger, 90.00 each) are removed. Two filters, two moments.

### What to take away

A `SELECT` is evaluated in this order, whatever order you type it in:

```
FROM / JOIN  ->  WHERE  ->  GROUP BY  ->  HAVING  ->  SELECT  ->  ORDER BY  ->  LIMIT
```

- `WHERE` after an outer join sees the NULL-filled rows (Task 2).
- Any comparison with NULL is unknown, and unknown is not true (Task 4).
- Aggregates run on the joined rows, so a join can multiply what they add up (Task 5).
- `WHERE` runs before the groups exist, `HAVING` after (Task 5).

---

# Part B: right rows, slowly

Part B runs on the big shop: 20,008 customers, 300,012 orders. The first Part B demo prints
`Loading the big shop...` and takes about 5 seconds longer.

## Task 7: Find Where the Time Goes, Then Rewrite the Query

"The query is slow, what do you do?" has an answer that is a list of steps. Do them in order.

### Step 1: get the real query and its parameters

```bash
./demo --scenario country-spend
```
```
    SELECT c.id, c.name,
           (SELECT COUNT(*)
              FROM orders o
             WHERE o.customer_id = c.id
               AND o.status = 'PAID') AS paid_orders,
           (SELECT COALESCE(SUM(o.total_cents), 0) ... the same WHERE ...) AS spent_cents
      FROM customers c
     WHERE c.country = ?
     ORDER BY c.id
    -- ? = 'IE'
```

Not "roughly this query". The exact SQL the application sends, and the value of every `?`: the same
query can be fast for one parameter and slow for another. At work you get it from the SQL log, from
`pg_stat_statements`, or from your APM tool.

### Step 2: EXPLAIN ANALYZE

```
    Index Scan using customers_pkey on customers c  (cost=0.29..1481100.68 rows=50 width=38) (actual time=42.683..1933.657 rows=50 loops=1)
      Filter: (country = 'IE'::text)
      Rows Removed by Filter: 19958
      SubPlan 1
        ->  Aggregate  (cost=14803.20..14803.21 rows=1 width=8) (actual time=19.328..19.328 rows=1 loops=50)
              ->  Seq Scan on orders o  (cost=0.00..14803.18 rows=9 width=0) (actual time=7.076..19.318 rows=6 loops=50)
                    Filter: ((customer_id = c.id) AND (status = 'PAID'::text))
                    Rows Removed by Filter: 300006
      SubPlan 2
        ->  (the same two steps again, for the second subquery)
    Execution Time: 1933.761 ms
```

`EXPLAIN` shows the plan. `EXPLAIN ANALYZE` runs the query and shows what really happened. Add
`--buffers` to the demo to see the pages each step touched.

### Step 3: find the step where the time goes

The total is 1,934 ms. The trap is on this line:

```
->  Seq Scan on orders o  (...) (actual time=7.076..19.318 rows=6 loops=50)
```

19 ms does not look like a problem. But **`loops=50`** means this step ran 50 times, and
`actual time` and `rows` are **per loop**. 50 x 19 ms = 965 ms. There are two such scans, one per
subquery: 1,930 ms. That is the whole query.

`SubPlan 1` and `SubPlan 2` are the two subqueries in the `SELECT` list. Each mentions `c.id`, the
customer of the outer row, so Postgres has to run it again for every customer.

### Step 4: compare rows

```
WHAT STANDS OUT
  Rows returned:             50
  Rows read from tables:     30,021,208   (600,424 read for every row returned)
  Table reads:
      Index Scan using customers_pkey on customers: read 20,008 rows, threw away 19,958 (Rows Removed by Filter)
      Seq Scan on orders (SubPlan 1): ran 50 times, read 15,000,600 rows, threw away 15,000,300 (Rows Removed by Filter)
      Seq Scan on orders (SubPlan 2): ran 50 times, read 15,000,600 rows, threw away 15,000,300 (Rows Removed by Filter)
  Estimate vs actual:        close. The worst step is Seq Scan on orders (SubPlan 2): expected 9 rows, got 6
  Pages touched (8 kB each): 1,030,554

RESULT: SLOW. Postgres read 30,021,208 rows to return 50. The orders table (300,012 rows) was read 100 times.
```

Two comparisons, every time:

- **Rows read against rows returned.** 30 million read, 50 returned.
- **Estimated against actual rows.** Close: the planner expected 9 orders per customer and got 6.
  So the planner is not confused. It did what the query asked for. The query is the problem.

### Step 5: choose a fix

There are four kinds of fix: rewrite the query, fetch less (Task 8), fix the statistics (Task 9),
or add an index ([Indexes](../03-indexes/)). What you found decides which. Here the same work is done
once per row, so the fix is a **rewrite** that does it once. An index on `orders (customer_id)`
would make each of the 100 lookups cheap, and would hide the real problem until someone runs the
report for a country with 3,000 customers. Fix the query first.

**The change.** You wrote the query this report needs in Task 2: every customer, with a count of
their PAID orders. In `ShopQueries.countrySpend`:

```java
return new Query("""
        SELECT c.id, c.name,
               COUNT(o.id)                     AS paid_orders,
               COALESCE(SUM(o.total_cents), 0) AS spent_cents
          FROM customers c
          LEFT JOIN orders o ON o.customer_id = c.id
                            AND o.status = 'PAID'
         WHERE c.country = ?
         GROUP BY c.id, c.name
         ORDER BY c.id
        """, country);
```

- `LEFT JOIN` with the status in `ON`: 10 of the 50 Irish customers never paid, and they must stay
  in the report. Put the status in `WHERE` and the first test fails with 40 rows instead of 50.
- `WHERE c.country = ?` is a condition on the **left** table. That is safe: it removes customers
  you do not want, not NULL-filled rows.
- `COALESCE(SUM(...), 0)`: `SUM` over no orders is NULL, and the report should say 0.

### Step 6: measure again

```bash
./demo --scenario country-spend
./check 7
```
```
            ->  Hash Right Join  (cost=448.73..14974.29 rows=450 width=34) (actual time=5.406..34.429 rows=330 loops=1)
                  Hash Cond: (o.customer_id = c.id)
                  ->  Seq Scan on orders o  (cost=0.00..14053.15 rows=179928 width=20) (actual time=0.007..25.921 rows=179928 loops=1)
                        Filter: (status = 'PAID'::text)
                  ->  Hash  ...
                        ->  Seq Scan on customers c  (cost=0.00..448.10 rows=50 width=22) (actual time=0.018..0.981 rows=50 loops=1)
                              Filter: (country = 'IE'::text)
    Execution Time: 34.657 ms

RESULT: OK. Every table was read once: 320,020 rows read to return 50, in 36.7 ms.
```

Every `loops=` is 1. Postgres puts the 50 Irish customers in a hash table, walks through `orders`
one time, and keeps the PAID ones that belong to one of the 50.

| | Before | After |
|---|---|---|
| Times `orders` was read | 100 | 1 |
| Rows read | 30,021,208 | 320,020 |
| Pages touched | 1,030,554 | 10,501 |
| Time | about 1,900 ms | about 35 ms |

It still reads 320,020 rows to return 50. That is what an index could improve next. At 35 ms it is
no longer a problem. `./check 7` prints 3 of 3.

---

## Task 8: Fetch One Page, Not the Whole Table

```bash
./demo --scenario recent-orders
```
```
    SELECT *
      FROM orders
     ORDER BY created_at DESC

EXPLAIN ANALYZE
    Index Scan Backward using idx_orders_created_at on orders  (cost=0.42..50376.60 rows=300012 width=230) (actual time=0.401..352.118 rows=300012 loops=1)
    Execution Time: 359.555 ms

WHAT THE APPLICATION RECEIVES
  Rows:                      300,012   (the page shows 20)
  Columns per row:           7   (the page shows 5: id, customer_id, status, total_cents, created_at)
  Size of the result:        70.3 MB

RESULT: FETCHES EVERYTHING. The page shows 20 orders. The query returned 300,012 rows, 70.3 MB.
```

Run the method on it. Rows read against rows returned: 300,012 and 300,012. Estimate against
actual: spot on. An index is used. **The plan has nothing wrong with it.** Postgres did exactly
what it was asked, as well as it can be done.

The problem is the question. The page shows 20 rows and 5 columns. The query asks for every order
with every column, including the shipping address and the notes (`width=230` bytes per row). 70 MB
travel to the application, become 300,012 Java objects, and 299,992 of them are thrown away. The
time Postgres reports is the small part: the transfer and the application's memory are not in it.

This is the most common "slow query" there is, and no index fixes it.

### The change

In `ShopQueries.recentOrders`:

```java
public static Query recentOrders(int pageSize) {
    return new Query("""
            SELECT id, customer_id, status, total_cents, created_at
              FROM orders
             ORDER BY created_at DESC
             LIMIT ?
            """, Math.clamp(pageSize, 1, MAX_PAGE_SIZE));
}
```

- **Name the columns.** Only what the page shows.
- **`LIMIT ?`**, bound as a parameter like any other value.
- **Cap it.** The page size comes from the client (`/api/orders/recent?size=...`). Without a cap,
  `size=1000000` brings the old query back. `Math.clamp` keeps it between 1 and 100.

### After

```bash
./demo --scenario recent-orders
./check 8
```
```
    Limit  (cost=0.42..3.78 rows=20 width=34) (actual time=0.079..0.487 rows=20 loops=1)
      ->  Index Scan Backward using idx_orders_created_at on orders  (cost=0.42..50376.60 rows=300012 width=34) (actual time=0.078..0.485 rows=20 loops=1)
    Execution Time: 0.509 ms

RESULT: OK. 20 rows, 973 bytes. Postgres read 20 rows and stopped.
```

| | Before | After |
|---|---|---|
| Rows returned | 300,012 | 20 |
| Bytes per row (`width`) | 230 | 34 |
| Size of the result | 70.3 MB | 973 bytes |
| Rows read | 300,012 | 20 |
| Pages touched | 302,321 | 23 |

5 of 5 pass, and `./check` now prints 33 passes and no failures.

> **Page 2?** `LIMIT 20 OFFSET 20` works and gets slower with every page, because Postgres has to
> walk past the rows it skips. The fix (keyset pagination) is its own project. The point here is
> smaller and comes first: never send a query without a limit to a table that grows.

---

## Task 9: Inspect "Fast in Dev, Slow in Prod"

### Steps 1 and 2: what your two fixes changed

The numbers are in the tables of Tasks 7 and 8. Two details:

- In `country-spend` the join is a `Hash Right Join`: the outer join of Task 3 again, sides
  swapped, keeping the 10 customers who never paid.
- In `recent-orders` the scan is the same `Index Scan Backward` before and after. The index was
  there all along. What is new is the `Limit` step on top: it asks the scan for one row at a time
  and stops asking after 20. A plan is a pipeline, and the top decides how much the bottom does.

### Step 3: a sort that does not fit in memory

There is no index on `total_cents`, so this needs a sort:

```bash
./inspect "SELECT * FROM orders ORDER BY total_cents DESC"
```
```
    Sort Method: external merge  Disk: 73376kB
```

The rows did not fit in the memory a sort may use (`work_mem`, 4 MB by
default), so Postgres wrote 73 MB of temporary files. Now fetch less:

```bash
./inspect "SELECT id, customer_id, status, total_cents, created_at FROM orders ORDER BY total_cents DESC LIMIT 20"
```
```
          Sort Method: top-N heapsort  Memory: 27kB
...
  Rows read from tables:     300,012   (15,000 read for every row returned)
```

With a `LIMIT`, Postgres keeps only the best 20 rows it has seen so far: 27 kB, not 73 MB. But
"Rows read" is still 300,012: without an index in that order, every row must be looked at. `LIMIT`
cut the sort and the transfer, not the reading. That last part is a job for an index.

### Step 4: the same SQL on 12 orders and on 300,012

```bash
./demo --scenario dev-vs-prod
```
```
QUERY A
                           "dev": 12 orders             "prod": 300,012 orders
  How the rows are found   Seq Scan                     Seq Scan
  Rows returned            3                            3
  Rows read                12                           300,012
  Pages touched            1                            10,303
  Time in the database     0.02 ms                      22.7 ms

QUERY B
                           "dev": 12 orders             "prod": 300,012 orders
  How the rows are found   Seq Scan                     Bitmap Heap Scan (idx_orders_created_at)
  Rows read                12                           1

RESULT: SAME SQL, DIFFERENT WORK. Query A has the same plan in both, and reads 300,012 rows in prod where it read 12 in dev.
        Query B has a different plan: Seq Scan in dev, Bitmap Heap Scan (idx_orders_created_at) in prod. The index is ignored on a table of one page.
```

**Query A** (orders of one customer, no index on `customer_id`): the same Seq Scan in both. On 12
rows it takes 0.02 ms and nobody can see a problem. On 300,012 rows it does 25,000 times the work.
A missing index is invisible in dev.

**Query B** (orders of one day, index on `created_at`): a **different plan**. On the small table
Postgres ignores the index: the whole table is one page, and reading one page is cheaper than
walking an index first. So on a dev database you cannot even check whether your index gets used.
The plan you see in dev is not the plan production will run.

### Step 5: stale statistics

The planner does not count rows when it plans. It uses **statistics**: a summary of each column
that `ANALYZE` writes down. This demo loads the small shop, runs `ANALYZE`, loads 300,000 orders on
top and does **not** run `ANALYZE` again.

```bash
./demo --scenario stale-stats
```
```
Loaded the small shop (12 orders) and ran ANALYZE.
Loaded 300,000 more orders in 2.5 s. ANALYZE has NOT run since.

  table        rows_now  planner_thinks  size        last_analyze        rows_changed_since
  orders       300,012   123,636         80 MB       11:09:57 (3 s ago)  300,000

  status     rows_now  share_now  planner_thinks
  PAID       179,928   60.0%      66.7%
  EXPIRED    90,000    30.0%      does not know this value
  CANCELLED  15,092    5.0%       16.7%
  PENDING    14,992    5.0%       16.7%
```

The statistics still describe 12 orders, and none of those 12 had status EXPIRED. Now 90,000 orders
have it, and the query asks for exactly those:

```
EXPLAIN ANALYZE, WITH STALE STATISTICS
    Nested Loop  (cost=0.28..11856.76 rows=1 width=34) (actual time=0.014..121.013 rows=90000 loops=1)
      ->  Seq Scan on orders o  (cost=0.00..11848.45 rows=1 width=20) (actual time=0.006..31.905 rows=90000 loops=1)
            Filter: (status = 'EXPIRED'::text)
      ->  Index Scan using customers_pkey on customers c  (cost=0.28..8.29 rows=1 width=30) (actual time=0.001..0.001 rows=1 loops=90000)
            Index Cond: (id = o.customer_id)

  Estimate vs actual:        FAR OFF. Seq Scan on orders: the planner expected 1 row, got 90,000 (90,000 times off)
  Pages touched (8 kB each): 280,303
```

On the `Seq Scan on orders` line, `rows=1` in the first bracket is the **estimate** and
`rows=90000` in the second is what **happened**. For "one order" the cheapest plan is a
`Nested Loop`: take the order, look its customer up by primary key. A good plan for 1 row. It ran
for 90,000: `loops=90000` on the index scan, 280,303 pages touched.

Then the demo runs `ANALYZE`:

```
EXPLAIN ANALYZE, AFTER ANALYZE
    Hash Join  (cost=648.18..14937.62 rows=90000 width=49) (actual time=3.529..42.518 rows=90000 loops=1)
      Hash Cond: (o.customer_id = c.id)
      ->  Seq Scan on orders o  (cost=0.00..14053.15 rows=90000 width=20) (actual time=0.005..26.210 rows=90000 loops=1)
      ->  Hash  ...

RESULT: STALE STATISTICS. The planner expected 1 EXPIRED order and got 90,000. It chose a Nested Loop: 280,303 pages, 138 ms.
        After ANALYZE the planner expected 90,000 and chose a Hash Join: 10,501 pages, 58.3 ms. Same SQL, same rows, different plan.
```

Estimate 90,000, actual 90,000. Knowing that, the planner reads `customers` once into a hash table:
10,501 pages, 27 times fewer. Nobody changed the SQL and nobody added an index.

Be honest about the time: 138 ms against 58 ms. Everything here is in memory, so a page touch is
cheap. On a production table that does not fit in memory, many of those 280,303 page touches are
disk reads, and a wrong estimate at the bottom of a plan with five joins gets worse at every level.
The page count is the number to trust.

### Step 6: see it in the catalog

```bash
./demo --scenario stale-stats --leave-stale
./inspect
```

`./inspect` prints the two tables you saw above, in full. `last_analyze` comes from
`pg_stat_user_tables` (`last_analyze` and `last_autoanalyze`), and `rows_changed_since` is
`n_mod_since_analyze` from the same view. Those are the columns to look at when you suspect stale
statistics.

Why exactly 1 row? The statistics list every status the planner knows, and their shares add up to
100%. A value that is not on the list gets what is left: nothing. And the planner never estimates
fewer than 1 row.

The next `./demo` of a Part B scenario loads the big shop again, with fresh statistics.

### Step 7: who runs ANALYZE

**Autovacuum** does: a background worker that runs `ANALYZE` on a table when about 10% of its rows
have changed. This project switches it off for its four tables (see `schema.sql`), so statistics
change only when a tool says so and your numbers match this guide. Never do that in production.

Autovacuum is not always soon enough: right after a bulk load, an import or a large `DELETE`, the
first queries run on the old statistics. A migration that loads data should end with `ANALYZE`.

### Check

`./check 9` prints 4 of 4. These tests pass from the start: they document the problem.

### Fast in dev, slow in prod: the reasons

| Reason | You saw it in |
|---|---|
| More rows: the same plan does more work | Step 4, query A |
| A different plan on a big table than on a small one | Step 4, query B |
| The query fetches everything, and "everything" grew | Task 8 |
| Stale statistics: the planner plans for data that is gone | Step 5 |
| Not reproduced here: an index that exists only in dev, a cold cache, other sessions holding locks, a slow network between application and database | |

---

## Task 10: The Steps to Improve a Slow Query

### The steps, in order

1. **Get the real query and its parameters.** From the log, `pg_stat_statements` or your APM tool.
   With an ORM, turn on SQL logging: the slow query is often not the one you think.
2. **Run `EXPLAIN (ANALYZE, BUFFERS)`** on data the size of production. (`ANALYZE` executes the
   statement: wrap an `UPDATE` or `DELETE` in a transaction and roll it back.)
3. **Find the step where the time goes.** Time and rows are per loop: multiply by `loops`.
4. **Compare rows.** Rows read against rows returned. Estimated against actual.
5. **Choose the fix** that matches what you found.
6. **Measure again**, and check that the result is still the same rows.

### Which fix

| What you found | Fix | In this project |
|---|---|---|
| Work repeated per row: `SubPlan`, `loops=50` | Rewrite the query: one join and `GROUP BY` | Task 7: 100 reads of `orders` became 1 |
| The result is far bigger than what is shown | Fetch less: name the columns, `LIMIT`, cap the page size | Task 8: 70.3 MB became 973 bytes |
| Estimated rows far from actual rows | `ANALYZE`, and find out why autovacuum did not do it | Task 9: Nested Loop became Hash Join |
| A sort that spills to disk | Fetch less (`LIMIT`, fewer columns), or an index in that order | Task 9 step 3 |
| A big table read to return a few rows, and the query is already as simple as it gets | An index | Task 9, query A: [Indexes](../03-indexes/) |

**Step 3 of the task.** Query A (`WHERE customer_id = ?`, 3 rows returned, 300,012 read) is the
case for an index: `CREATE INDEX ON orders (customer_id)`. You prove it with `EXPLAIN ANALYZE`
again: `Seq Scan` becomes `Index Scan` and "Rows read" drops to 3. On production-sized data, not on
12 rows.

**Step 4.** An index on `orders.status` would not have helped the country report: 179,928 of
300,012 orders are PAID, 60%. For a value that common, reading the whole table is cheaper than
jumping through an index, and the planner knows it. An index on `customer_id` would have made each
lookup cheap. The rewrite removed 99 of the 100 reads, which no index can do.

**Step 5.** Before you call it fixed: the plan on production-sized data with production's
parameters (the biggest country, not the one with 50 customers). The result still has the same
rows. And the number that started it, after the deploy: the response time, or the query's entry in
`pg_stat_statements`.

### When the answer is not an index

- The query repeats work: **rewrite it**.
- The query fetches too much: **fetch less**.
- The planner is misinformed: **fix the statistics**.
- The condition matches most of the table: an index would not be used.
- The table is small: a Seq Scan of a few pages is already the fastest plan.

### How to say it in an interview

"JOIN vs LEFT JOIN, and what happens with a WHERE on the right table?"

> "An inner join returns only rows that match on both sides. A left join returns every row of the
> left table, and fills the right-hand columns with NULL when there is no match. If I then put a
> condition on a right-hand column in the WHERE clause, the NULL-filled rows fail it, because a
> comparison with NULL is never true, so they are removed and the query behaves exactly like an
> inner join. Postgres even plans it as one. If the condition is meant to limit which rows are
> matched, it belongs in the ON clause. And after a left join I count a column of the right table,
> not star, or customers without orders show up as one."

"This query is slow. What do you do?"

> "First I get the exact query and its real parameters. Then I run EXPLAIN ANALYZE with BUFFERS on
> production-sized data and find the step where the time goes, keeping in mind that the numbers on
> a line are per loop. I compare two things: rows read against rows returned, and estimated rows
> against actual rows. What I find decides the fix. If the same work is repeated per row, like a
> subquery in the select list, I rewrite it as one join with GROUP BY. If the query fetches far
> more than the page shows, I select only the columns I need and add a LIMIT. If the estimate is
> far from the actual, the statistics are stale and I run ANALYZE. If a big table is scanned to
> find a few rows and the query is already simple, I add an index. Then I run EXPLAIN ANALYZE again
> to prove the plan changed, and check that the result did not."

"Fast in dev, slow in prod. Why?" Use the table of reasons at the end of Task 9: more rows, a
different plan, a query that fetches everything, stale statistics. Then say what you would do:
look at the plan in production, not guess.

Then be ready for the follow-ups in "Questions to Ask Afterwards" at the end of
[`INTERVIEW_TASKS.md`](INTERVIEW_TASKS.md).

---

## Clean up

```bash
docker compose down
```

The data is gone with the container. The next `docker compose up -d` and `./demo` load it again.
