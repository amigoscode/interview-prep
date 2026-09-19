# Interview Tasks — SQL Transfer

Sequential, but this one is study rather than a timed round: the point is to watch each anomaly
happen and each fix work. Do all five, then answer the questions at the end in full sentences.

---

## Task 1: The Naive Transfer

**Story:** *`transferNaive(from, to, amount)`: in one transaction, read the sender's balance, fail
if it is too low, debit the sender, credit the receiver, commit.*

**What to do:**
1. Plain JDBC: `PreparedStatement`, `commit()` / `rollback()` (autocommit is already off)
2. Test single-threaded first

**Acceptance Criteria:**
- Balances move
- Insufficient funds rolls back both sides
- Unknown account rolls back

---

## Task 2: Reproduce the Lost Update

**Story:** *Two transfers from the same account run at the same time. With the naive version,
money disappears or is created.*

**What to do:**
1. Use `TwoSessions.run(db, (s1, s2) -> ...)`: session 1 reads, session 2 reads, session 1 writes
   and commits, session 2 writes and commits
2. Assert the **wrong** final balances so the test documents the bug

**Acceptance Criteria:**
- Both sessions read 100
- After both commit, A shows only the second debit and B received both credits

**Hints:**
- This is the anomaly to name: **lost update**. `READ COMMITTED` allows it because each statement
  sees the latest commit, but nothing ties session 2's read to its write

---

## Task 3: Pessimistic Locking

**Story:** *`transferPessimistic(...)`: lock both rows with `SELECT ... FOR UPDATE` **in id
order** before reading balances.*

**What to do:**
1. Rerun the task 2 script. Session 2's `FOR UPDATE` now blocks until session 1 commits
2. Write a second script where the two sessions lock in opposite orders and observe what the
   database does about the deadlock

**Acceptance Criteria:**
- Session 2 is still waiting 300 ms after session 1 took the lock, then reads the committed value
- Opposite lock order: the database fails at least one side (H2 raises "Deadlock detected";
  Postgres reports the same within a second)

---

## Task 4: Optimistic Locking

**Story:** *`transferOptimistic(...)`: read `balance` and `version`, then
`UPDATE ... SET balance = ?, version = version + 1 WHERE id = ? AND version = ?`. If the update
touches 0 rows, someone else won: roll back and retry (bounded).*

**Acceptance Criteria:**
- In the task 2 script, session 2's conditional update matches 0 rows
- The full method retries and lands on the right balance
- Four real threads transferring in both directions conserve money (see the README for what this
  test taught the solution)

---

## Task 5: One-Statement Transfer (bonus)

**Story:** *`UPDATE accounts SET balance = balance - ? WHERE id = ? AND balance >= ?` and check
the row count.*

**Acceptance Criteria:**
- Explain why this is safe without an explicit lock (a single `UPDATE` is atomic: the database
  locks the row, re-evaluates the `WHERE` against the current committed value, and applies it)
- Explain why it still deadlocks in opposite directions unless rows are written in id order

---

## Questions to Ask Afterwards

- Name the four isolation levels and the anomaly each one still allows.
- Postgres default is `READ COMMITTED`. What changes at `REPEATABLE READ`? At `SERIALIZABLE`?
  Which one would you use for the transfer, and what does your application do on error `40001`?
- `FOR UPDATE` vs `FOR SHARE`? What is `SKIP LOCKED` for?
- How does Postgres detect a deadlock, and what does the application do when it gets one?
- Pessimistic or optimistic for a bank ledger? Why? (Hot rows, held locks, retries)
- Where would you put an index on this table, and why is `balance` not a candidate?

## Tips for Interviewers

- This is the round where candidates get rejected for "answers too vague". Push for the concrete: which
  statement, which lock, which error code, what the retry looks like.
