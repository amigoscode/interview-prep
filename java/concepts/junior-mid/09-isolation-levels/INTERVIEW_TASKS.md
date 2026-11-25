# Interview Tasks — Isolation Levels

> **In `start/`, `./check` FAILS on purpose: 22 of 31 checks pass.** Tasks 2, 4 and 6 turn the other
> 9 green. Tasks 1, 3, 5, 7 and 8 are inspection tasks: you run things, look, and answer questions.

Do the tasks in order. Every fix is followed by an inspection task: a green test tells you the fix
works, the inspection tells you **why**, and the why is what an interviewer asks for.

Stuck, or want the explanation? The same task number in [`TASKS_GUIDED.md`](TASKS_GUIDED.md) walks
you through it with the expected output.

Commands are for macOS and Linux. On Windows type `.\demo`, `.\inspect` and `.\check`.

**Rules:** work in `start/`. Never edit, delete or disable a test. Do not change anything in
`demo/` or `checkpoint/`.

**The data:** alice has a current account (id 1) with 500 and a savings account (id 2) with 500.
Every demo run and every test starts from there.

**The demo has two modes.** Without `--level` it runs the application's code, so it shows what your
`@Transactional` settings do. With `--level` it runs the same anomaly as plain SQL at that level.

---

## Task 1: See the Statement That Does Not Add Up (15 min)

**Story:** *A customer sends a screenshot of her statement: current 500, savings 500, total 1100.
Nothing is in the error log. Before you change anything, reproduce it and find out what the database
did.*

**Steps:**
1. `docker compose up -d`, then `./demo`. Do the lines add up to the total? Which isolation level
   did the application's transaction run at? Nobody chose it: where does it come from?
2. Open `StatementService.statement`. It is one transaction. Match the three queries to the
   timeline. Between which two did session B commit?
3. `./demo --anomaly phantom`. What is different about what session B does? What is wrong with the
   statement this time?
4. `./demo --anomaly non-repeatable-read --level read-committed` and
   `./demo --anomaly phantom --level read-committed`. This is the same thing in plain SQL. In each
   one, which statement does session A run twice, and what changes between the two runs?
5. Maybe a lower level is even worse. `./demo --anomaly dirty-read --level read-uncommitted`. What
   does session A read while B's change is uncommitted? What does `show transaction_isolation` say?

**Acceptance Criteria** (`./check 1`, already passing: these tests document what Postgres does)
- [ ] You can name the two anomalies and say what the difference between them is
- [ ] You can say what the default isolation level is in Postgres, and what one query can and cannot
      see at that level
- [ ] You can explain why `@Transactional` alone did not protect the statement
- [ ] You can say what READ UNCOMMITTED does in Postgres
- [ ] The five tests in `ReadCommittedIT` pass

**Hints:**
- Non-repeatable read: a row you already read has **changed**. Phantom: a row you had not seen
  **appeared** (or one you saw disappeared)
- A transaction groups your writes. What it lets you see of other people's writes is a separate
  setting

---

## Task 2: Fix the Statement With REPEATABLE READ (10 min)

**Story:** *The statement must describe one moment in time. Whatever commits while it is being
built must not show up in half of it. Fix `StatementService.statement`.*

**Acceptance Criteria** (`./check 2`)
- [ ] A deposit commits between the queries, and the lines still add up to the total
      — `StatementIT.aDepositInTheMiddleDoesNotBreakTheStatement`
- [ ] An account is opened between the queries, and the count still matches the lines
      — `StatementIT.aNewAccountInTheMiddleDoesNotBreakTheStatement`
- [ ] All of its queries run in one REPEATABLE READ transaction
      — `StatementIT.statementRunsAtRepeatableRead`
- [ ] The next statement shows the deposit — `StatementIT.theNextStatementSeesTheDeposit` (already
      passing: keep it that way)
- [ ] `./demo` and `./demo --anomaly phantom` print `RESULT: OK`
- [ ] The baseline tests still pass (`./check`)

**Hints:**
- `@Transactional` has an `isolation` attribute
- The method only reads. Keep `readOnly = true`
- Could you also fix it without touching the isolation level? How many queries would you need?

---

## Task 3: Inspect the Snapshot (15 min)

**Story:** *Your fix passes the tests. Now prove you know why.*

**Steps:**
1. You need two terminals. In the first: `./demo --level read-committed --pause`. While it waits,
   in the second: `./inspect`. Find session A. Is `snapshot_xmin` filled in?
2. The same with `./demo --level repeatable-read --pause`. What is in `snapshot_xmin` now? Look at
   the `xmin` of account 1 under ACCOUNTS. Which transaction wrote the balance of 600, and can
   session A see it?
3. `./demo --pause` (the application this time), and `./inspect`. Which level does the `bank-app`
   session report? Is it holding a snapshot?
4. `./demo --anomaly dirty-read --level repeatable-read --pause`, and `./inspect`. Session A has run
   `begin` but no query yet. Does it hold a snapshot? So when exactly is the snapshot taken?
5. Session A in step 2 never saw the 600. When will it? Which test proves it?

**Acceptance Criteria** (no tests: you answer)
- [ ] You can say what a snapshot is, and when a REPEATABLE READ transaction takes it
- [ ] You can say when a READ COMMITTED transaction takes a snapshot, and how long it keeps it
- [ ] You can explain why nobody had to wait: not the deposit, not the statement
- [ ] You can say whether REPEATABLE READ allows phantoms in Postgres, and what the SQL standard says
- [ ] You can name one cost of a transaction that holds a snapshot for a long time

---

## Task 4: Stop the Lost Update, and Retry the Serialization Failure (25 min)

**Story:** *Two withdrawals of 100 from the same account were paid out, and the balance went from
500 to 400. A snapshot fixed the statement, so use it here too: `Withdrawer.withdraw` at REPEATABLE
READ. Then deal with what the database does to the loser. Fix `Withdrawer`, then
`WithdrawalService`.*

**Acceptance Criteria** (`./check 4`)
- [ ] One attempt that loses the race fails with Spring's `CannotAcquireLockException`, caused by
      SQLSTATE `40001` — `LostUpdateIT.theLosingAttemptFailsWithCannotAcquireLockException`
- [ ] Two withdrawals of 100 from the same account both go through, one on its second attempt, and
      the balance is 300 — `LostUpdateIT.bothWithdrawalsGoThroughAndNothingIsLost`
- [ ] When Postgres cancels the transaction, the withdrawal is tried again and succeeds
      — `WithdrawalServiceRetryTest.retriesAfterASerializationFailure`
- [ ] After 3 cancelled attempts it stops and throws `WithdrawalConflictException`
      — `WithdrawalServiceRetryTest.givesUpAfterThreeAttempts`
- [ ] Insufficient funds is not retried — `WithdrawalServiceRetryTest.doesNotRetryInsufficientFunds`
      (already passing: keep it that way)
- [ ] `WithdrawalService` is not `@Transactional`
      — `WithdrawalServiceRetryTest.theRetryLoopIsOutsideTheTransaction` (already passing)
- [ ] `./demo --anomaly lost-update` prints `RESULT: OK`

**Hints:**
- Change the isolation level first and run `./demo --anomaly lost-update` before writing the retry.
  Read what session A gets
- `CannotAcquireLockException` is a `ConcurrencyFailureException`. Catch that
- The retry must run a **new transaction**. Why is the loop in `WithdrawalService` and not in
  `Withdrawer`?

---

## Task 5: Inspect the Serialization Failure (15 min)

**Story:** *Same again: the tests are green, now show what the database does.*

**Steps:**
1. `./demo --anomaly lost-update --level repeatable-read`. Which statement fails, in which session?
   Write down the SQLSTATE and the message. Why did session B, which did exactly the same thing,
   not fail?
2. `./demo --anomaly lost-update --level read-committed`. Same steps, no error. What did the same
   `update` do this time?
3. Look at `Withdrawer.withdraw`. The exception does not come from the line that sets the balance.
   Where does it come from, and what does that mean for a `try`/`catch` inside this method?
4. `./demo --anomaly lost-update` (your fixed application). Which isolation level does attempt 2
   report, and which balance does it start from? Where did that number come from?
5. Suppose `Withdrawer.withdraw` also sent an email "you withdrew 100" before returning. What
   happens to that email when the transaction is cancelled and run again?

**Acceptance Criteria** (no tests: you answer)
- [ ] You can quote the SQLSTATE and the message, and say which transaction gets it
- [ ] You can name the exception Spring raises, and its parent that you catch
- [ ] You can explain why the retry must be a new transaction that reads again
- [ ] You can explain why the retry cannot live inside the `@Transactional` method
- [ ] You can say what must not be inside a transaction that may be retried

---

## Task 6: Fix the Write Skew With SERIALIZABLE (15 min)

**Story:** *Now the two withdrawals come from different accounts: 800 from current, 800 from
savings, at the same time. Both pass the check "the accounts together hold at least 800". The
accounts end at -600. Your REPEATABLE READ fix from Task 4 is in place and did nothing. Fix
`Withdrawer.withdraw`.*

**Acceptance Criteria** (`./check 6`)
- [ ] 800 from current and 800 from savings at the same time: one goes through, the other is
      refused, the accounts keep 200 — `WriteSkewIT.onlyOneOfTheTwoWithdrawalsGoesThrough`
- [ ] Every attempt runs in a SERIALIZABLE transaction — `WriteSkewIT.everyAttemptRunsAtSerializable`
- [ ] `./demo --anomaly write-skew` prints `RESULT: OK`
- [ ] Task 4 is still green (`./check 4`)

**Hints:**
- Run `./demo --anomaly write-skew` first. Which rows does each session write? Is any row written
  twice?
- `./demo --anomaly write-skew --level repeatable-read`, then `--level serializable`
- The retry from Task 4 is already there. Does it need to change?

---

## Task 7: Inspect SERIALIZABLE (15 min)

**Story:** *SERIALIZABLE sounds like "one at a time". Find out what Postgres really does.*

**Steps:**
1. `./demo --anomaly write-skew --level serializable`. Which statement fails? Read the `Detail` and
   the `Hint`. Did any session wait for the other one?
2. `./demo --anomaly write-skew --level serializable --pause`, and `./inspect` in a second terminal.
   Look at PREDICATE LOCKS. Who holds one, and what does it cover? Is `waits_for_pid` filled in for
   anyone?
3. `./demo --anomaly write-skew --pause` (the application), and `./inspect`. One predicate lock
   belongs to a session that is not in the SESSIONS list. Whose is it, and why is it still there?
4. In step 1 the transaction failed at `commit`. Read `WriteSkewIT.aFailedCommitIsAlsoCannotAcquireLockException`.
   Does the retry need a second `catch` for that case?
5. Read `WriteSkewIT.anInnerIsolationLevelIsIgnored`. A colleague calls `withdrawer.withdraw` from
   a method that is already `@Transactional`. Which level does the withdrawal run at?
6. The reporting team keeps its transactions at READ COMMITTED. Does your SERIALIZABLE withdrawal
   protect the rule against a READ COMMITTED transaction that does the same read-check-write?

**Acceptance Criteria** (no tests: you answer)
- [ ] You can explain what SERIALIZABLE guarantees, in one sentence
- [ ] You can explain how Postgres does it without making anyone wait
- [ ] You can say at which moments a serializable transaction can be cancelled
- [ ] You can name two costs of SERIALIZABLE
- [ ] You can explain why the isolation level has to be set where the transaction starts

---

## Task 8: Compare Every Level, and Isolation Levels vs Locking (20 min)

**Story:** *"Which isolation level would you use?" and "Why not just lock the row?" are the
interview questions. Collect the evidence, then answer them.*

**Steps:**
1. `./demo --matrix`. Which two columns are identical? Which level is the first with no `HAPPENS`?
   Compare the table with the one the SQL standard gives (it is in the guided task). Where is
   Postgres stricter than the standard asks?
2. `./demo --anomaly write-skew --for-update`. This is READ COMMITTED. What did session B do when
   it tried to read? What did it read in the end?
3. Put that next to `./demo --anomaly write-skew --level serializable`. In which run does somebody
   wait? In which run is somebody cancelled?
4. `./demo --anomaly write-skew --for-update --pause`, and `./inspect`. Find `waits_for_pid`. Look
   at `xmax` of the two account rows.
5. A new rule arrives: "a customer may have at most 3 accounts". Two sessions each count 2 accounts
   and each insert one. Can `SELECT ... FOR UPDATE` on the existing rows stop that? Can
   SERIALIZABLE?
6. Decide, and say why: which would you use for the statement, for the withdrawal, and for
   "decrement the stock of a product" from [`../07-locking`](../07-locking/)?

**Acceptance Criteria** (`./check 8`, already passing, and you answer)
- [ ] You can draw the table: four levels, which anomalies each one allows in Postgres
- [ ] You can say in one sentence each what an isolation level is and what a lock is
- [ ] You can say what the "loser" experiences with a lock, and with a higher isolation level
- [ ] You can give one situation where `SELECT ... FOR UPDATE` is the better tool, and one where
      only SERIALIZABLE works
- [ ] `IsolationVsLockingIT` passes

---

## Questions to Ask Afterwards

Answer out loud, in full sentences, before reading the answer in brackets.

- What are the four isolation levels? (READ UNCOMMITTED, READ COMMITTED, REPEATABLE READ,
  SERIALIZABLE, weakest first. Each one rules out more of what a transaction can observe of other
  transactions that run at the same time.)
- What is the default in Postgres? (READ COMMITTED. Every statement sees what was committed when
  that statement started. Two statements in the same transaction can see different data. Spring's
  `@Transactional` without an `isolation` attribute uses the database default.)
- Dirty read, non-repeatable read, phantom read: what is each? (Dirty: you read a change that was
  never committed. Non-repeatable: you read a row twice and it changed in between, because someone
  committed. Phantom: you run a query twice and a row appeared or disappeared, because someone
  committed an insert or delete.)
- Does READ UNCOMMITTED give you dirty reads in Postgres? (No. Postgres accepts the level and runs
  it as READ COMMITTED. There is no way to read uncommitted data in Postgres.)
- What does REPEATABLE READ do in Postgres? (The transaction takes one snapshot at its first query
  and reads from it until it ends. So no non-repeatable reads and, in Postgres, no phantoms either,
  although the SQL standard would allow them. If it tries to update a row that someone changed after
  the snapshot, it is cancelled with SQLSTATE 40001.)
- What is write skew? (Two transactions read the same data, each makes a decision from it, and each
  writes a different row. No row is written twice, so nothing looks like a conflict, but the two
  decisions together break a rule. REPEATABLE READ allows it. SERIALIZABLE does not.)
- What does SERIALIZABLE guarantee? (The result is the same as if the transactions had run one
  after another, in some order. Postgres does not run them one at a time and does not make them
  wait. It tracks what each one read, and when the overlap cannot be explained by any order, it
  cancels one with 40001.)
- What do you do with a 40001? (Run the whole transaction again, a limited number of times. It is
  not a bug in the request. In Spring it arrives as `CannotAcquireLockException`; catch its parent
  `ConcurrencyFailureException`. In production add a short random delay between attempts.)
- Where does the retry go? (Outside the transaction, in a different bean. A cancelled transaction
  cannot be continued, so every attempt needs a new one, and a call to a method on `this` does not
  go through the transactional proxy. With Hibernate the UPDATE is sent at commit, so the exception
  comes out of the call to the transactional method, not from a line inside it.)
- What must not be in a transaction that can be retried? (Anything the database cannot roll back:
  an email, an HTTP call, a message. It would happen once per attempt. Do it after the commit.)
- Is an isolation level a lock? (No. An isolation level is a setting of one transaction: what it may
  see. A lock is a claim on specific rows that makes other transactions wait. With a lock the loser
  waits and then carries on with fresh data. A higher isolation level in Postgres adds no waiting:
  the loser is cancelled and must retry.)
- When `SELECT ... FOR UPDATE` and when SERIALIZABLE? (Lock when you know exactly which rows you
  are going to change and conflicts are common: a stock counter, a seat. Use SERIALIZABLE when the
  decision depends on a set of rows, or on rows that do not exist yet, which you cannot lock. Often
  the best answer is neither: one conditional UPDATE, or a constraint.)
- You put `@Transactional(isolation = SERIALIZABLE)` on a method and it still runs at READ
  COMMITTED. Why? (It was called from inside a transaction that was already open. The method joined
  it, and a transaction has one level, fixed when it starts. Spring ignores the inner setting
  without telling you.)
- Is SERIALIZABLE on one method enough? (No. Postgres only guarantees it between transactions that
  are all SERIALIZABLE. A READ COMMITTED transaction that does the same read-check-write can still
  cause write skew against it.)
- What does a long REPEATABLE READ transaction cost? (It pins a snapshot. While it is open, VACUUM
  cannot remove any row version that the snapshot might still need, so tables bloat. Keep them
  short.)
- Do other databases behave the same? (No. The level names come from the SQL standard, the
  behaviour does not. MySQL's InnoDB, for example, defaults to REPEATABLE READ, not READ COMMITTED.
  Always say which database you are talking about.)

## Tips for Interviewers

- The most common wrong answer is a recited table: "READ UNCOMMITTED allows dirty reads, ..." Ask
  "in Postgres?". A candidate who has run it knows that the first two levels are the same level.
- Ask the candidate to predict `./demo --anomaly write-skew --level repeatable-read` before running
  it. Most people who say "REPEATABLE READ gives me a consistent view" expect it to be safe.
- "I would set it to SERIALIZABLE" is half an answer. The other half is the retry. Ask what the
  user sees when the transaction is cancelled, and watch where the candidate puts the loop.
- A retry loop inside the `@Transactional` method never runs: the exception appears at commit, after
  the method has returned. It is the bug that separates levels.
- Listen for "isolation level" and "lock" being used as the same word. Task 8 steps 2 and 3 are the
  quickest way to pull them apart: same scenario, one run waits, the other is cancelled.
- For senior candidates, ask Task 8 step 5, then ask for a third option. A good answer is a unique
  constraint or a counter row that every writer has to lock.
