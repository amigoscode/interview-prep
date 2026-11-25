# Interview Tasks — SQL Traps and Slow Queries

> **In `start/`, `./check` FAILS on purpose: 17 of 33 checks pass.** Tasks 2, 4, 5, 7 and 8 turn the
> other 16 green. Tasks 1, 3, 6, 9 and 10 are inspection tasks: you run things, look, and answer
> questions.

Do the tasks in order. Every fix is followed by an inspection task: a green test tells you the
query is right, the inspection tells you **why**, and the why is what an interviewer asks for.

Stuck, or want the explanation? The same task number in [`TASKS_GUIDED.md`](TASKS_GUIDED.md) walks
you through it with the expected output.

Commands are for macOS and Linux. On Windows type `.\demo`, `.\inspect` and `.\check`.

**Rules:** work in `start/`. Never edit, delete or disable a test. All your changes go in
`src/main/java/com/amigoscode/sqltraps/shop/ShopQueries.java`. Do not add an index: none of these
problems needs one, and indexes have [their own project](../03-indexes/).

**Part A (Tasks 1 to 6)** runs on the small shop: 8 customers and 12 orders, all written out in
`src/main/resources/seed-small.sql`. Read that file first. **Part B (Tasks 7 to 10)** runs on the
big shop: the same rows plus 300,000 generated orders.

---

## Task 1: See the Report Drop Customers (10 min)

**Story:** *Sales asked for a list of every customer with the number of PAID orders they have, so
they can call the ones with zero. The report has been live for a month. Today someone noticed that
nobody on it has zero. Before you change anything, find out what the query does.*

**Steps:**
1. `docker compose up -d`, then `./demo`. How many customers are in the report? How many are in the
   shop?
2. Which customers are missing? Open `seed-small.sql`. What do the missing ones have in common?
3. `./inspect "SELECT c.id, c.name, o.id AS order_id, o.status FROM customers c LEFT JOIN orders o
   ON o.customer_id = c.id ORDER BY c.id, o.id"`. This is the join without the `WHERE`. What is in
   the order columns for Margaret and Alan?
4. `./demo --plan`. The query says `LEFT JOIN`. Which join does the plan say Postgres ran?
5. Open `ShopQueries.paidOrdersPerCustomer`. Which line removes the customers?

**Acceptance Criteria** (`./check 1`, already passing: this test documents the trap)
- [ ] You can say which customers are missing and why those ones
- [ ] You can say what a `LEFT JOIN` puts in the right-hand columns when there is no match
- [ ] You can explain why no error or warning was raised
- [ ] `LeftJoinTrapIT.aWhereOnTheRightTableDropsCustomers` passes

**Hints:**
- `WHERE` runs after the join, on the joined rows
- What is `NULL = 'PAID'`?

---

## Task 2: Fix the LEFT JOIN That Became an Inner Join (10 min)

**Story:** *Make the report show all 8 customers. A customer with no PAID order must be on it,
with 0. Fix `ShopQueries.paidOrdersPerCustomer`.*

**Acceptance Criteria** (`./check 2`)
- [ ] All 8 customers are in the report, also the 4 who have no PAID order
      — `PaidOrdersReportIT.everyCustomerIsInTheReport`
- [ ] A customer with no PAID order shows 0 (not 1), the others their real count
      — `PaidOrdersReportIT.customersWithoutAPaidOrderShowZero`
- [ ] Postgres runs it as an outer join (Left or Right), not as an Inner join
      — `PaidOrdersReportIT.postgresRunsAnOuterJoin`
- [ ] `./demo` prints `RESULT: OK`
- [ ] The baseline tests still pass (`./check`)

**Hints:**
- The condition on `orders` is fine. It is in the wrong clause
- A join condition can have more than one part: `ON a AND b`
- Keep `COUNT(o.id)`. Task 3 shows what `COUNT(*)` would do here

---

## Task 3: Inspect the Join (15 min)

**Story:** *The report is right. Now prove you know why, because "JOIN vs LEFT JOIN, and what
happens with a WHERE on the right table?" is a question people fail.*

**Steps:**
1. `./demo --plan`. Which join type does the plan show now? Why might it say `Right` when you wrote
   `LEFT`?
2. Look at the rows before they are grouped. Run
   `./inspect "SELECT c.id, c.name, o.id AS order_id, o.status FROM customers c LEFT JOIN orders o
   ON o.customer_id = c.id AND o.status = 'PAID' ORDER BY c.id, o.id"`. How many rows? What do the
   rows of Linus and Dennis look like, although both have an order?
3. Run the same statement with the status in `WHERE` instead of `ON`. How many rows? What does the
   plan's join type say?
4. Run it with `JOIN` instead of `LEFT JOIN` and the status in `ON`. Compare with step 3. For an
   inner join, does it matter whether a condition is in `ON` or in `WHERE`?
5. `./inspect "SELECT c.id, c.name, COUNT(*) AS all_rows, COUNT(o.id) AS orders FROM customers c
   LEFT JOIN orders o ON o.customer_id = c.id AND o.status = 'PAID' GROUP BY c.id, c.name ORDER BY
   c.id"`. Why is `all_rows` 1 for Margaret?
6. There is one useful `WHERE` on the right table of a `LEFT JOIN`. Add `WHERE o.id IS NULL` to the
   statement of step 2 (and drop the order columns from the `SELECT`). What does it return?

**Acceptance Criteria** (no tests: you answer)
- [ ] You can say what `JOIN` and `LEFT JOIN` each do with a row that has no match
- [ ] You can explain the difference between a condition in `ON` and the same condition in `WHERE`
- [ ] You can explain why a `WHERE` on the right table turns a `LEFT JOIN` into an inner join, and
      name the one condition that does not
- [ ] You can find the join type Postgres really used in a plan
- [ ] You can say why `COUNT(*)` is wrong after a `LEFT JOIN`

---

## Task 4: Fix NOT IN With a NULL (10 min)

**Story:** *Marketing wants the customers who have never ordered, to send them a voucher. The
report worked for a year. Last month the shop added guest checkout, and since then the report is
empty. Fix `ShopQueries.customersWhoNeverOrdered`.*

**Acceptance Criteria** (`./check 4`)
- [ ] The report finds Margaret (4) and Alan (7), although a guest order has a NULL `customer_id`
      — `NeverOrderedIT.findsTheTwoCustomersWithoutOrders`
- [ ] Postgres runs it as an Anti join, which is what `NOT EXISTS` becomes
      — `NeverOrderedIT.postgresRunsAnAntiJoin`
- [ ] `NeverOrderedIT.notInOnlyWorksWhileNoCustomerIdIsNull` still passes (it documents the trap)
- [ ] `./demo --scenario never-ordered` prints `RESULT: OK`

**Hints:**
- `./demo --scenario never-ordered` first. Then look at order 12 in `seed-small.sql`
- `x NOT IN (a, b, c)` means `x <> a AND x <> b AND x <> c`. What if `c` is NULL?
- `NOT EXISTS (SELECT 1 FROM ... WHERE ...)` asks whether a row exists. That is yes or no

---

## Task 5: Fix the Revenue That Is Counted Twice, and the Big Spenders (20 min)

**Story:** *Two more reports from the same author. Finance says the revenue report shows 820.00 and
the paid orders add up to 480.00. And the "big spenders" list (customers who spent more than
100.00 in total) is missing Barbara, who spent 120.00. Fix `ShopQueries.revenuePerCustomer` and
`ShopQueries.bigSpenders`.*

**Acceptance Criteria** (`./check 5`)
- [ ] Every PAID order is counted once: Ada 2, Grace 1, Barbara 3, Edsger 1
      — `RevenueReportIT.everyOrderIsCountedOnce`
- [ ] Revenue is the sum of the order totals: 180.00, 90.00, 120.00, 90.00, together 480.00
      — `RevenueReportIT.revenueIsNotInflated`
- [ ] Units bought are still right: 3, 4, 6, 2
      — `RevenueReportIT.unitsAreStillRight` (already passing: keep it that way)
- [ ] Barbara is found: three orders of 40.00 add up to more than 100.00
      — `BigSpendersIT.findsEveryCustomerWhoseTotalIsOverTheLimit`
- [ ] The amount shown is everything the customer spent: Ada 180.00, not only her 150.00 order
      — `BigSpendersIT.showsTheFullAmountSpent`
- [ ] `./demo --scenario revenue` and `./demo --scenario big-spenders` print `RESULT: OK`

**Hints:**
- Revenue: Grace has one PAID order with three lines. How many rows does that order become after
  the join to `order_items`? `./inspect` the join without the `GROUP BY`
- Revenue: make `order_items` one row per order **before** you join it (a subquery with its own
  `GROUP BY`), or count and add at the line level
- Big spenders: `WHERE` is applied to each order, before the groups exist. Which clause is applied
  to a group?

---

## Task 6: Inspect NULL and Aggregates (20 min)

**Story:** *Three fixes, one root cause each. Make the rules visible.*

**Steps:**
1. `./inspect "SELECT 1 = NULL AS a, NULL = NULL AS b, 1 <> NULL AS c, NULL IS NULL AS d,
   (1 = NULL) IS NULL AS e"`. Which columns are true? What are the others?
2. `./inspect "SELECT 4 IN (1, 2) AS a, 4 NOT IN (1, 2) AS b, 4 NOT IN (1, 2, NULL) AS c,
   1 NOT IN (1, 2, NULL) AS d"`. Explain column `c`. Why did that empty the report in Task 4?
3. `./demo --scenario never-ordered --plan`. Which join type is in the plan? Compare with the plan
   of the old query: `./inspect "SELECT c.id, c.name FROM customers c WHERE c.id NOT IN (SELECT
   o.customer_id FROM orders o) ORDER BY c.id"`.
4. `./inspect "SELECT COUNT(*), COUNT(customer_id), COUNT(DISTINCT customer_id) FROM orders"`.
   Three different numbers from one table. What does each one count?
5. `./inspect "SELECT AVG(x) AS avg, SUM(x) AS sum, COUNT(x) AS count_x, COUNT(*) AS count_rows
   FROM (VALUES (10), (NULL), (20)) AS v(x)"`. Is the average 10 or 15? Why?
6. `./inspect "SELECT o.id AS order_id, o.total_cents, i.id AS line_id, i.quantity FROM orders o
   JOIN order_items i ON i.order_id = o.id WHERE o.customer_id = 2 AND o.status = 'PAID'"`. How
   many times does order 3 appear? What would `SUM(o.total_cents)` be? What would `SUM(i.quantity)`
   be, and why was that one right all along?
7. `./demo --scenario big-spenders --plan`. The plan has two `Filter` lines. Which one comes from
   `WHERE`, which one from `HAVING`, and which runs first?

**Acceptance Criteria** (no tests: you answer)
- [ ] You can explain three-valued logic in one sentence, and say how to test for NULL
- [ ] You can explain why `NOT IN` with a NULL in the list returns no rows, and why `IN` does not
      have the same problem
- [ ] You can say what `COUNT(*)`, `COUNT(column)` and `COUNT(DISTINCT column)` each count
- [ ] You can say what `SUM` and `AVG` do with NULL
- [ ] You can explain row multiplication and name two ways to avoid it
- [ ] You can state the difference between `WHERE` and `HAVING`

---

## Task 7: Find Where the Time Goes, Then Rewrite the Query (25 min)

**Story:** *The Irish sales team opens "customers in my country with their paid orders and total
spend" every morning. It returns 50 rows and takes seconds. The rows are right. Find out where the
time goes, using a method you could repeat on any query, then fix
`ShopQueries.countrySpend`.*

**The method:**
1. **Get the real query and its parameters.** `./demo --scenario country-spend` prints both.
2. **`EXPLAIN (ANALYZE, BUFFERS)`.** The demo runs it for you. Add `--buffers` to see pages.
3. **Find the step where the time goes.** Read `actual time` and `loops` on every line.
4. **Compare rows.** Rows read against rows returned. Estimated rows against actual rows.
5. **Choose a fix:** rewrite the query, fetch less, fix the statistics, or add an index.
6. **Measure again.**

**Acceptance Criteria** (`./check 7`)
- [ ] The report still returns the 50 Irish customers with the right numbers, also the 10 who never
      paid — `CountrySpendIT.returnsEveryCustomerOfTheCountry` (already passing: keep it that way)
- [ ] The orders table is read once, not once per customer: no SubPlan in the plan
      — `CountrySpendIT.theOrdersTableIsReadOnce`
- [ ] Postgres reads no more rows than the two tables hold (320,020)
      — `CountrySpendIT.readsNoMoreRowsThanTheTablesHold`
- [ ] `./demo --scenario country-spend` prints `RESULT: OK`

**Hints:**
- `loops=50` on a line means that step ran 50 times. Its `actual time` and `rows` are per loop:
  multiply
- A subquery in the `SELECT` list that mentions the outer row runs once per outer row
- You wrote the query this report needs in Task 2. It needs one more line
- Careful: 10 of the 50 customers never paid. The first test catches the Task 1 trap

---

## Task 8: Fetch One Page, Not the Whole Table (15 min)

**Story:** *The admin screen "recent orders" shows the 20 newest orders: id, customer, status,
total, date. It takes longer every month and the application's memory graph has a spike every time
someone opens it. Fix `ShopQueries.recentOrders`.*

**Acceptance Criteria** (`./check 8`)
- [ ] The query returns 20 rows, the newest first
      — `RecentOrdersIT.returnsOnePageNewestFirst`
- [ ] Postgres stops after one page: a Limit step, and 20 rows read from the table
      — `RecentOrdersIT.postgresStopsAfterOnePage`
- [ ] Only the 5 columns the page shows are fetched, not the address and the notes
      — `RecentOrdersIT.fetchesOnlyTheColumnsThePageShows`
- [ ] The SQL names its columns (no `SELECT *`) and ends with `LIMIT ?`, bound to the page size
      — `RecentOrdersQueryTest.namesItsColumnsAndHasALimit`
- [ ] The page size is capped: a client asking for 1,000,000 rows gets 100, and 0 becomes 1
      — `RecentOrdersQueryTest.thePageSizeIsCapped`
- [ ] `./demo --scenario recent-orders` prints `RESULT: OK`

**Hints:**
- `./demo --scenario recent-orders` first. Read "WHAT THE APPLICATION RECEIVES"
- The method has a `pageSize` parameter that the query ignores
- `Math.clamp(value, min, max)`, and `ShopQueries.MAX_PAGE_SIZE`
- Page 2 and beyond are a different problem (pagination) with its own project. Do the first page

---

## Task 9: Inspect "Fast in Dev, Slow in Prod" (20 min)

**Story:** *"It was fast on my machine." Collect the evidence for the three reasons you can
reproduce here: how much is fetched, how many rows there are, and what the planner believes.*

**Steps:**
1. `./demo --scenario country-spend`. Compare with Task 7: rows read, the number of times `orders`
   was read, pages. Which join type is in the plan, and where did you meet it before?
2. `./demo --scenario recent-orders`. The scan is the same `Index Scan Backward` as before your
   fix. What stopped it after 20 rows? Compare rows, columns and bytes with Task 8.
3. `./inspect "SELECT * FROM orders ORDER BY total_cents DESC"`. There is no index on
   `total_cents`. Find `Sort Method`. Now run
   `./inspect "SELECT id, customer_id, status, total_cents, created_at FROM orders ORDER BY
   total_cents DESC LIMIT 20"`. What changed in `Sort Method`, and what did not change in
   "Rows read"?
4. `./demo --scenario dev-vs-prod`. Query A has the same plan on 12 orders and on 300,012. What is
   different? Query B has a different plan. Why does Postgres ignore the index in "dev"?
5. `./demo --scenario stale-stats`. In the first plan, compare `rows=` in the first bracket with
   `rows=` in the second on the `Seq Scan on orders` line. Which join did the planner choose, and
   which one after `ANALYZE`? Compare "Pages touched".
6. `./demo --scenario stale-stats --leave-stale`, then `./inspect`. Read `planner_thinks`,
   `last_analyze` and `rows_changed_since` for `orders`, and the `EXPIRED` row of the status table.
   Why did the planner expect exactly 1 row?
7. In production nobody runs `ANALYZE` by hand. Who does? When is that not soon enough?

**Acceptance Criteria** (`./check 9`, already passing: these tests document the problem)
- [ ] You can give four reasons a query is fast in dev and slow in prod
- [ ] You can say what statistics are, who gathers them, and how you see when they were gathered
- [ ] You can point at estimated and actual rows in a plan, and say why a big gap matters
- [ ] You can explain why you cannot judge a query, or an index, on a table with 12 rows
- [ ] `SmallVsBigIT` and `StaleStatisticsIT` pass

---

## Task 10: The Steps to Improve a Slow Query (15 min)

**Story:** *"This query is slow. What do you do?" was asked in a mock interview and in a real one,
and the answer was weak both times. Write yours down, in order, and say when the answer is not an
index.*

**Steps:**
1. Write the method of Task 7 from memory, as six short steps. Check it against Task 7.
2. For each of these, name the step of the plan that gave the problem away, and the kind of fix:
   - `./demo --scenario country-spend` before your fix
   - `./demo --scenario recent-orders` before your fix
   - `./demo --scenario stale-stats`
3. None of the three fixes was an index. Query A of `./demo --scenario dev-vs-prod` is different:
   the query is as simple as it can be, it returns 3 rows and reads 300,012. What is the fix
   there? Do not build it here: say which index, and how you would prove that it is used.
4. A colleague says "add an index on `orders.status` for the country report". Before your Task 7
   rewrite, would that have fixed it? How many of the 300,012 orders are PAID?
5. You fixed the query and it is fast on your laptop. Name three things you check before you say
   it is fixed in production.

**Acceptance Criteria** (no tests: you answer)
- [ ] You can list the steps in order without looking
- [ ] You can name four kinds of fix and give one situation for each
- [ ] You can say when an index is the wrong answer
- [ ] You can say how you prove a fix worked

---

## Questions to Ask Afterwards

Answer out loud, in full sentences, before reading the answer in brackets.

- What is the difference between `JOIN` and `LEFT JOIN`? (`JOIN`, an inner join, returns a row only
  when both sides match. `LEFT JOIN` returns every row of the left table: when the right table has
  no match, the right-hand columns are NULL. So a left join never loses left-hand rows, and can add
  rows when there are several matches.)
- What happens if you put a `WHERE` condition on the right table of a `LEFT JOIN`? (The rows that
  had no match have NULL in that column, the comparison is not true for NULL, and `WHERE` removes
  them. The result is exactly an inner join, and Postgres even plans it as one. If the condition
  should limit which right-hand rows are matched, it belongs in the `ON` clause. The one exception
  is `WHERE right.id IS NULL`, which keeps only the rows without a match on purpose.)
- `ON` or `WHERE`: does it matter? (For an inner join, no: the result is the same. For an outer
  join, yes. `ON` decides which rows match. `WHERE` runs afterwards and decides which joined rows
  survive.)
- Why did `NOT IN (subquery)` return no rows? (Three-valued logic. `x NOT IN (1, 2, NULL)` is
  `x <> 1 AND x <> 2 AND x <> NULL`. The last part is unknown, so the whole thing is unknown or
  false, never true, and `WHERE` only keeps true. Use `NOT EXISTS`, or make sure the subquery
  cannot return NULL.)
- What is `NULL = NULL`? (Unknown, shown as NULL. Not true and not false. NULL means "no value", so
  any comparison with it is unknown. You test for it with `IS NULL` and `IS NOT NULL`.)
- `COUNT(*)`, `COUNT(column)`, `COUNT(DISTINCT column)`? (`COUNT(*)` counts rows. `COUNT(column)`
  counts rows where the column is not NULL. `COUNT(DISTINCT column)` counts the different non-NULL
  values. After a `LEFT JOIN`, `COUNT(*)` counts the NULL-filled row of a customer without orders
  as 1.)
- What do `SUM` and `AVG` do with NULL? (They skip it. `AVG` of 10, NULL, 20 is 15, not 10. If a
  NULL should count as zero, write `AVG(COALESCE(x, 0))`. `SUM` over no rows is NULL, not 0, which
  is why reports wrap it in `COALESCE`.)
- Your revenue report is too high. Where do you look first? (At the joins. A join to a table with
  several rows per order, like order lines or payments, repeats the order once per line, and `SUM`
  of an order column adds it once per line. Aggregate the many-side to one row per order before
  joining, or sum a column of the many-side. `SELECT DISTINCT` hides the symptom and does not fix a
  `SUM`.)
- What is the difference between `WHERE` and `HAVING`? (`WHERE` filters rows before they are
  grouped and cannot see an aggregate. `HAVING` filters groups after aggregation and can. "Orders
  over 100" is `WHERE`. "Customers whose orders add up to over 100" is `HAVING`.)
- In what order is a `SELECT` evaluated? (`FROM` and `JOIN`, then `WHERE`, then `GROUP BY`, then
  `HAVING`, then the `SELECT` list, then `ORDER BY`, then `LIMIT`. That order explains most of the
  traps in this project.)
- This query is slow. What are your steps? (Get the exact query and its real parameters. Run
  `EXPLAIN (ANALYZE, BUFFERS)` on data the size of production. Find the step where the time goes,
  remembering that time and rows are per loop. Compare rows read with rows returned, and estimated
  rows with actual rows. Then choose the fix: rewrite the query, fetch fewer rows and columns,
  refresh the statistics, or add an index. Run `EXPLAIN ANALYZE` again to prove the plan changed.)
- When is the answer not an index? (When the query does the same work many times, like a subquery
  per row: rewrite it. When it fetches far more than it shows: select fewer columns and add a
  `LIMIT`. When the planner's estimate is wrong: `ANALYZE`. When the condition matches most of the
  table, where an index would not be used anyway.)
- It is fast in dev and slow in prod. Why? (Dev has a few hundred rows: every plan is fast, and the
  planner ignores indexes on tiny tables, so you cannot even see whether your index would be used.
  Prod has different data: more of it, skewed, with values dev never had. Statistics may be stale
  after a bulk load or a big delete. Prod may be missing an index, have a cold cache, or have other
  sessions holding locks. Always look at the plan on production-sized data.)
- What are statistics, and what happens when they are stale? (A summary of each column that
  `ANALYZE` writes down: how many rows, the most common values and their share, a histogram. The
  planner never counts rows: it estimates from the statistics and picks the cheapest plan for that
  estimate. With stale statistics it plans for data that is no longer there, for example a Nested
  Loop for "1 row" that turns out to be 90,000. Autovacuum runs `ANALYZE` when about 10% of a table
  has changed. After a bulk load, run it yourself.)
- How do you see that an estimate is wrong? (In `EXPLAIN ANALYZE`, each line has the estimated
  `rows=` in the first bracket and the actual `rows=` in the second. A difference of 10 times or
  more on a step near the bottom of the plan is the place to look.)
- What is wrong with `SELECT *`? (It fetches columns nobody uses, often the wide ones: more
  bytes read, sent and held in memory. It prevents an index-only scan. And it breaks silently when
  someone adds a column. Name the columns.)
- What does a `LIMIT` change in the plan? (Postgres can stop early. With an index in the right
  order it reads only that many rows. Without one it still reads everything, but it keeps only the
  top N in memory instead of sorting the whole result, which may otherwise spill to disk.)
- "SQL select with indexes": how does an index help a `SELECT`? (It lets Postgres find the rows
  that match a `WHERE`, a join or an `ORDER BY` without reading the whole table. That is the
  [Indexes](../03-indexes/) project: B-tree, composite, partial, covering, and why an index is
  sometimes not used.)
- What is a CTE, and what is a window function? (A CTE, `WITH name AS (SELECT ...)`, names a
  subquery so the main query reads top to bottom. The "aggregate before joining" subquery of Task 5
  can be written as one. A window function, such as `ROW_NUMBER() OVER (PARTITION BY customer_id
  ORDER BY created_at DESC)`, computes a value across related rows without collapsing them the way
  `GROUP BY` does: "the latest order of each customer" is the classic use. This project does not
  practise them.)

## Tips for Interviewers

- "JOIN vs LEFT JOIN" gets a textbook answer from almost everyone. The follow-up, "and if I add
  `WHERE orders.status = 'PAID'`?", is what separates candidates. Ask them to predict the row count
  of `./demo` before running it.
- In Task 2, watch for `COUNT(*)`. A candidate who moves the condition to `ON` and then counts with
  `COUNT(*)` gets 1 for every customer without orders. A strong candidate explains `COUNT(o.id)`
  without being asked.
- In Task 4, a candidate may patch `NOT IN` with `WHERE customer_id IS NOT NULL` in the subquery.
  That is correct, and fragile: ask what happens when the next developer copies the query without
  the filter. The plan test asks for an anti join.
- In Task 5, `SELECT DISTINCT` or `SUM(DISTINCT o.total_cents)` is the classic wrong fix. Barbara
  has three orders of the same amount: ask what `SUM(DISTINCT ...)` makes of them.
- In Task 7, do not accept "I would add an index" as the first sentence. Ask which step of the plan
  is slow and how they know. `loops=50` is the whole answer, and many candidates read the per-loop
  time as the total.
- In Task 7, a candidate who rewrites the subqueries as a `LEFT JOIN` with the status in `WHERE`
  has just repeated Task 1. The first test fails with 40 rows instead of 50. Let them find it.
- In Task 9, a strong candidate predicts the Nested Loop before seeing the plan: "if it thinks one
  row comes out, it will look that one row up by primary key".
- For senior candidates, go past the tasks: why `ANALYZE` after a bulk load belongs in the
  migration, `pg_stat_statements` for finding the slow query in the first place, parameter-dependent
  plans, and why `OFFSET 100000` is slow.
