# Idempotency — Safe Retries

> **In `start/`, `./check` FAILS on purpose: 15 of 32 checks pass.** That is the exercise, not a
> broken checkout. Tasks 2, 4, 6 and 7 turn the other 17 green.

Maria pays 25.00 **once**. The answer takes too long, the client times out and sends the same
request again, and Maria is charged **twice**. Nothing crashed: the first request worked, only its
answer got lost. This project lets you watch that happen, then make retries safe step by step and
see what each step stores in the database.

It answers the interview questions students most often get:

- "What is idempotency, and how would you make a payment endpoint idempotent?"
- "Which HTTP methods are idempotent?"
- "One message was received and the email was sent three times. Why, and how do you fix it?"
- "Is cancelling an order with a GET a problem?"

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
docker compose up -d      # Postgres on port 5444
./demo                    # watch one payment get charged twice
./check                   # 15 of 32 pass: that is your starting point
```

The first run downloads Maven and the dependencies, so it takes a minute or two.

When you are finished: `docker compose down`.

## The three tools

### `./demo` — see what happens

Starts the real application, plays a client that sends the same payment more than once to
`POST /api/payments` over HTTP, and prints how often the customer was charged. Every run starts from
a clean state: Maria has 100.00 and no payments. The client makes up a new `Idempotency-Key` (a
UUID) for every run and sends it with every copy of the request.

```
./demo                                        the client times out, then retries (the default)
./demo --scenario double-click                the same request twice at the same moment
./demo --scenario double-click --requests 10  ten copies at the same moment
./demo --scenario replay                      pay A, pay B, retry A: is the answer the original one?
./demo --scenario different-body              the same key again, with a different amount
./demo --scenario expired                     the key expires and is deleted, then the client retries
./demo --no-key                               the client sends no Idempotency-Key header
./demo --sql                                  print every SQL statement and the thread that ran it
./demo --pause                                the card processor takes 10 seconds: time to run ./inspect
./demo --help
```

### `./inspect` — look inside the database

Shows each customer's balance and what they were charged in total, the payment rows, the
idempotency-key rows (the key, the request hash, the payment, the stored status and body, the time
left before expiry), and every session that is inside a transaction, including which session it is
waiting for. Run it after a `./demo`, or in a second terminal while `./demo --pause` is running.
`./inspect --watch` refreshes every second.

### `./check` — are the acceptance criteria met?

`./check 2` runs the tests for Task 2 and prints one line per criterion. `./check` runs everything.
`./mvnw verify` runs the same tests the usual Maven way.

The tests are the acceptance criteria. **Never edit, delete or disable a test.**

## The code

```
start/src/main/java/com/amigoscode/idempotency/
├── payment/
│   ├── PaymentController.java   POST /api/payments, reads the Idempotency-Key header
│   ├── PaymentService.java      one transaction per request. THE BUG IS HERE
│   └── PaymentResult.java       status + body + "is this a replay?"
├── key/
│   ├── IdempotencyRecord.java             one row of the idempotency_key table
│   ├── IdempotencyRecordRepository.java   the queries
│   ├── RequestHash.java                   fingerprint of a request body (Task 7)
│   ├── IdempotencyKey.java                the rules for the header (given)
│   └── IdempotencyCleanup.java            deletes expired keys (given)
├── customer/                    the balance
├── processor/                   a fake card processor that takes 100 ms
└── demo/                        the demo and inspect tools. Nothing to change here
start/src/main/resources/schema.sql        the tables. Task 4 changes this file
```

`schema.sql` drops and recreates the tables every time the application starts, so a change to it is
live on the next `./demo`.

## Two ways to work

- **[`INTERVIEW_TASKS.md`](INTERVIEW_TASKS.md)**: challenge mode. A story, acceptance criteria and
  hints for each task. Use this if you want to work it out yourself.
- **[`TASKS_GUIDED.md`](TASKS_GUIDED.md)**: the same nine tasks, step by step, with the output you
  should see and an explanation of why. Use this to learn the concept, or when you are stuck.

Both files use the same task numbers.

| Task | What you do | Checked by |
|---|---|---|
| 1 | See the double charge | `./check 1` (passes: it documents the bug) |
| 2 | Fix it with an idempotency key (check, then charge) | `./check 2` |
| 3 | Inspect the double click: the fix has a race | you answer questions |
| 4 | Fix the race: UNIQUE constraint, insert the key first | `./check 4` |
| 5 | Inspect the insert-first fix: the loser waits | you answer questions |
| 6 | Replay the same response | `./check 6` |
| 7 | Same key, different body: reject with 422 | `./check 7` |
| 8 | Inspect the key table: scope and expiry | `./check 8` (passes: it documents the trade-off) |
| 9 | Compare: where else, and natural idempotency vs a key table | you answer questions |

## Solution

`solution/` is the same project with every task done: `cd solution && ./check` prints 32 passes and
no failures.

To start again from scratch: `git checkout -- start` (this throws away your changes in `start/`).

<details>
<summary>Spoiler: the bug and the fixes</summary>

**The bug.** `PaymentService.pay` ignores the `Idempotency-Key` header. Every request that arrives
is a new payment. A client that did not get an answer cannot know whether the payment happened, so
it retries, and the server charges again.

**Fix 1, check then charge (Task 2).** Look the key up. If it is there, answer with the payment
that already exists. If not, charge and save the key. Retries one after another are now safe.

**The race (Task 3).** Two copies at the same moment both look the key up before either has saved
it. Both find nothing, both charge.

**Fix 2, insert first (Task 4).** A `UNIQUE (customer_id, idempotency_key)` constraint, and
`INSERT ... ON CONFLICT DO NOTHING` as the **first** statement of the transaction. The database
lets one request insert the row. The other one waits on that insert until the winner commits, then
inserts 0 rows and knows it is a duplicate. It never calls the card processor.

**Fix 3, replay (Task 6).** Store the status code and the JSON body in the key row, in the same
transaction as the payment. A duplicate gets exactly that answer back, plus the header
`Idempotent-Replay: true`.

**Fix 4, request hash (Task 7).** Store a SHA-256 of the request with the key. The same key with a
different hash is a client bug: answer `422`, do not hand out the result of another request.

**Scope and lifetime (Task 8).** A key is unique per customer, not globally. Rows expire after 24
hours and a cleanup deletes them. After that the key is unknown again, and a very late retry is a
new payment.

</details>
