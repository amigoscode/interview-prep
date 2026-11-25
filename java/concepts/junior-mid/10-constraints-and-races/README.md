# Constraints and Races — The Database as the Last Line of Defence

> **In `start/`, `./check` FAILS on purpose: 19 of 35 checks pass.** That is the exercise, not a
> broken checkout. Tasks 2, 4, 5, 6 and 7 turn the other 16 green.

Two people sign up with the same email at the same moment. The code checks "is this email taken?"
for both, both are told "no", and the `users` table ends up with two accounts for one address. This
project lets you watch that happen, then make the database refuse the second row and turn its
refusal into a clean answer.

It answers the interview questions students most often get wrong:

- "You check `existsByEmail` before you save. Why can you still get duplicates?"
- "How do you return a 409 instead of a 500 when a unique constraint is violated?"
- "How do you enforce 'only one ACTIVE subscription per user' in the database?"
- "What is `INSERT ... ON CONFLICT`, and when do you need it?"
- "You caught the exception. Why does the next query say 'current transaction is aborted'?"

## Tech Stack

- **Java 25**, **Spring Boot 4**, Spring Data JPA (Hibernate), JdbcTemplate
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
docker compose up -d      # Postgres on port 5449
./demo                    # watch one email get two accounts
./check                   # 19 of 35 pass: that is your starting point
```

The first run downloads Maven and the dependencies, so it takes a minute or two.

When you are finished: `docker compose down`.

## The three tools

### `./demo` — see what happens

Starts the real application, sends requests at it over HTTP at the same moment, and prints the rows
that ended up in the database. Every run starts from empty tables.

```
./demo                                   two signups, one email
./demo --scenario case                   signup | case | subscription | settings | theme | bad-data
./demo --scenario settings --strategy catch-inside
                                         check-first | catch-inside | catch-outside | savepoint | on-conflict
./demo --requests 20                     more requests at the same moment
./demo --work-ms 0                       no time between the check and the insert
./demo --sql                             print every SQL statement and the thread that ran it
./demo --help
```

### `./inspect` — look inside the database

For every table: its columns, its constraints (from `pg_constraint`) and its indexes (from
`pg_indexes`). Then what is wrong in the data right now: emails with more than one row, users with
more than one ACTIVE subscription, and rows that should never have been stored. Run it after
`./demo`.

### `./check` — are the acceptance criteria met?

`./check 2` runs the tests for Task 2 and prints one line per criterion. `./check` runs everything.
`./mvnw verify` runs the same tests the usual Maven way.

The tests are the acceptance criteria. **Never edit, delete or disable a test.**

## The code

```
start/src/main/resources/
└── schema.sql                     the tables. YOU ADD THE CONSTRAINTS HERE

start/src/main/java/com/amigoscode/constraints/
├── user/
│   ├── SignupController.java      POST /api/signups
│   ├── SignupService.java         check, then insert. THE BUG IS HERE
│   └── UserRepository.java        the email check
├── subscription/
│   ├── SubscriptionController.java   POST /api/subscriptions, POST /api/subscriptions/{id}/cancel
│   └── SubscriptionService.java   check, then insert. The same bug
├── settings/
│   ├── SettingsController.java    PUT /api/users/{id}/settings?strategy=...   PUT .../settings/theme
│   ├── SettingsService.java       picks the strategy
│   └── SettingsStore.java         one transaction per strategy, plain SQL
├── work/                          100 ms of work that runs between a check and an insert
└── demo/                          the demo and inspect tools. Nothing to change here
```

`schema.sql` is run every time the application starts: it drops the tables and creates them again.
So the database always has exactly the constraints written in that file. To add one, edit the file
and run the command again. (In a real project the same line would go into a Flyway or Liquibase
migration.)

`SettingsStore.checkThenInsert` and `SettingsStore.insertAndCatchInside` stay broken for ever, so
you can always compare against them.

## Two ways to work

- **[`INTERVIEW_TASKS.md`](INTERVIEW_TASKS.md)**: challenge mode. A story, acceptance criteria and
  hints for each task. Use this if you want to work it out yourself.
- **[`TASKS_GUIDED.md`](TASKS_GUIDED.md)**: the same eight tasks, step by step, with the output you
  should see and an explanation of why. Use this to learn the concept, or when you are stuck.

Both files use the same task numbers.

| Task | What you do | Checked by |
|---|---|---|
| 1 | See the duplicate signup | `./check 1` (passes: it documents the race) |
| 2 | Fix it with a UNIQUE constraint and a 409 | `./check 2` |
| 3 | Inspect the constraint | you answer questions |
| 4 | The same email in different capital letters: a unique index on `lower(email)` | `./check 4` |
| 5 | One ACTIVE subscription per user: a partial unique index | `./check 5` |
| 6 | Create if absent: `ON CONFLICT`, and the aborted transaction | `./check 6` |
| 7 | Bad data: NOT NULL, CHECK and FOREIGN KEY | `./check 7` |
| 8 | Inspect everything and compare | you answer questions |

Related projects: [`../07-locking`](../07-locking) protects a row that already exists (two buyers,
one item), and [`../08-idempotency`](../08-idempotency) uses a unique key to make a retried request
safe.

## Solution

`solution/` is the same project with every task done: `cd solution && ./check` prints 35 of 35.

To start again from scratch: `git checkout -- start` (this throws away your changes in `start/`).

<details>
<summary>Spoiler: the bug and the fixes</summary>

**The bug.** `SignupService` asks `existsByEmail`, hashes the password, then saves. Nothing ties the
check to the insert. Two requests can both be told "not taken" and both insert. `@Transactional`
does not help, and neither does a faster machine. The application cannot make this check safe on
its own, because the two requests may even run on two different servers.

**Fix 1, UNIQUE (Task 2).** `constraint users_email_key unique (email)` in `schema.sql`. The
database refuses the second row. `users.save` then throws Spring's
`DataIntegrityViolationException`, whose cause is Hibernate's `ConstraintViolationException`, and
`getConstraintName()` on that says which rule it was. `SignupService` turns `users_email_key` into
`EmailTakenException`, a 409. The `exists` check stays as a fast path.

**Fix 2, expression index (Task 4).** `create unique index users_email_lower_key on users
(lower(email))`, and the check compares `lower(email)` too.

**Fix 3, partial index (Task 5).** `create unique index subscriptions_one_active_per_user on
subscriptions (user_id) where status = 'ACTIVE'`. Only ACTIVE rows are in the index, so cancelled
rows may repeat.

**Fix 4, ON CONFLICT (Task 6).** `insert ... on conflict (user_id) do nothing` and the row count (1
or 0) for get-or-create; `on conflict (user_id) do update ... returning (xmax = 0)` for
create-or-update. Nothing fails, so the transaction is never aborted.

**Fix 5, the rest (Task 7).** `not null` on `users.email`, `check (seats > 0)`,
`check (price_cents >= 0)` and `references users (id)` on `subscriptions`.

**The trap.** In Postgres a failed statement aborts the whole transaction. Catching the exception
inside the `@Transactional` method and carrying on gives "current transaction is aborted, commands
ignored until end of transaction block". Catch it outside the transaction, use a savepoint, or use
`ON CONFLICT` so that nothing fails.

| The rule | The tool |
|---|---|
| No two rows with the same value | `UNIQUE` constraint |
| The same, on a computed value (`lower(email)`) | unique index on an expression |
| The same, for some of the rows only (`status = 'ACTIVE'`) | partial unique index |
| A value must be present | `NOT NULL` |
| A value must make sense on its own (`seats > 0`) | `CHECK` |
| A row must point at a row that exists | `FOREIGN KEY` |
| Create it unless it is there | `INSERT ... ON CONFLICT` |

</details>
