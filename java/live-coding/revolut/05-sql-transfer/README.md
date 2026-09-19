# 05 SQL transfer: lost updates, FOR UPDATE and optimistic locking

The second half of the Revolut interview is database questions about the transfer you just
wrote in Java: "which isolation level fixes it", "pessimistic or optimistic", "what does
`SELECT ... FOR UPDATE` do". This module makes you *see* each answer instead of memorising it.

No Docker, no Postgres install: it runs on an in-memory **H2** database in PostgreSQL
compatibility mode. Everything you learn transfers to Postgres one to one (the SQL is the
same); only the error messages differ. `Database.java` and the `TwoSessions` test helper are
given, they are the environment. You write the three transfer variants and the tests that
prove what each one does.

```bash
./mvnw -pl 05-sql-transfer test
```

## User stories

### Story 1: the naive transfer

> `transferNaive(from, to, amount)`: in one transaction, read the sender's balance, fail if it
> is too low, debit the sender, credit the receiver, commit.

Plain JDBC: `connection.setAutoCommit(false)`, `PreparedStatement`, `commit()` / `rollback()`.
Test it single-threaded first: balances move, insufficient funds rolls back both sides.

### Story 2: reproduce the lost update

> Two transfers from the same account run at the same time. With the naive version, money
> disappears or the account overdraws.

Use `TwoSessions.runInLockstep(...)`: session 1 reads, session 2 reads, session 1 writes and
commits, session 2 writes and commits. The second write overwrites the first. Assert the wrong
final balance so the test documents the bug. This is the anomaly the interviewer will ask you
to name (**lost update**) and to explain why `READ COMMITTED` allows it.

### Story 3: pessimistic locking

> `transferPessimistic(...)`: lock both rows with `SELECT ... FOR UPDATE` **in id order**
> before reading balances.

Rerun the story 2 scenario: session 2's `FOR UPDATE` now blocks until session 1 commits, then
reads the committed balance. No money lost. Then write a test where the two sessions lock in
opposite orders and observe what the database does about the deadlock.

### Story 4: optimistic locking

> `transferOptimistic(...)`: read `balance` and `version`, then
> `UPDATE ... SET balance = ?, version = version + 1 WHERE id = ? AND version = ?`. If the
> update touches 0 rows, someone else won: roll back and retry (bounded).

Rerun the story 2 scenario: session 2's update affects 0 rows, it retries, and the final
balance is right. Be able to say when you would choose this over `FOR UPDATE`
(low contention, no long-held locks, works across a stateless retry).

### Story 5: one-statement transfer (bonus)

> `UPDATE accounts SET balance = balance - ? WHERE id = ? AND balance >= ?` and check the row
> count. Why is this safe without an explicit lock?

## Two things the solution branch learned from real threads

Both were found by running the transfers from four threads, not by reasoning, and both are
worth saying out loud in the interview:

1. **A plain `UPDATE` takes a row lock too.** The optimistic and one-statement variants had no
   `FOR UPDATE` anywhere and still deadlocked when two transfers ran in opposite directions,
   because each `UPDATE` holds its row until commit. Every variant therefore writes the two
   rows in id order, the same rule as the Java ledger.
2. **Optimistic locking thrashes on a hot row.** Four threads on the same two accounts produced
   twenty consecutive version conflicts and the retry loop gave up. Jittered backoff fixes the
   test; the lesson is that optimistic locking is for rarely contended rows, and a hot account
   belongs under `FOR UPDATE`. That is the reasoning behind "Revolut prefers pessimistic".

## Questions the interviewer asks afterwards

- Name the four isolation levels and the anomaly each one still allows.
- Postgres default is `READ COMMITTED`. What changes at `REPEATABLE READ`? At `SERIALIZABLE`?
- What is the difference between `FOR UPDATE` and `FOR SHARE`? What is `SKIP LOCKED` for?
- How does Postgres detect a deadlock, and what does your application do when it gets one?
- Where would you put an index for this table, and why is `balance` not a candidate?

## Solution

`git checkout solutions/revolut/05-sql-transfer`, then `./mvnw -pl 05-sql-transfer test`. The tests
are the documentation: each one is named after the anomaly it shows or prevents.
