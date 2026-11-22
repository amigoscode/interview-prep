# SQL Traps and Slow Queries

> **In `start/`, `./check` FAILS on purpose: 17 of 33 checks pass.** That is the exercise, not a
> broken checkout. Tasks 2, 4, 5, 7 and 8 turn the other 16 green.

A shop has 8 customers. The "paid orders per customer" report shows 4 of them. Nothing failed, no
error was logged, and the query says `LEFT JOIN`. This project lets you watch four queries return
the wrong rows without any error, fix each one, and then learn a method for the other kind of bad
query: the one that returns the right rows, slowly.

It answers the interview questions students most often get wrong:

- "What is the difference between `JOIN` and `LEFT JOIN`? What happens if you put a `WHERE` on the
  right table?"
- "Why does `NOT IN` return nothing?"
- "`WHERE` or `HAVING`? `COUNT(*)` or `COUNT(column)`?"
- "This query is slow. What are your steps?"
- "It is fast in dev and slow in prod. Why?"

**Part A (Tasks 1 to 6): wrong rows, no error.** Four reports on a shop so small that you can check
every answer by reading the rows.

**Part B (Tasks 7 to 10): right rows, slowly.** A method you can repeat, on a shop with 300,000
orders. None of the fixes in this project is an index: you rewrite the query, fetch less, or fix
the statistics. Indexes have their own project, [Indexes](../03-indexes/), which also teaches how to
read `EXPLAIN ANALYZE` line by line. Do that one first if a query plan is new to you.

## Tech Stack

- **Java 25**, **Spring Boot 4**, Spring JDBC (`JdbcClient`, plain SQL: the SQL you read is the SQL that runs)
- **PostgreSQL 17** in Docker
- JUnit 5, AssertJ, Testcontainers

## What you need

- JDK 25
- Docker, running

## Getting Started

Work in `start/`. Commands are shown for macOS and Linux; on Windows type `.\demo` where you see
`./demo` (same for `inspect` and `check`).

```bash
cd start
docker compose up -d      # Postgres on port 5447
./demo                    # watch the report lose 4 of the 8 customers
./check                   # 17 of 33 pass: that is your starting point
```

The first run downloads Maven and the dependencies, so it takes a minute or two.

When you are finished: `docker compose down`.

## The data

There are two data sets, and the tools load the one a scenario needs.

**The small shop**: 8 customers, 5 products, 12 orders, 18 order lines. Every row is written out in
[`start/src/main/resources/seed-small.sql`](start/src/main/resources/seed-small.sql). Part A runs
on it. Read that file once: it is one screen, and it is the answer key.

| Customer | Orders |
|---|---|
| 1 Ada | 2 PAID (150.00 and 30.00) |
| 2 Grace | 1 PAID (90.00), 1 CANCELLED |
| 3 Linus | 1 PENDING |
| 4 Margaret | none |
| 5 Dennis | 1 CANCELLED |
| 6 Barbara | 3 PAID (40.00 each) |
| 7 Alan | none |
| 8 Edsger | 1 PAID (90.00), 1 PENDING |
| (no customer) | order 12: a guest checkout, `customer_id` is NULL |

**The big shop**: the small shop plus 20,000 customers, 300,000 orders and 600,000 order lines,
generated inside Postgres with `generate_series`. Part B runs on it. Loading it takes about 5
seconds, and happens only when the big shop is not already loaded.

Money is stored in cents: `15000` is 150.00.

## The three tools

### `./demo` — see what happens

Loads the shop, runs one scenario and prints the evidence, ending with a `RESULT:` line.

For a report (Part A) the evidence is the question, the SQL, the rows it returned, the rows it
should return and the difference. For a slow query (Part B) it is the plan, the facts pulled out of
the plan, and the counts.

```
./demo                               paid-orders: the report that loses customers
./demo --scenario never-ordered      paid-orders | never-ordered | revenue | big-spenders |
                                     country-spend | recent-orders | dev-vs-prod | stale-stats
./demo --scenario revenue --plan     a report, with its EXPLAIN ANALYZE
./demo --scenario country-spend      a slow query: the plan and where the time goes
./demo --scenario stale-stats        the same query before and after ANALYZE
./demo --help
```

### `./inspect` — make the invisible visible

`./inspect "SELECT ..."` runs **any read-only SELECT** against the shop and prints:

- the first 20 rows it returns
- the plan Postgres used (`EXPLAIN ANALYZE`, exactly as Postgres prints it)
- the facts that matter, pulled out of the plan: the join type that really ran, rows read against
  rows returned, estimated against actual rows, whether a sort spilled to disk, where the time went
- the four tables: rows counted now, rows the planner assumes, size, when `ANALYZE` last ran

`./inspect` with no argument shows the tables and what the planner believes about `orders.status`.

It refuses anything that is not one read-only `SELECT`, and runs the statement in a `READ ONLY`
transaction. Put the statement in double quotes. It runs against whatever `./demo` loaded last.

### `./check` — are the acceptance criteria met?

`./check 2` runs the tests for Task 2 and prints one line per criterion. `./check` runs everything.
`./mvnw verify` runs the same tests the usual Maven way.

The tests are the acceptance criteria. **Never edit, delete or disable a test.** They do not measure
time. They check the rows a query returns (exact ids and amounts from the small shop) and the shape
of its plan (the join type, the steps, how many rows were read).

## The code

```
start/src/main/
├── resources/
│   ├── schema.sql                the four tables. Nothing to change here
│   ├── seed-small.sql            the small shop, row by row. READ THIS
│   └── seed-big.sql              the 300,000 generated orders
└── java/com/amigoscode/sqltraps/
    ├── shop/
    │   ├── ShopQueries.java      every SQL query of the shop. ALL YOUR WORK IS IN THIS FILE
    │   ├── ShopRepository.java   runs them with JdbcClient
    │   └── ShopController.java   the HTTP endpoints
    ├── db/                       loading the data, reading plans. Nothing to change here
    └── demo/                     the demo and inspect tools. Nothing to change here
```

Six queries in `ShopQueries` have a `TODO Task N` comment. The other three are fine.

Two things about the database are unusual, and both are there so that your numbers match the guide:
autovacuum is switched off for the four tables, so statistics change only when a tool runs
`ANALYZE`; and `ANALYZE` reads every order instead of a sample. `schema.sql` explains both. The
plans are taken with parallel query and JIT switched off, for the same reason.

## Two ways to work

- **[`INTERVIEW_TASKS.md`](INTERVIEW_TASKS.md)**: challenge mode. A story, acceptance criteria and
  hints for each task. Use this if you want to work it out yourself.
- **[`TASKS_GUIDED.md`](TASKS_GUIDED.md)**: the same ten tasks, step by step, with the output you
  should see and an explanation of why. Use this to learn the concept, or when you are stuck.

Both files use the same task numbers.

| Task | What you do | Checked by |
|---|---|---|
| 1 | See the report drop customers | `./check 1` (passes: it documents the trap) |
| 2 | Fix the `LEFT JOIN` that became an inner join | `./check 2` |
| 3 | Inspect the join: `ON` vs `WHERE`, and the join type in the plan | you answer questions |
| 4 | Fix `NOT IN` with a NULL | `./check 4` |
| 5 | Fix the revenue that is counted twice, and `WHERE` vs `HAVING` | `./check 5` |
| 6 | Inspect NULL and aggregates | you answer questions |
| 7 | Slow query: find where the time goes, then rewrite it | `./check 7` |
| 8 | Slow query: fetch one page, not the whole table | `./check 8` |
| 9 | Inspect "fast in dev, slow in prod": table size and stale statistics | `./check 9` (passes: it documents the problem) |
| 10 | The steps to improve a slow query, in order | you answer questions |

## Solution

`solution/` is the same project with every task done: `cd solution && ./check` prints 33 passes and
no failures.

`start/` and `solution/` use the same database (port 5447). That is fine: every `./demo` loads the
data it needs.

To start again from scratch: `git checkout -- start` (this throws away your changes in `start/`).

<details>
<summary>Spoiler: the traps and the fixes</summary>

**Trap 1, the LEFT JOIN that became an inner join (Task 2).** `LEFT JOIN orders o ... WHERE
o.status = 'PAID'`. A customer with no order comes out of the LEFT JOIN with NULL in every order
column. `NULL = 'PAID'` is not true, so `WHERE` throws that row away. Postgres knows this and runs
the query as a plain inner join. Fix: put the condition in the `ON` clause, where it decides which
orders match, not which rows survive.

**Trap 2, NOT IN with a NULL (Task 4).** `id NOT IN (SELECT customer_id FROM orders)`. One guest
order has `customer_id` NULL. `4 NOT IN (1, 2, NULL)` is "4 is not 1, and not 2, and not
(unknown)", which is unknown, never true. So no row passes. Fix: `NOT EXISTS`, which asks "is there
a matching row?" and can only answer yes or no.

**Trap 3, row multiplication (Task 5).** Join `orders` to `order_items` and an order with three
lines becomes three rows. `SUM(o.total_cents)` adds the order total three times and `COUNT(*)`
counts lines, not orders. Fix: add up the lines per order in a subquery first, then join, so each
order is one row. (Or count and sum at the line level: `COUNT(DISTINCT o.id)` and
`SUM(i.quantity * i.unit_price_cents)`.)

**Trap 4, WHERE vs HAVING (Task 5).** "Customers who spent more than 100.00 in total" written as
`WHERE o.total_cents > 10000`. `WHERE` filters single orders before grouping, so it finds customers
with one order above 100.00. Fix: `HAVING SUM(o.total_cents) > ?`, which filters the groups after
the totals are known.

**Slow query 1, the subquery that runs once per row (Task 7).** Two scalar subqueries in the
`SELECT` list. Postgres runs each of them once per customer: 50 customers, so the 300,000-row
orders table is read 100 times, 30 million rows to return 50. Fix: one `LEFT JOIN` + `GROUP BY`.
The table is read once.

**Slow query 2, fetching everything (Task 8).** `SELECT * FROM orders ORDER BY created_at DESC` for
a page that shows 20 rows and 5 columns: 300,012 rows, 70 MB. Fix: name the 5 columns and add
`LIMIT ?`, capped at 100. 20 rows, 973 bytes, and Postgres stops reading after 20.

**Fast in dev, slow in prod (Task 9).** Two reasons you can watch. The same plan does more work on
more rows, and a different plan is chosen on a big table than on a small one. And stale
statistics: after 300,000 orders are loaded without `ANALYZE`, the planner expects 1 EXPIRED order,
gets 90,000, and picks a Nested Loop that touches 27 times more pages than the Hash Join it picks
after `ANALYZE`.

**The steps (Task 10).** Get the real query and its parameters. `EXPLAIN (ANALYZE, BUFFERS)` on
production-sized data. Find the step where the time goes. Compare rows read with rows returned, and
estimated with actual rows. Then choose: rewrite the query, fetch less, fix the statistics, or add
an index. Measure again.

</details>
