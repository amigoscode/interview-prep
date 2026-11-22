# Indexes — Why Is This Query Slow?

> **In `start/`, `./check` FAILS on purpose: 11 of 25 checks pass.** That is the exercise, not a
> broken checkout. Tasks 2, 4, 6 and 8 turn the other 14 green.

A shop has one million orders. Finding one order by its tracking number reads all one million of
them. This project lets you watch Postgres do that, then fix it with the right index, five different
ways, and see what each index changes in the query plan.

It answers the interview questions students most often get wrong:

- "This query is slow. What are your steps?"
- "How do you choose an index? What is a B-tree? What other index types are there?"
- "There is an index on that column. Why is the database not using it?"
- "It is fast in dev and slow in prod. Why?"
- "Why not put an index on every column?"

## Tech Stack

- **Java 25**, **Spring Boot 4**, Spring JDBC (`JdbcTemplate`, plain SQL)
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
docker compose up -d      # Postgres on port 5441
./demo                    # watch Postgres read 1,000,000 rows to return 1
./check                   # 11 of 25 pass: that is your starting point
```

The first run downloads Maven and the dependencies, and seeds one million orders. That takes a
minute or two, once. After that `./demo` takes a few seconds.

When you are finished: `docker compose down`.

## How you add an index

There is one file: **`src/main/resources/indexes.sql`**. Add a `CREATE INDEX` statement to it and
run `./demo` or `./check` again. That is all.

The file is the single source of truth. Every time the application, `./demo` or `./check` starts, it
compares the file with what it applied last time. If the file changed, every index on `orders` and
`customers` except the primary keys is dropped and the file is run again, followed by
`VACUUM ANALYZE`. So deleting a line removes that index, and you can never end up with a half-applied
state. If a statement has a typo, you get the Postgres error and the statement, not a stack trace.

## The three tools

### `./demo` — see how Postgres runs a query

Runs one of the shop's queries with `EXPLAIN ANALYZE` against the one million orders and prints the
query, the plan, a summary (rows returned, rows read, pages read, time) and a `RESULT` line. The
data is the same on every run.

```
./demo                                     find an order by tracking number
./demo --query latest-orders               track | latest-orders | email | daily-sales | pending |
                                           spend | by-status | like | numeric-id
./demo --query track --no-indexes          the same query with every index hidden, for this run only
./demo --query track --buffers             show the pages each step of the plan read
./demo --query by-status --status PENDING  DELIVERED | CANCELLED | SHIPPED | PENDING
./demo --query spend --after-update        the plan right after an UPDATE, then again after VACUUM
./demo --write-cost                        insert the same rows with 0 indexes and with 6
./demo --help
```

### `./inspect` — look inside the database

Shows the two tables (rows, size, size of all indexes), every index with its columns, its size and
how many times a query has used it, and what the planner believes about the `status` column.

### `./check` — are the acceptance criteria met?

`./check 2` runs the tests for Task 2 and prints one line per criterion. `./check` runs everything.
`./mvnw verify` runs the same tests the usual Maven way.

The tests are the acceptance criteria. **Never edit, delete or disable a test.** They do not measure
time. They ask Postgres for the plan of the real query and check which scan it chose, how many rows
it read and how many pages it touched.

## The code

```
start/src/main/
├── resources/
│   ├── schema.sql                the two tables. Nothing to change here
│   └── indexes.sql               THE INDEXES. Most of your work is in this file
└── java/com/amigoscode/indexes/
    ├── shop/
    │   ├── ShopQueries.java      every SQL query of the shop. Task 6 changes one of them
    │   ├── ShopRepository.java   runs them with JdbcTemplate
    │   └── ShopController.java   the HTTP endpoints
    ├── db/                       seeding, applying indexes.sql, reading plans. Nothing to change here
    └── demo/                     the demo and inspect tools. Nothing to change here
```

The data: 100,000 customers and 1,000,000 orders spread over two years, made inside Postgres with
`generate_series`. It is skewed the way a real shop is: ten big customers have 1,000 orders each and
everyone else about 10; 93% of the orders are `DELIVERED` and only the newest 0.4% are `PENDING`.

## Two ways to work

- **[`INTERVIEW_TASKS.md`](INTERVIEW_TASKS.md)**: challenge mode. A story, acceptance criteria and
  hints for each task. Use this if you want to work it out yourself.
- **[`TASKS_GUIDED.md`](TASKS_GUIDED.md)**: the same ten tasks, step by step, with the output you
  should see and an explanation of why. Use this to learn the concept, or when you are stuck.

Both files use the same task numbers.

| Task | What you do | Checked by |
|---|---|---|
| 1 | See the sequential scan and read `EXPLAIN ANALYZE` | `./check 1` (passes: it documents the problem) |
| 2 | Fix it with a B-tree index | `./check 2` |
| 3 | Inspect the B-tree index | you answer questions |
| 4 | Fix it with a composite index (column order) | `./check 4` |
| 5 | Inspect the composite index (leftmost prefix) | you answer questions |
| 6 | Fix the index that exists but is skipped | `./check 6` |
| 7 | Inspect more ways to lose an index (`LIKE`, wrong type) | you answer questions |
| 8 | Fix it with a partial index and a covering index | `./check 8` |
| 9 | Inspect the partial and the covering index (`VACUUM`) | you answer questions |
| 10 | The cost: why not index everything | `./check 10` (passes: it documents the cost) |

## Solution

`solution/` is the same project with every task done: `cd solution && ./check` prints 25 of 25.

`start/` and `solution/` use the same database. When you switch from one to the other, the next
command notices that `indexes.sql` is different and rebuilds the indexes.

To start again from scratch: `git checkout -- start` (this throws away your changes in `start/`).

<details>
<summary>Spoiler: the problems and the fixes</summary>

**The problem.** With no index on the column in the `WHERE` clause, Postgres has one way to find the
rows: read the whole table and test every row. That is a **Seq Scan**. On one million orders it reads
10,310 pages to return one row.

**Fix 1, B-tree (Task 2).** `CREATE INDEX ... ON orders (tracking_number)`. A B-tree is a sorted
tree: Postgres walks from the root to the leaf in 3 or 4 pages, then fetches the one row. Seq Scan
becomes Index Scan.

**Fix 2, composite index (Task 4).** "The 5 newest orders of a customer" needs
`(customer_id, created_at)`: equality column first, sort column second. The index that was already
there, `(created_at, customer_id)`, has the same columns in the wrong order and Postgres has to walk
it from the end. A composite index can be searched by its leftmost columns only.

**Fix 3, the skipped index (Task 6).** `WHERE lower(email) = ?` cannot use an index on `email`: the
index stores `email`, not `lower(email)`. Either index the expression,
`CREATE INDEX ... ON customers (lower(email))`, or rewrite the query so the column is left alone:
`date(created_at) = ?` becomes `created_at >= ? AND created_at < ?`.

**Fix 4, partial index (Task 8).** `CREATE INDEX ... ON orders (created_at) WHERE status = 'PENDING'`
holds only the 4,000 pending orders, not all million. It is tiny and it is already in queue order.

**Fix 5, covering index (Task 8).** Add `INCLUDE (total_cents)` to the Task 4 index. "What did this
customer spend?" is then answered from the index alone: an **Index Only Scan** with
`Heap Fetches: 0`. That only holds while the visibility map is up to date, which is VACUUM's job.

**The cost (Task 10).** Every index is written on every `INSERT`, and takes disk and memory. Six
indexes made the same insert several times slower and took twice the space of the rows. And an index
on a value that matches most of the table (`status = 'DELIVERED'`, 93%) is not used at all.

</details>
