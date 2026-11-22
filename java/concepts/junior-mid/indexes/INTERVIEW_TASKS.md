# Interview Tasks — Indexes

> **In `start/`, `./check` FAILS on purpose: 11 of 25 checks pass.** Tasks 2, 4, 6 and 8 turn the
> other 14 green. Tasks 1, 3, 5, 7, 9 and 10 are inspection tasks: you run things, look, and answer
> questions.

Do the tasks in order. Every fix is followed by an inspection task: a green test tells you the index
is used, the inspection tells you **why**, and the why is what an interviewer asks for.

Stuck, or want the explanation? The same task number in [`TASKS_GUIDED.md`](TASKS_GUIDED.md) walks
you through it with the expected output.

Commands are for macOS and Linux. On Windows type `.\demo`, `.\inspect` and `.\check`.

**Rules:** work in `start/`. Never edit, delete or disable a test. Indexes go in
`src/main/resources/indexes.sql` and nowhere else. Leave the two indexes that are already in that
file alone until Task 10.

---

## Task 1: See the Sequential Scan (10 min)

**Story:** *Customers type a tracking number into "Where is my parcel?" and the page takes longer
every month. The table has grown to one million orders. Before you change anything, find out what
the database does with that query.*

**Steps:**
1. `docker compose up -d`, then `./demo`. How many rows were returned? How many were read?
2. In the `EXPLAIN ANALYZE` block, find the scan type, the `Filter` line and `Rows Removed by
   Filter`. What does each one tell you?
3. `./demo --buffers`. How many pages were read? A page is 8 kB. How many megabytes is that, and
   how does it compare with the size of the table?
4. `./inspect`. Which indexes does `orders` have? Is `tracking_number` in any of them?
5. Open `ShopQueries.trackOrder`. That is the SQL the endpoint runs. Is anything wrong with the
   query itself?

**Acceptance Criteria** (`./check 1`, already passing: this test documents the problem)
- [ ] You can name the scan type and say what it does
- [ ] You can read `rows=`, `actual time=` and `Rows Removed by Filter` in a plan
- [ ] You can explain why the query gets slower as the table grows
- [ ] You can explain why the same query is fast on a developer's laptop
- [ ] `SeqScanIT.withoutAnIndexEveryRowIsRead` passes

**Hints:**
- `EXPLAIN` shows the plan. `EXPLAIN ANALYZE` runs the query and shows what really happened
- Compare "Rows read" with "Rows returned"

---

## Task 2: Fix It With a B-tree Index (10 min)

**Story:** *Make "Where is my parcel?" find one order without reading the other 999,999.*

**Acceptance Criteria** (`./check 2`)
- [ ] `indexes.sql` creates an index on `orders (tracking_number)`
      — `BTreeIndexIT.anIndexOnTrackingNumberExists`
- [ ] Finding an order by tracking number is an Index Scan that reads 1 row, not a Seq Scan
      — `BTreeIndexIT.trackingAnOrderUsesTheIndex`
- [ ] `./demo` prints `RESULT: OK`
- [ ] The baseline tests still pass (`./check`)

**Hints:**
- One line in `src/main/resources/indexes.sql`. The two indexes already in the file show the syntax
- You do not have to restart or run anything by hand: `./demo` and `./check` apply the file

---

## Task 3: Inspect the B-tree Index (15 min)

**Story:** *Your index passes the tests. Now prove you know what it does.*

**Steps:**
1. `./demo --buffers`. How many pages were read now? Compare with Task 1 step 3.
2. In the plan, `Filter` has been replaced by `Index Cond`. What is the difference between the two?
3. `./demo --no-indexes --buffers` hides every index for one run. Compare the first number of
   `cost=` in both plans, and the second. What is the planner comparing when it picks a plan?
4. `./inspect`. How big is the new index? How does that compare with the table? What did you pay
   for the speed?
5. Run `./demo` three more times, then `./inspect`. What happened to `scans` for your index? What
   would `scans = 0` after a month in production tell you?
6. The table has 1,000,000 rows and the lookup read 4 pages. If the table had 100 million rows,
   roughly how many pages would it read? Why?

**Acceptance Criteria** (no tests: you answer)
- [ ] You can explain what a B-tree is in two sentences
- [ ] You can explain why a lookup stays fast when the table grows 100 times
- [ ] You can say which comparisons a B-tree supports (`=`, `<`, `>`, `BETWEEN`, `ORDER BY`)
- [ ] You can say what the index costs

---

## Task 4: Fix It With a Composite Index (15 min)

**Story:** *The account page shows a customer's 5 newest orders. There is already an index with
`customer_id` and `created_at` in it, and the page is still slow for most customers. Find out why,
and add the index this query needs.*

**Acceptance Criteria** (`./check 4`)
- [ ] `indexes.sql` creates an index on `orders (customer_id, created_at)`, in that column order
      — `CompositeIndexIT.aCompositeIndexInTheRightOrderExists`
- [ ] A customer's 5 newest orders are found by reading fewer than 100 pages
      — `CompositeIndexIT.latestOrdersTouchFewPages`
- [ ] The rows come out of the index already newest first: 5 rows read, no Sort step
      — `CompositeIndexIT.latestOrdersNeedNoSort`
- [ ] `./demo --query latest-orders` prints `RESULT: OK`

**Hints:**
- Run `./demo --query latest-orders` first and read "Pages read"
- Look at the query in `ShopQueries.latestOrders`: one column is compared with `=`, the other is
  sorted on. Which one should the index be sorted by first?
- Keep `idx_orders_created_customer`. Another query needs it

---

## Task 5: Inspect the Composite Index (15 min)

**Story:** *Two indexes, the same two columns, opposite order. One works for this query and one
does not. Explain it.*

**Steps:**
1. `./demo --query latest-orders`. The plan says `Index Scan Backward`. Why backward? Why is there
   no `Sort` step although the query has an `ORDER BY`?
2. Compare "Pages read" before and after Task 4 (1,607 before). Both plans show an `Index Cond` on
   `customer_id`. Why did the old index need so many pages for the same condition?
3. Think of a phone book sorted by last name, then first name. Which of these can you look up
   quickly: everyone called Smith; everyone called Anna; Anna Smith? Map that to the two indexes.
4. `./demo --query spend --customer 4242`. It filters on `customer_id` only. Which index does it
   use? Which one can it not use, and why?
5. `./demo --query daily-sales` filters on `created_at` only. Which of the two indexes could serve
   it? (It is a Seq Scan for now. Task 6 is about why.)
6. Before Task 4, `./demo --query latest-orders --customer 1` was fast even with the wrong index.
   Customer 1 has 1,000 orders. Why did walking the index from the end work for them and not for
   customer 4242?
7. Would an index on `(customer_id)` alone pass Task 4? What extra step would the plan have?

**Acceptance Criteria** (no tests: you answer)
- [ ] You can state the leftmost-prefix rule
- [ ] You can say which queries an index on `(a, b)` serves: `a = ?`, `a = ? AND b = ?`,
      `a = ? ORDER BY b`, and which it does not: `b = ?`
- [ ] You can give the rule of thumb for column order: equality first, then range or sort
- [ ] You can explain why "it is fast for my test customer" proves nothing

---

## Task 6: Fix the Index That Exists but Is Skipped (20 min)

**Story:** *Two more slow endpoints, and this time the columns ARE indexed. Login looks a customer
up by email: there is an index on `email`. The daily sales report filters on `created_at`: there is
an index that starts with `created_at`. Both are sequential scans. Fix one in `indexes.sql` and the
other in `ShopQueries.dailySales`.*

**Acceptance Criteria** (`./check 6`)
- [ ] Finding a customer by `lower(email)` is an Index Scan that reads 1 row, whatever case the
      email was typed in — `SkippedIndexIT.customerByEmailUsesAnIndex`
- [ ] The daily sales SQL compares `created_at` directly, with no function or cast around the column
      — `DailySalesQueryTest.theColumnIsNotWrappedInAFunction`
- [ ] The range is half-open: from midnight of the day (included) to midnight of the next day
      (excluded) — `DailySalesQueryTest.theRangeIsHalfOpen`
- [ ] The daily sales report reads only that day's orders through an index
      — `SkippedIndexIT.dailySalesUsesAnIndex`
- [ ] `./demo --query email` and `./demo --query daily-sales` both print `RESULT: OK`

**Hints:**
- Run both demos and read the `Filter` line of each plan. What exactly is being compared?
- An index stores the values of what you indexed. You can index an expression, not only a column
- Emails are stored the way people typed them, so you cannot just drop `lower()` from the query
- For the report, do not add an index. Change the query so the column stands alone on one side:
  bind `day.atStartOfDay()` and `day.plusDays(1).atStartOfDay()`
- Why `>=` and `<`, and not `BETWEEN` with `23:59:59`?

---

## Task 7: Inspect More Ways to Lose an Index (15 min)

**Story:** *"There is an index on that column, why is it slow?" is one of the most common production
questions. Collect the usual causes.*

**Steps:**
1. `./demo --query email` and `./demo --query daily-sales`. Compare the `Index Cond` lines with the
   `Filter` lines you saw before Task 6. Two different fixes: when would you choose each?
2. `./demo --query like`. It searches `tracking_number LIKE '%ABC'`, and `tracking_number` has an
   index. What does the plan do? Use the phone book again: find everyone whose name ends in "son".
3. `./demo --query like --pattern 'TRKF63F%'`. A prefix this time. What does the plan do? (The
   answer surprises most people. The guided file explains it.)
4. `./demo --query numeric-id`. It is the `spend` query, but the customer id is bound as a
   `BigDecimal` instead of a `long`. Read the `Filter` line. Which side of the comparison did
   Postgres convert?
5. `./demo --query spend`. Same query, id bound as a `long`. Compare.
6. Make a list: every reason you have seen so far for an index not being used.

**Acceptance Criteria** (no tests: you answer)
- [ ] You can explain why a function or cast on the column disables a plain index
- [ ] You can name two fixes: an expression index, or rewriting the predicate
- [ ] You can explain why a leading wildcard cannot use a B-tree, and name what can (`pg_trgm`)
- [ ] You can explain how a parameter of the wrong type turns into a cast on the column

---

## Task 8: Fix It With a Partial Index and a Covering Index (20 min)

**Story:** *Two endpoints left. The warehouse screen shows the 50 oldest orders that are still
PENDING, and it reads almost the whole table to find them. The customer page shows what a customer
has spent in total, and for a big customer it visits the table a thousand times to add up one
column.*

**Acceptance Criteria** (`./check 8`)
- [ ] The pending queue is read from an index that only contains the PENDING orders
      — `PartialAndCoveringIndexIT.pendingQueueUsesAPartialIndex`
- [ ] The queue reads exactly the 50 rows it returns, and nothing is sorted or thrown away
      — `PartialAndCoveringIndexIT.pendingQueueReadsOnlyWhatItReturns`
- [ ] That index is at least 20 times smaller than the primary key index
      — `PartialAndCoveringIndexIT.thePartialIndexIsSmall`
- [ ] A customer's total spend is an Index Only Scan
      — `PartialAndCoveringIndexIT.customerSpendIsAnIndexOnlyScan`
- [ ] The table is never visited (`Heap Fetches: 0`)
      — `PartialAndCoveringIndexIT.customerSpendNeverVisitsTheTable`
- [ ] `./demo --query pending` and `./demo --query spend` both print `RESULT: OK`
- [ ] Task 4 is still green (`./check`)

**Hints:**
- Run `./demo --query pending` first. It uses an index and it is still slow. Read `Rows Removed by
  Filter`
- A `CREATE INDEX` statement can end with a `WHERE` clause. Which column should the index be sorted
  by, so that the queue comes out in order?
- For the spend query, the index finds the rows but `total_cents` is not in it. `INCLUDE (...)`
  adds a column to an index without making it part of the sort order
- You already have an index that starts with `customer_id`. Change it, rather than adding another

---

## Task 9: Inspect the Partial and the Covering Index (15 min)

**Story:** *Both are small changes with a big effect. Show where the effect comes from.*

**Steps:**
1. `./inspect`. Compare the size of the partial index with the other indexes on `orders`. How many
   orders does it hold? What happens to its size when the shop has 100 million delivered orders?
2. `./demo --query pending`. The plan has no `Filter` line, no `Index Cond` and no `Sort`. Where
   did `status = 'PENDING'` and `ORDER BY created_at` go?
3. `./demo --query by-status --status DELIVERED` filters on `status`. Can it use your partial
   index? Why not?
4. `./demo --query spend`. Find `Index Only Scan` and `Heap Fetches`. Compare "Pages read" with
   1,006 before the fix. Where did the other thousand pages go?
5. `./inspect`: how much bigger did `idx_orders_customer_created` get from `INCLUDE (total_cents)`?
6. `./demo --query spend --after-update`. It updates the customer's orders, runs the query, runs
   `VACUUM`, and runs the query again. What is `Heap Fetches` each time? Why would an index-only
   scan have to visit the table at all?
7. `./demo --query latest-orders`. It selects `status`, which is not in the index. Is it an Index
   Only Scan? What would you have to add, and is it worth it?

**Acceptance Criteria** (no tests: you answer)
- [ ] You can say when a partial index is the right tool, and the condition a query must meet to
      use it
- [ ] You can explain what "covering" means and what `INCLUDE` does
- [ ] You can explain what the visibility map is for and who keeps it up to date
- [ ] You can explain why `SELECT *` makes an index-only scan impossible

---

## Task 10: The Cost — Why Not Index Everything? (15 min)

**Story:** *Every query is fast now. A teammate suggests putting an index on every column "to be
safe". Collect the numbers, then answer.*

**Steps:**
1. `./demo --write-cost`. It inserts the same 200,000 orders into a table with no index and a table
   with six. Note the time and the sizes. Where does the extra time go?
2. `./demo --write-cost --rows 1000000`. Does the gap stay the same, shrink or grow?
3. `./demo --query by-status --status DELIVERED`. For this run the table has an index on `status`.
   Is it used? Read the `RESULT` line.
4. Repeat with `--status CANCELLED`, `--status SHIPPED` and `--status PENDING`. Note the share of
   rows, the plan and "Pages read" for each. Where is the tipping point?
5. For `CANCELLED` the index is used and the query still reads about as many pages as a Seq Scan.
   Every 20th order is cancelled. Why does that make the index nearly useless?
6. Run every query once (`./demo --query track`, `latest-orders`, `email`, `daily-sales`, `pending`,
   `spend`), then `./inspect`. Which index still has `scans = 0`? Delete its line from
   `indexes.sql` and run `./check`: does anything break? Now delete `idx_orders_created_customer`
   instead and run `./check` again. Put it back.
7. Look at the last block of `./inspect`. Where does the planner get "93% of the rows are
   DELIVERED" from? What happens to its decisions if that information is out of date?

**Acceptance Criteria** (`./check 10`, already passing: these tests document the cost)
- [ ] You can name three costs of an index
- [ ] You can explain why the planner ignores an index on a low-selectivity value
- [ ] You can explain how to find unused indexes in production
- [ ] You can give a rule for when a column deserves an index
- [ ] `IndexCostIT` passes: `indexesTakeMoreSpaceThanTheRows`, `aLowSelectivityValueIsASeqScan`,
      `aSelectiveValueUsesTheIndex`

---

## Questions to Ask Afterwards

Answer out loud, in full sentences, before reading the answer in brackets.

- A query is slow. What are your steps? (First find the query: slow query log, `pg_stat_statements`
  or the APM trace. Run it with `EXPLAIN ANALYZE` on production-sized data. Look for a Seq Scan on a
  big table, a large "Rows Removed by Filter", a Sort, and for estimates that are far from the
  actual rows. Then fix the cause: add or change an index, rewrite a predicate that hides the
  column, select fewer columns, or refresh statistics with `ANALYZE`. Run `EXPLAIN ANALYZE` again
  to confirm. Only after that think about caching or denormalising.)
- What is the difference between `EXPLAIN` and `EXPLAIN ANALYZE`? (`EXPLAIN` shows the plan and the
  planner's estimates without running the query. `EXPLAIN ANALYZE` runs it and adds the real row
  counts and times. Careful with `EXPLAIN ANALYZE` on an `UPDATE` or `DELETE`: it really runs.)
- What is a B-tree? (A balanced, sorted tree. Every page holds sorted keys and pointers; the leaves
  hold the indexed value and the location of the row. Finding a value means walking from the root
  to a leaf, 3 or 4 pages for millions of rows, because the depth grows with the logarithm of the
  row count. Because the leaves are sorted and linked, it also serves ranges and `ORDER BY`.)
- How do you choose which index to create? (Start from the query, not the table. Index the columns
  in `WHERE`, `JOIN` and `ORDER BY` of the queries that are slow or frequent. Prefer selective
  columns. In a composite index put equality columns first, then the range or sort column. Check
  with `EXPLAIN ANALYZE` that the planner uses it, and check later that it is still used.)
- What is the leftmost-prefix rule? (An index on `(a, b, c)` can be searched by `a`, by `a, b` or
  by `a, b, c`. It cannot be searched by `b` or `c` alone, because the index is sorted by `a` first.)
- What other index types are there besides B-tree? (In Postgres: Hash, for equality only and rarely
  worth it. GIN, for values that contain many items: arrays, JSONB, full-text search, and trigram
  search for `LIKE '%abc%'`. GiST, for geometry, ranges and nearest-neighbour. BRIN, a tiny index
  for huge tables whose values follow the physical order, like an append-only log by timestamp.
  Besides types there are variations: unique, composite, partial, expression and covering.)
- There is an index on the column and the database does not use it. Why? (A function or cast on the
  column: `lower(email)`, `date(created_at)`. A parameter of a different type, which makes the
  database cast the column. A leading wildcard in `LIKE`. The column is not the leftmost column of
  the index. The value is not selective, so a Seq Scan really is cheaper. The table is small. Or
  the statistics are stale and the planner misjudges the row count.)
- It is fast in dev and slow in prod. Why? (Dev has a thousand rows, where a Seq Scan is instant
  and the planner would not use an index anyway. Prod has millions, and different data: skew,
  hot values, rows nobody has in a test fixture. Prod may also be missing an index that someone
  created by hand in dev, have stale statistics, a cold cache, or other queries holding locks.
  Always look at the plan on production-sized data.)
- Why not index every column? (Every index must be updated on every `INSERT` and `DELETE` and on
  every `UPDATE` of its columns, so writes get slower. Indexes take disk and compete with the data
  for memory. More indexes mean more work for VACUUM and longer planning. And an index on a
  low-selectivity column, like a status that is the same for most rows, is never used.)
- What is selectivity? (The share of rows a condition matches. `tracking_number = ?` matches one
  row in a million: very selective, an index is ideal. `status = 'DELIVERED'` matches 93%: reading
  the whole table in order is cheaper than jumping to almost every row through an index. The
  planner estimates it from the statistics that `ANALYZE` collects.)
- What is a partial index and when do you use one? (An index with a `WHERE` clause, containing only
  the rows that match. Use it when queries always ask for a small, hot subset: unprocessed jobs,
  pending orders, non-deleted rows. It is small, cheap to maintain, and the query must contain the
  same condition for the planner to use it.)
- What is a covering index? (An index that contains every column the query needs, so the database
  answers from the index and never reads the table: an Index Only Scan. In Postgres you add
  payload columns with `INCLUDE`. It only skips the table for pages the visibility map marks as
  all-visible, which VACUUM maintains, so on a heavily updated table you still see heap fetches.)
- How would you add an index to a 500-million-row table in production? (`CREATE INDEX
  CONCURRENTLY`. A plain `CREATE INDEX` blocks every write to the table until it finishes, which
  could be many minutes. `CONCURRENTLY` lets writes continue; it takes longer, cannot run inside a
  transaction, and if it fails it leaves an `INVALID` index that you must drop and retry. Run it
  off-peak, watch disk space and replication lag, and remember that most migration tools wrap
  statements in a transaction, so it needs its own non-transactional migration.)
- Does Postgres create an index for a foreign key? (No. It indexes the primary key and unique
  constraints, not the referencing column. `orders.customer_id` had no index until you made one.
  That hurts twice: queries by customer, and every delete of a customer, which must look for
  orders that reference it.)
- How do you find indexes that are not used? (`pg_stat_user_indexes.idx_scan`, the "scans" column
  of `./inspect`. An index with zero scans over a representative period, that does not back a
  constraint, is a candidate to drop. Check the replicas too: their counters are separate.)

## Tips for Interviewers

- The most common weak answer is "I would add an index" with no column and no reason. Ask which
  column, in which order, and how they would prove it is used.
- Ask the candidate to predict the plan before running the demo, especially in Task 4. Many expect
  the existing `(created_at, customer_id)` index to work because "both columns are in it".
- In Task 6, see whether they read the `Filter` line. `lower(email)` in the plan is the whole
  answer. A candidate who reaches for a new index on the report instead of rewriting the predicate
  has not understood why the first one was skipped.
- Task 8 separates levels: a junior adds a second index for the spend query; a stronger candidate
  notices the Task 4 index already starts with `customer_id` and extends it.
- In Task 10, a strong candidate predicts that `DELIVERED` will not use the index before running
  it, and can explain `CANCELLED`: the index is used but touches almost every page.
- For senior candidates, go past the tasks: `CREATE INDEX CONCURRENTLY` and what an `INVALID` index
  is, index bloat, why a foreign key column needs an index, and when BRIN beats a B-tree.
