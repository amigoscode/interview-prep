# Pagination — Offset vs Keyset (Cursor)

> **In `start/`, `./check` FAILS on purpose: 15 of 35 checks pass.** That is the exercise, not a
> broken checkout. Tasks 2, 3, 5, 6 and 8 turn the other 20 green.

A shop has one million orders. The order list is paginated with the one line everybody writes:
`repository.findAll(PageRequest.of(page, size, Sort.by("createdAt").descending()))`. Page 1 reads 20
rows. Page 5,000 reads 100,000 rows to return 20. This project lets you watch that happen, find the
four other problems hiding in the same line, and replace it with keyset (cursor) pagination.

It answers the interview questions students most often get wrong:

- "How do you paginate a large table? What is wrong with `LIMIT` / `OFFSET`?"
- "What is keyset (cursor) pagination, and when would you not use it?"
- "Users see the same row on two pages. Why?"
- "What is the difference between `Page` and `Slice` in Spring Data?"
- "A client calls your endpoint with `size=1000000`. What happens?"

## Tech Stack

- **Java 25**, **Spring Boot 4**, Spring Data JPA (Hibernate)
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
docker compose up -d      # Postgres on port 5450
./demo                    # watch page 5,000 read 100,000 rows to return 20
./check                   # 15 of 35 pass: that is your starting point
```

The first run downloads Maven and the dependencies, and seeds one million orders. That takes a
minute or two, once. After that `./demo` takes a few seconds.

When you are finished: `docker compose down`.

## The three tools

### `./demo` — see what a page costs

Starts the real application, asks it for pages over HTTP and prints, for every request, the SQL the
application really sent and what Postgres did with it: rows read, rows returned, pages touched. Ends
with one or more `RESULT` lines. The data is the same on every run.

```
./demo                             offset: page 1 against page 5,000
./demo --depth 50000               the same, 50,000 pages deep
./demo --size 100000               ask for 100,000 orders in one request
./demo --mode history              read ALL orders of customer 42 page by page: is each one returned once?
./demo --insert                    a new order is placed between the request for page 1 and page 2
./demo --mode keyset               the cursor endpoint: page 1, page 2 and page 5,000
./demo --mode keyset --insert      the same insert, with a cursor
./demo --reseed                    empty the table and seed it again
./demo --help
```

### `./inspect` — look inside the database

Shows the table and its indexes, then sends one page request and prints the complete
`EXPLAIN (ANALYZE, BUFFERS)` output of every SQL statement that request ran.
`./inspect --mode keyset --depth 5000`, `./inspect --mode history`, `./inspect --depth 1`.

### `./check` — are the acceptance criteria met?

`./check 2` runs the tests for Task 2 and prints one line per criterion. `./check` runs everything.
`./mvnw verify` runs the same tests the usual Maven way.

The tests are the acceptance criteria. **Never edit, delete or disable a test.** They do not measure
time. They call the service, write down the SQL it really sent, and check which rows came back, how
many statements were sent, and how many rows Postgres read to answer them.

## The code

```
start/src/main/
├── resources/
│   ├── schema.sql                 the table. Nothing to change here
│   └── indexes.sql                THE INDEXES. Task 5 changes this file
└── java/com/amigoscode/pagination/
    ├── order/
    │   ├── OrderController.java   the three endpoints. Nothing to change here
    │   ├── Paging.java            the rules for ?page= and ?size=. Task 2
    │   ├── OrderService.java      the sort order, Page and the feed. Tasks 3, 5 and 8
    │   ├── OrderRepository.java   the queries. Tasks 5 and 8
    │   └── Cursor.java            what the client sends back to get the next page. Task 6
    ├── db/                        seeding, applying indexes.sql, recording SQL, reading plans. Nothing to change here
    └── demo/                      the demo and inspect tools. Nothing to change here
```

The three endpoints:

| Endpoint | How it pages |
|---|---|
| `GET /api/orders?page=0&size=20` | Every order, newest first, with `LIMIT` and `OFFSET` |
| `GET /api/customers/{id}/orders?page=0&size=20` | The orders of one customer, the same way |
| `GET /api/orders/feed?after=<cursor>&size=20` | Every order, newest first, with a cursor. Somebody started it and did not finish |

The data: 1,000,000 orders made inside Postgres with `generate_series`. They arrive from a
marketplace in batches: 40 orders every 40 minutes, written in one transaction, so **all 40 share
the same `created_at`**. The first 12 orders of every batch belong to one business customer.

You add or change an index in `src/main/resources/indexes.sql`. The next `./demo`, `./inspect` or
`./check` notices that the file changed, drops every index except the primary key and runs the file
again. [`../03-indexes`](../03-indexes/) is the project about indexes themselves; this one assumes
you know what a composite index is.

## Two ways to work

- **[`INTERVIEW_TASKS.md`](INTERVIEW_TASKS.md)**: challenge mode. A story, acceptance criteria and
  hints for each task. Use this if you want to work it out yourself.
- **[`TASKS_GUIDED.md`](TASKS_GUIDED.md)**: the same nine tasks, step by step, with the output you
  should see and an explanation of why. Use this to learn the concept, or when you are stuck.

Both files use the same task numbers.

| Task | What you do | Checked by |
|---|---|---|
| 1 | See the deep `OFFSET`, the unbounded size and the extra `COUNT` | `./check 1` (passes: it documents the problem) |
| 2 | Fix the unbounded page size | `./check 2` |
| 3 | Fix the unstable order with a tie-breaker | `./check 3` |
| 4 | Inspect what `OFFSET` cannot fix: depth and shifting rows | `./check 4` (passes: it documents the problem) |
| 5 | Fix it with keyset pagination | `./check 5` |
| 6 | Fix the cursor: opaque, and a 400 when it is malformed | `./check 6` |
| 7 | Inspect keyset pagination | you answer questions |
| 8 | Fix the hidden `COUNT(*)` | `./check 8` |
| 9 | Compare offset and keyset, and say when each is right | you answer questions |

## Solution

`solution/` is the same project with every task done: `cd solution && ./check` prints 35 of 35.

`start/` and `solution/` use the same database. When you switch from one to the other, the next
command notices that `indexes.sql` is different and rebuilds the indexes.

To start again from scratch: `git checkout -- start` (this throws away your changes in `start/`).

<details>
<summary>Spoiler: the problems and the fixes</summary>

**Unbounded size (Task 2).** `?size=1000000` is accepted and a request without `size` returns the
whole table. Fix: a default of 20 and a maximum of 100 in `Paging`, and a 400 for anything outside
1 to 100.

**Unstable order (Task 3).** `ORDER BY created_at` does not say which of 40 orders with the same
`created_at` comes first, so Postgres may answer differently for every page. Reading a customer's
300 orders page by page returns 14 of them twice and never returns 14 others. Fix: a unique
tie-breaker, `ORDER BY created_at DESC, id DESC`.

**Deep OFFSET and shifting rows (Task 4).** `OFFSET 99980` means "read 99,980 rows and throw them
away". And because `OFFSET` counts from the top of the list at the moment of each request, an order
placed between two requests moves every row down by one: the last row of page 1 comes back as the
first row of page 2. No tie-breaker and no index fixes either. They are what `OFFSET` is.

**Keyset pagination (Task 5).** Do not count rows. Remember where the last page ended and start
there: `WHERE (created_at, id) < (?, ?) ORDER BY created_at DESC, id DESC LIMIT ?`, with an index on
`(created_at, id)`. Postgres jumps to that position in the index: page 5,000 reads 21 rows, like
page 1, and an insert at the top cannot shift anything. Fetch `size + 1` rows: the extra row says
whether there is a next page.

**The cursor (Task 6).** The client gets the position as an opaque string: Base64 of
`createdAt|id`. A cursor that cannot be decoded is the client's mistake: a 400, never a 500.

**The hidden COUNT (Task 8).** A Spring Data `Page` runs `SELECT COUNT` on every request to fill
`totalElements`, which no screen shows. On one million rows that count reads one million rows.
Return a `Slice`: it fetches `size + 1` rows and never counts.

**When to use which.**

| | Offset | Keyset |
|---|---|---|
| Cost of page N | Reads N x size rows | Reads size + 1 rows |
| Rows inserted or deleted while paging | Rows repeat or are skipped | Nothing shifts |
| Jump to page 37, show "page 37 of 500" | Yes | No: only next (and previous) |
| Sort order | Anything | Needs a unique, indexed sort key, fixed for the endpoint |
| Use it when | Small tables, admin screens, a user who needs page numbers | Feeds, infinite scroll, exports, sync jobs, anything deep |

</details>
