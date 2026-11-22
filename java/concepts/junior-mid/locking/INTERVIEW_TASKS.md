# Interview Tasks — Locking

> **In `start/`, `./check` FAILS on purpose: 7 of 18 checks pass.** Tasks 2, 4 and 6 turn the other
> 11 green. Tasks 1, 3, 5 and 7 are inspection tasks: you run things, look, and answer questions.

Do the tasks in order. Every fix is followed by an inspection task: a green test tells you the fix
works, the inspection tells you **why**, and the why is what an interviewer asks for.

Stuck, or want the explanation? The same task number in [`TASKS_GUIDED.md`](TASKS_GUIDED.md) walks
you through it with the expected output.

Commands are for macOS and Linux. On Windows type `.\demo`, `.\inspect` and `.\check`.

**Rules:** work in `start/`. Never edit, delete or disable a test. Do not fix `placeNaive`: it stays
broken so you can compare against it.

---

## Task 1: See the Lost Update (10 min)

**Story:** *The shop has one PS5 left. Support says two customers both got an order confirmation for
it. Before you change anything, reproduce it and find out exactly what the database did.*

**Steps:**
1. `docker compose up -d`, then `./demo`. How many orders were confirmed? What does the stock say?
2. `./demo --sql`. Each line shows the thread that ran the statement. Write down the order of the
   statements across the two threads.
3. Open `OrderPlacer.placeNaive`. Match each SQL statement to the line of Java that produced it.
4. `./demo --buyers 50 --stock 10`. How many orders now? What does the stock say, and what should
   it say?
5. `./demo --fraud-check-ms 0`, a few times. Does the bug still happen? Every time?

**Acceptance Criteria** (`./check 1`, already passing: this test documents the bug)
- [ ] You can name the anomaly
- [ ] You can point to the two SQL statements it happens between
- [ ] You can explain why the stock says 0 and not -1
- [ ] You can explain why a fast machine does not make the bug go away
- [ ] `NaiveStrategyIT.twoBuyersBothGetTheLastItem` passes

**Hints:**
- Both buyers ran the same `select` before either ran an `update`
- Look at what value each `update` writes. Is it computed in the database or in Java?

---

## Task 2: Fix It With a Pessimistic Lock (15 min)

**Story:** *Make the second buyer wait. While one order is being placed for a product, nobody else
may read that product's stock in order to buy it. Fix `OrderPlacer.placePessimistic`.*

**Acceptance Criteria** (`./check 2`)
- [ ] Two buyers, one item: exactly one order is confirmed, the other buyer is told it is sold out
      — `PessimisticLockIT.onlyOneBuyerGetsTheLastItem`
- [ ] The second buyer cannot even read the stock while the first buyer's transaction is open
      — `PessimisticLockIT.secondBuyerWaitsWhileTheFirstHoldsTheLock`
- [ ] 30 buyers, 10 items: exactly 10 orders, 20 sold out, stock ends at 0
      — `PessimisticLockIT.stockIsNeverOversoldUnderLoad`
- [ ] `./demo --strategy pessimistic` prints `RESULT: OK`
- [ ] The baseline tests still pass (`./check`)

**Hints:**
- The lock has to be taken by the **read**, not the write
- Spring Data JPA: `@Lock` on a repository query method, with a `LockModeType`
- A lock only lasts until the end of the transaction. Check that the method is `@Transactional`

---

## Task 3: Inspect the Pessimistic Lock (15 min)

**Story:** *Your fix passes the tests. Now prove you know why.*

**Steps:**
1. `./demo --strategy pessimistic --sql`. Find the statement that changed compared with Task 1.
   What was added to it?
2. In the same output, count the `select` statements each thread ran before the first `update`.
   Compare with Task 1. Why did the second thread's order not go through?
3. Open a second terminal. In the first, run `./demo --strategy pessimistic --pause`. While it
   waits, run `./inspect` in the second. Which session is waiting? For which session? What is the
   other one doing?
4. Look at the timestamps when the demo finishes. When did the losing buyer get its answer, and what
   was the answer?
5. `./demo --strategy pessimistic --buyers 50 --stock 10`, then the same with `--strategy naive`.
   Compare "Time taken".
6. While step 5's pessimistic run is going, how many database connections are in use? (The pool
   holds 10.) What is a waiting buyer holding on to while it waits?

**Acceptance Criteria** (no tests: you answer)
- [ ] You can quote the exact SQL that takes the lock
- [ ] You can say what the second buyer was doing while it waited, and what it saw when it stopped
      waiting
- [ ] You can say when the lock is released
- [ ] You can explain the cost of this fix under load, in terms of time and connections

---

## Task 4: Fix It With Optimistic Locking (20 min)

**Story:** *Waiting is expensive. Most of the time nobody else is buying the same product, so let
everyone go ahead without a lock, and catch the rare collision at the moment of writing. Fix the
optimistic strategy: `Product`, then `OrderService`.*

**Acceptance Criteria** (`./check 4`)
- [ ] Every sale raises the product's version by exactly one
      — `OptimisticLockIT.versionGoesUpByOneOnEverySale`
- [ ] Two buyers, one item: exactly one order is confirmed; the loser retries and is told it is sold
      out — `OptimisticLockIT.onlyOneBuyerGetsTheLastItem`
- [ ] Two buyers, two items: the loser retries and still gets one, and no stock update is lost
      — `OptimisticLockIT.theLoserRetriesAndBuysWhenStockRemains`
- [ ] 20 buyers, 5 items: never oversold, and stock left + orders placed always equals 5
      — `OptimisticLockIT.stockAndOrdersAlwaysAddUpUnderLoad`
- [ ] When the version check fails, the order is tried again and succeeds
      — `OrderServiceRetryTest.retriesWhenTheVersionCheckFails`
- [ ] After 3 failed attempts it stops and throws `OrderConflictException` (a 409, not a 500)
      — `OrderServiceRetryTest.givesUpAfterThreeAttempts`
- [ ] A sold-out product is not retried
      — `OrderServiceRetryTest.doesNotRetryWhenSoldOut` (already passing: keep it that way)
- [ ] `./demo --strategy optimistic` prints `RESULT: OK` and no buyer gets a 500

**Hints:**
- The `product` table already has a `version` column. One annotation on one new field in `Product`
  is enough for Hibernate to start checking it
- Do the entity change first and run `./demo --strategy optimistic` before writing the retry. Look
  at what the losing buyer gets
- The failure surfaces as Spring's `OptimisticLockingFailureException`, thrown when the transaction
  commits
- The retry must run a **new transaction**. Why is `OrderService` not `@Transactional`?

---

## Task 5: Inspect the Optimistic Lock (15 min)

**Story:** *Same again: the tests are green, now show what the database does.*

**Steps:**
1. `./demo --strategy optimistic --sql`. Find the `update` of `product`. What is in its `where`
   clause that was not there in Task 1? Who wrote that SQL?
2. Both threads run that `update`. One of them changes 1 row, the other 0. Which statement does the
   loser run next, and why?
3. Read "Product version" and "Version conflicts detected" in the summary. Run
   `./demo --strategy optimistic --stock 2`. What changes?
4. `./demo --strategy optimistic --pause`, and `./inspect` in a second terminal. Is any session
   waiting for another one? Compare with Task 3 step 3.
5. `./demo --strategy optimistic --buyers 50 --stock 10`. How many buyers "gave up after retries"?
   How many version conflicts were there? Could a buyer give up while stock was still available?
6. Change `MAX_ATTEMPTS` to 10 and repeat step 5. What improves, and what does it cost? Put it back
   to 3.

**Acceptance Criteria** (no tests: you answer)
- [ ] You can quote the SQL that detects the conflict, and say what "0 rows updated" means
- [ ] You can explain why the retry has to re-read the product
- [ ] You can say what a buyer experiences when it loses once, and when it loses three times
- [ ] You can explain why optimistic locking gets worse as more buyers want the same row

---

## Task 6: Fix It With One Atomic UPDATE (15 min)

**Story:** *The whole problem is the gap between the read and the write. For this rule ("there must
be enough stock") you can close the gap completely: let the database check and decrement in one
statement. Fix `OrderPlacer.placeAtomic`.*

**Acceptance Criteria** (`./check 6`)
- [ ] Two buyers, one item: exactly one order is confirmed, the other buyer is told it is sold out
      — `AtomicUpdateIT.onlyOneBuyerGetsTheLastItem`
- [ ] 30 buyers, 10 items: exactly 10 orders, 20 sold out, stock ends at 0, nobody gives up
      — `AtomicUpdateIT.stockIsNeverOversoldUnderLoad`
- [ ] An unknown product is still a `ProductNotFoundException`, not "sold out"
- [ ] `./demo --strategy atomic` prints `RESULT: OK`

**Hints:**
- `@Modifying` + `@Query` with an `update` that has the stock condition in its `where` clause. The
  method can return `int`: the number of rows changed
- 0 rows changed means one of two things. How do you tell them apart?
- Where should the fraud check go now: before or after the `update`? What is locked in each case?

---

## Task 7: Compare All Three Under Load (15 min)

**Story:** *"Which one would you use?" is the interview question. Collect the numbers, then answer
it.*

**Steps:**
1. Run each of these and note confirmed, sold out, gave up and time taken:
   - `./demo --strategy naive --buyers 50 --stock 10`
   - `./demo --strategy pessimistic --buyers 50 --stock 10`
   - `./demo --strategy optimistic --buyers 50 --stock 10`
   - `./demo --strategy atomic --buyers 50 --stock 10`
2. Run the three correct ones again with `--stock 1000`, so nobody is fighting over the last items.
   Which strategy changed the most? Why?
3. `./demo --strategy atomic --sql`. How long is the product row locked for, compared with the
   pessimistic run in Task 3?
4. A new rule arrives: "a customer may buy at most 2 PS5s across all their orders". Which of the
   three fixes can still enforce it, and which cannot?

**Acceptance Criteria** (no tests: you answer)
- [ ] You can rank the three fixes by time taken and explain the ranking
- [ ] You can say which fix makes buyers wait, which makes them retry, and which does neither
- [ ] You can give one situation where each fix is the right choice
- [ ] You can explain why the atomic update is not always possible

---

## Questions to Ask Afterwards

Answer out loud, in full sentences, before reading the answer in brackets.

- Two requests update the same row at the same time. What happens? (With plain read-then-write code
  both read the same value, both compute a new value from it, and the second write overwrites the
  first. That is a lost update. The default isolation level in Postgres, READ COMMITTED, allows it,
  because nothing connects a transaction's read to its later write.)
- What is the difference between optimistic and pessimistic locking? (Pessimistic assumes a conflict
  will happen: it locks the row when it reads it, and others wait. Optimistic assumes it will not:
  nobody locks, every row carries a version, and the write only succeeds if the version is unchanged.
  The loser finds out at write time and has to retry.)
- Is `SELECT ... FOR UPDATE` optimistic or pessimistic? (Pessimistic. It takes a row lock at read
  time and holds it until the transaction commits or rolls back.)
- How does `@Version` work? (Hibernate adds `AND version = ?` to the UPDATE and sets
  `version = version + 1`. If another transaction committed first, the UPDATE matches 0 rows and
  Hibernate throws an optimistic locking failure. No database lock is held between the read and the
  write.)
- Is optimistic locking an isolation level? (No. Isolation levels are a database setting for what a
  transaction can see of other transactions. Optimistic locking is an application technique using a
  version column. They solve overlapping problems, but they are different things, and mixing them up
  is a common wrong answer.)
- When would you choose each? (Pessimistic when conflicts are common or redoing the work is
  expensive, for example the last seats of a flight, or a transfer between two accounts. Optimistic
  when conflicts are rare and reads far outnumber writes, for example editing a profile or a
  document. An atomic conditional UPDATE when the whole rule fits in one WHERE clause.)
- What does the user see when optimistic locking fails? (After the retries run out, a 409 Conflict
  with a message that it is safe to try again. Never a 500. For a form a person edited, you do not
  retry silently: you show them that someone else changed the record.)
- Why must the retry be outside the transaction? (The failed transaction is rolled back and its
  persistence context holds stale data. A retry needs a new transaction that re-reads the row. In
  Spring that means the retry loop lives in a different bean from the `@Transactional` method,
  because a call to a method on `this` does not go through the transactional proxy.)
- What can go wrong with pessimistic locks? (Deadlocks, when two transactions lock the same rows in
  opposite order: lock rows in a consistent order, for example by id. Long waits and exhausted
  connection pools, because a waiting transaction holds its connection: keep transactions short and
  set a lock timeout. Never call a slow external service while holding a row lock.)
- What would you add in the database as a last line of defence? (`CHECK (stock >= 0)` on the column.
  It does not fix the lost update, but it stops any code path from ever storing negative stock.)
- Does any of this work across two databases or two services? (No. A row lock and a version column
  only protect one row in one database. Across services you need something else: an idempotency key,
  a reservation with an expiry, or a saga.)

## Tips for Interviewers

- The most common wrong answer is confident and vague: "I would use a transaction" or "I would set
  the isolation level to serializable". Ask which statement takes the lock and when it is released.
- Ask the candidate to predict the output of `./demo --strategy optimistic --buyers 50 --stock 10`
  before running it. A strong candidate predicts that some buyers give up while stock remains.
- Watch where they put the retry. A retry loop inside the `@Transactional` method retries with stale
  data in a transaction that is already marked for rollback. It is the bug that separates levels.
- In Task 6, a candidate who puts the fraud check after the `UPDATE` has just rebuilt the pessimistic
  lock: the row stays locked for the whole check. Ask what is locked, and for how long.
- For senior candidates, Task 7 step 4 is the real question: the atomic update is the fastest fix and
  the first one to stop working when the rule grows.
