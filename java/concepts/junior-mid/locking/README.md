# Locking — Optimistic vs Pessimistic

> **In `start/`, `./check` FAILS on purpose: 7 of 18 checks pass.** That is the exercise, not a
> broken checkout. Tasks 2, 4 and 6 turn the other 11 green.

A shop has **one** PS5 left. Two buyers click "buy" at the same moment, and both orders are
confirmed. This project lets you watch that happen, then fix it three different ways and see what
each fix does inside the database.

It answers the interview questions students most often get wrong:

- "Two requests update the same row at the same time. What happens?"
- "Optimistic or pessimistic locking: what is the difference, and when do you use each?"
- "Is `SELECT ... FOR UPDATE` optimistic or pessimistic?"
- "How does `@Version` work?"

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
docker compose up -d      # Postgres on port 5440
./demo                    # watch two buyers get the last item
./check                   # 7 of 18 pass: that is your starting point
```

The first run downloads Maven and the dependencies, so it takes a minute or two.

When you are finished: `docker compose down`.

## The three tools

### `./demo` — see what happens

Starts the real application, sends buyers at `POST /api/orders` over HTTP at the same moment, and
prints what happened to the stock. Every run starts from a clean state.

```
./demo                                  two buyers, one item, the naive code
./demo --strategy pessimistic           naive | pessimistic | optimistic | atomic
./demo --buyers 50 --stock 10           more buyers, more stock
./demo --sql                            print every SQL statement and the thread that ran it
./demo --pause                          the first buyer holds its transaction open for 10 seconds
./demo --help
```

### `./inspect` — look inside the database

Shows the product row, the orders, and every session that is inside a transaction, including which
session it is waiting for. Run it in a second terminal while `./demo --pause` is running.
`./inspect --watch` refreshes every second.

### `./check` — are the acceptance criteria met?

`./check 2` runs the tests for Task 2 and prints one line per criterion. `./check` runs everything.
`./mvnw verify` runs the same tests the usual Maven way.

The tests are the acceptance criteria. **Never edit, delete or disable a test.**

## The code

```
start/src/main/java/com/amigoscode/locking/
├── order/
│   ├── OrderController.java     POST /api/orders?strategy=naive|pessimistic|optimistic|atomic
│   ├── OrderService.java        picks the strategy; the optimistic retry goes here
│   └── OrderPlacer.java         one transaction per strategy. THE BUG IS HERE
├── product/
│   ├── Product.java             the entity
│   └── ProductRepository.java   the queries
├── fraud/                       a 100 ms check that runs between reading the stock and writing it
└── demo/                        the demo and inspect tools. Nothing to change here
```

All four strategies start with the same bug. `placeNaive` stays broken for ever, so you can always
compare against it. You fix the other three.

## Two ways to work

- **[`INTERVIEW_TASKS.md`](INTERVIEW_TASKS.md)**: challenge mode. A story, acceptance criteria and
  hints for each task. Use this if you want to work it out yourself.
- **[`TASKS_GUIDED.md`](TASKS_GUIDED.md)**: the same seven tasks, step by step, with the output you
  should see and an explanation of why. Use this to learn the concept, or when you are stuck.

Both files use the same task numbers.

| Task | What you do | Checked by |
|---|---|---|
| 1 | See the lost update | `./check 1` (passes: it documents the bug) |
| 2 | Fix it with a pessimistic lock | `./check 2` |
| 3 | Inspect the pessimistic lock | you answer questions |
| 4 | Fix it with optimistic locking | `./check 4` |
| 5 | Inspect the optimistic lock | you answer questions |
| 6 | Fix it with one atomic `UPDATE` | `./check 6` |
| 7 | Compare all three under load | you answer questions |

## Solution

`solution/` is the same project with every task done: `cd solution && ./check` prints 18 of 18.

To start again from scratch: `git checkout -- start` (this throws away your changes in `start/`).

<details>
<summary>Spoiler: the bug and the three fixes</summary>

**The bug.** `OrderPlacer` reads the stock, checks it, runs a fraud check, then writes
`stock - quantity`. Nothing ties the read to the write. Two transactions can both read `stock = 1`,
both pass the check, and both write `0`. Two orders, one item: a **lost update**.

**Fix 1, pessimistic (Task 2).** Read the row with `SELECT ... FOR UPDATE`
(`@Lock(LockModeType.PESSIMISTIC_WRITE)` on a repository query). The row stays locked until the
transaction ends, so the second buyer waits at its read and then sees `stock = 0`.

**Fix 2, optimistic (Task 4).** Add `@Version` to `Product`. Hibernate writes
`UPDATE ... WHERE id = ? AND version = ?`; the loser's update matches 0 rows and Hibernate throws
`ObjectOptimisticLockingFailureException`. `OrderService` catches it and retries in a **new
transaction**, up to 3 attempts, then throws `OrderConflictException` (a 409).

**Fix 3, atomic (Task 6).** No separate read:
`UPDATE product SET stock = stock - ? WHERE id = ? AND stock >= ?`, then look at the row count.
0 rows means sold out.

**When to use which.**

| | Pessimistic | Optimistic | Atomic update |
|---|---|---|---|
| Who waits | Everyone queues at the read | Nobody | Only for the one statement |
| Loser | Waits, then gets the fresh value | Fails at commit, must retry | Gets 0 rows |
| Cost | Locks and connections held for the whole transaction | Wasted work and retries when conflicts are common | None, but only fits a simple rule |
| Use it when | Conflicts are common, or redoing the work is expensive | Conflicts are rare, reads far outnumber writes | The rule fits in one `WHERE` clause |

</details>
