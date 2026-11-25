# Isolation Levels — What One Transaction Sees of Another

> **In `start/`, `./check` FAILS on purpose: 22 of 31 checks pass.** That is the exercise, not a
> broken checkout. Tasks 2, 4 and 6 turn the other 9 green.

A bank prints a statement: current account 500, savings 500, **total 1100**. Nothing crashed. A
deposit was committed between the query that read the lines and the query that read the total. This
project lets you watch that happen, step by step, in two database sessions. Then you raise the
isolation level, meet the error that comes with it (SQLSTATE `40001`), and handle it.

It answers the interview questions students most often get wrong:

- "What are the four isolation levels, and which anomalies does each one allow?"
- "What is the default in Postgres? Does READ UNCOMMITTED give you dirty reads there?"
- "What is the difference between a non-repeatable read and a phantom read?"
- "What is write skew, and why does REPEATABLE READ not stop it?"
- "What happens when a SERIALIZABLE transaction fails? Where does the retry go?"
- "Isolation level or `SELECT ... FOR UPDATE`: what is the difference?"

## Tech Stack

- **Java 25**, **Spring Boot 4**, Spring Data JPA (Hibernate 7)
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
docker compose up -d      # Postgres on port 5448
./demo                    # watch a statement that does not add up
./check                   # 22 of 31 pass: that is your starting point
```

The first run downloads Maven and the dependencies, so it takes a minute or two.

When you are finished: `docker compose down`.

## The three tools

### `./demo` — see what happens

Two sessions, A and B, run their steps in a fixed order, so the anomaly happens on every run. The
demo prints each step and ends with a `RESULT:` line. Every run starts from the same data: alice
has a current account (id 1) with 500 and a savings account (id 2) with 500.

It has two modes:

- **Without `--level` it runs the application's code** (`StatementService`, `WithdrawalService`).
  What you see depends on the `@Transactional` settings in the code, so this is how you see your
  fix work.
- **With `--level` it runs the same anomaly as plain SQL** at the level you choose. This is what
  Postgres itself does, whatever the Java code says.

```
./demo                                               the application's statement while a deposit commits
./demo --anomaly phantom                             dirty-read | non-repeatable-read | phantom | lost-update | write-skew
./demo --anomaly write-skew --level serializable     read-uncommitted | read-committed | repeatable-read | serializable
./demo --anomaly write-skew --for-update             plain SQL with SELECT ... FOR UPDATE instead of a higher level
./demo --matrix                                      every anomaly at every level, as one table
./demo --pause                                       hold the transactions open for 15 seconds, for ./inspect
./demo --help
```

### `./inspect` — look inside the database

Shows the account rows with the transaction that wrote each one (`xmin`), every session that is
inside a transaction, its isolation level, whether it is holding a snapshot, which session it is
waiting for, and the predicate locks of SERIALIZABLE sessions. Run it in a second terminal while
`./demo --pause` is running. `./inspect --watch` refreshes every second.

### `./check` — are the acceptance criteria met?

`./check 2` runs the tests for Task 2 and prints one line per criterion. `./check` runs everything.
`./mvnw verify` runs the same tests the usual Maven way.

The tests are the acceptance criteria. **Never edit, delete or disable a test.**

## The code

```
start/src/main/java/com/amigoscode/isolation/
├── statement/
│   ├── StatementService.java     three queries in one transaction. BUG 1 IS HERE
│   └── Statement.java            the lines, the account count and the total
├── withdrawal/
│   ├── WithdrawalService.java    calls Withdrawer; the retry goes here
│   └── Withdrawer.java           one transaction: read, check, write. BUGS 2 AND 3 ARE HERE
├── account/                      the entity and the queries
├── checkpoint/                   a named point between a read and a write, where the demo and
│                                 the tests let the other session in. Nothing to change here
└── demo/                         the demo and inspect tools. Nothing to change here
```

The bank's rule for withdrawals: **one account may go below zero, as long as all of the customer's
accounts together stay at zero or above.**

## Two ways to work

- **[`INTERVIEW_TASKS.md`](INTERVIEW_TASKS.md)**: challenge mode. A story, acceptance criteria and
  hints for each task. Use this if you want to work it out yourself.
- **[`TASKS_GUIDED.md`](TASKS_GUIDED.md)**: the same eight tasks, step by step, with the output you
  should see and an explanation of why. Use this to learn the concept, or when you are stuck.

Both files use the same task numbers.

| Task | What you do | Checked by |
|---|---|---|
| 1 | See the statement that does not add up | `./check 1` (passes: it documents what Postgres does) |
| 2 | Fix the statement with REPEATABLE READ | `./check 2` |
| 3 | Inspect the snapshot | you answer questions |
| 4 | Stop the lost update, and retry the serialization failure | `./check 4` |
| 5 | Inspect the serialization failure | you answer questions |
| 6 | Fix the write skew with SERIALIZABLE | `./check 6` |
| 7 | Inspect SERIALIZABLE | you answer questions |
| 8 | Compare every level, and isolation levels vs locking | `./check 8` (passes) and you answer questions |

## Isolation levels are not locks

Students mix these up in interviews, so keep them apart from the start.

| | Isolation level | Lock (`SELECT ... FOR UPDATE`) |
|---|---|---|
| What it is | A setting of the **transaction**: what it may see of other transactions | A claim on **specific rows**: nobody else may change or lock them until I finish |
| Who is affected | Only the transaction that sets it | Every other transaction that wants those rows |
| What the other one experiences | No extra waiting. In Postgres a conflict **cancels** one transaction with SQLSTATE `40001` | It **waits** until the lock is released |
| What the application must do | Retry the cancelled transaction | Keep the transaction short, lock in a consistent order |
| Reach for it when | The decision depends on many rows, or on rows that do not exist yet. Conflicts are rare | You know exactly which rows you will change, and redoing the work is expensive or conflicts are common |

Locking, optimistic and pessimistic, has its own project: [`../07-locking`](../07-locking/).

## Solution

`solution/` is the same project with every task done: `cd solution && ./check` prints 31 of 31.

To start again from scratch: `git checkout -- start` (this throws away your changes in `start/`).

<details>
<summary>Spoiler: the three bugs and the fixes</summary>

**Bug 1, the statement (Task 2).** `StatementService.statement` runs three queries in a READ
COMMITTED transaction (the Postgres default). Each query sees whatever is committed when that query
starts. A deposit between them is a **non-repeatable read**, a new account is a **phantom read**.
Fix: `@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)`. The transaction takes
one snapshot at its first query and every later query reads from it.

**Bug 2, the lost update (Task 4).** `Withdrawer.withdraw` reads the balance, subtracts in Java and
writes the result. Two withdrawals from the same account both read 500 and both write 400. At
REPEATABLE READ Postgres cancels the second writer: SQLSTATE `40001`, "could not serialize access
due to concurrent update". Spring raises `CannotAcquireLockException`. `WithdrawalService` catches
it (as `ConcurrencyFailureException`) and runs the withdrawal again in a **new transaction**, up to
3 attempts, then throws `WithdrawalConflictException`.

**Bug 3, the write skew (Task 6).** Two withdrawals from **different** accounts each check the
combined balance, each see 1000, each write their own row. No row is written twice, so REPEATABLE
READ has nothing to object to, and the accounts end at -600. Fix:
`@Transactional(isolation = Isolation.SERIALIZABLE)`. Postgres tracks what each transaction read,
and cancels one of them with `40001`. The retry from Task 4 handles it.

**What each level stops in Postgres** (`./demo --matrix`):

| | read uncommitted | read committed | repeatable read | serializable |
|---|---|---|---|---|
| dirty read | no | no | no | no |
| non-repeatable read | HAPPENS | HAPPENS | no | no |
| phantom read | HAPPENS | HAPPENS | no | no |
| lost update | HAPPENS | HAPPENS | 40001 | 40001 |
| write skew | HAPPENS | HAPPENS | HAPPENS | 40001 |

READ UNCOMMITTED is accepted and runs as READ COMMITTED: Postgres has no dirty reads.

</details>
