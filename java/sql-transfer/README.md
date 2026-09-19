# SQL Transfer — Live Coding Practice

The second half of a fintech technical interview is database questions about the transfer you
just wrote in Java: *"which isolation level fixes it"*, *"pessimistic or optimistic"*, *"what does
`SELECT ... FOR UPDATE` do"*. This project makes you **see** each answer instead of memorising it.

No Docker, no Postgres install: it runs on an in-memory **H2** database in PostgreSQL
compatibility mode. Everything transfers to Postgres one to one (the SQL is the same); only the
error messages differ. `Database.java` and the `TwoSessions` test helper are given: they are the
environment. You write the transfer variants and the tests that prove what each one does.

Time box: **one evening**, and take a page of notes. Those notes become your Q&A answers.


## Tech Stack

- **Java 21**, plain **JDBC**
- **H2 2.3** in-memory, `MODE=PostgreSQL`
- **JUnit 5, Mockito 5, AssertJ 3**, Maven wrapper
- No Spring, no JPA, no ORM

## Getting Started

- JDK 21+
- `./mvnw test`. `databaseIsWiredUp` is green; the first real test is
  `@Disabled("story 1: remove this line to begin")`.

## What Is Given

- `Database`: a fresh, isolated in-memory schema per instance;
  `accounts(id, balance decimal(18,2) check (balance >= 0), version int)`. Every `connect()` is a
  new session with autocommit **off**.
- `TwoSessions` (test scope): runs two sessions on two threads and lets a test script the
  interleaving step by step, which is the only way to make a race deterministic.

## Two Things the Solution Learned From Real Threads

Both found by running the transfers from four threads, not by reasoning, and both worth saying
out loud in the interview:

1. **A plain `UPDATE` takes a row lock too.** The optimistic and one-statement variants had no
   `FOR UPDATE` anywhere and still deadlocked when two transfers ran in opposite directions,
   because each `UPDATE` holds its row until commit. Every variant therefore writes the two rows in
   id order, the same rule as the Java ledger.
2. **Optimistic locking thrashes on a hot row.** Four threads on the same two accounts produced
   twenty consecutive version conflicts and the retry loop gave up. Jittered backoff fixes the
   test; the lesson is that optimistic locking is for rarely contended rows, and a hot account
   belongs under `FOR UPDATE`. That is why ledgers lean pessimistic.

## Solution

`git checkout solutions/sql-transfer` then `./mvnw test`. The tests are the
documentation: each one is named after the anomaly it shows or prevents.

Reset to the skeleton: `git checkout main -- java/sql-transfer`.
