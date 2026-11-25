# Guided Tasks — Isolation Levels

The same eight tasks as [`INTERVIEW_TASKS.md`](INTERVIEW_TASKS.md), step by step: the command to
run, the output you should see, why it happens, and the change to make.

Commands are for macOS and Linux. On Windows type `.\demo`, `.\inspect` and `.\check`.

Process ids (`pid`), transaction ids (`xmin`, `own_xid`) and times will differ on your machine. The
steps, the values and the `RESULT` line should match: the demo does not race two threads, it runs
the steps of two sessions in a fixed order.

**Before you start**

```bash
cd start
docker compose up -d
```

**How to read the demo.** Session A is on the left, session B is indented. `->` is what came back.
`#` is something the application works out in Java, outside the database.

**Two modes.** `./demo` without `--level` runs the application's code, so it changes when you change
the code. `./demo --level ...` runs the same anomaly as plain SQL at the level you ask for, whatever
the code says.

---

## Task 1: See the Statement That Does Not Add Up

No code in this task. You are collecting evidence.

### Run it

```bash
./demo
```

### What you should see

```
Anomaly: non-repeatable-read | running the application: StatementService.statement
Session A is the application, building alice's statement: the lines first, then the count and the total.
Session B deposits 100 and commits in between.
Before: alice has two accounts, current (id 1) = 500 and savings (id 2) = 500.

  A | StatementService.statement("alice")
  A |   -> transaction isolation level: read committed
  A |   -> query 1 done: the lines are read
          B | begin
          B | update account set balance = balance + 100 where id = 1
          B |   -> 1 row
          B | commit
  A |   -> queries 2 and 3 done: the count and the total are read

The statement the customer gets:
    current         500
    savings         500
    -------------------
    2 accounts     1100

RESULT: NON-REPEATABLE READ. The lines add up to 1000, the total says 1100. The queries of one transaction saw two different moments.
```

500 + 500 = 1100. Nothing crashed and nothing was logged. Every number on that statement was true
at some moment. They were just not true at the same moment.

### Find it in the code

Open `StatementService.statement`:

```java
@Transactional(readOnly = true)
public Statement statement(String owner) {
    List<Line> lines = accounts.findByOwnerOrderById(owner)...;   // QUERY 1: the lines
    checkpoint.reached(Checkpoint.LINES_READ);                    // ...time passes...
    long accountCount = accounts.countByOwner(owner);             // QUERY 2: how many accounts
    long total = accounts.totalBalance(owner);                    // QUERY 3: the total
    return new Statement(owner, lines, accountCount, total);
}
```

It is `@Transactional`, and it still went wrong. A transaction makes **your writes** succeed or fail
together. What you can **see** of other people's writes is a different setting: the **isolation
level**. Nobody set one here, so the transaction ran at the database default. The demo printed it:
`read committed`.

At READ COMMITTED every **statement** sees what was committed at the moment that statement started.
Query 1 started before the deposit, queries 2 and 3 after it.

(`checkpoint.reached(...)` does nothing in production. The demo and the tests use it to let session
B in at exactly that point. Without it you would have to be unlucky to see the bug, which is what
makes it so hard to find in real life.)

### The same thing in plain SQL

```bash
./demo --anomaly non-repeatable-read --level read-committed
```

```
  A | begin isolation level read committed
  A | select balance from account where id = 1
  A |   -> 500
          B | begin
          B | update account set balance = balance + 100 where id = 1
          B |   -> 1 row
          B | commit
  A | select balance from account where id = 1
  A |   -> 600
  A | commit

RESULT: NON-REPEATABLE READ. Session A read the same row twice in one transaction and got 500, then 600.
```

Same transaction, same query, same row, two answers. That is a **non-repeatable read**.

### A row that was not there

```bash
./demo --anomaly phantom
```

```
          B | insert into account (id, owner, name, balance) values (3, 'alice', 'holiday', 250)
          ...
The statement the customer gets:
    current         500
    savings         500
    -------------------
    3 accounts     1250

RESULT: PHANTOM READ. The statement lists 2 accounts and then says there are 3. The lines add up to 1000, the total says 1250.
```

This time no row that A had read was changed. A **new row** appeared that matches A's query. That is
a **phantom read**. In plain SQL: `./demo --anomaly phantom --level read-committed` counts 2 rows,
then 3.

| | What happened to the data A reads |
|---|---|
| Non-repeatable read | A row A already read was **updated** (or deleted) and committed |
| Phantom read | A row A had never seen was **inserted** and committed, and now matches A's query |

### Is the level below even worse?

The textbook says READ UNCOMMITTED allows **dirty reads**: reading a change that was never
committed. Try it:

```bash
./demo --anomaly dirty-read --level read-uncommitted
```

```
  A | begin isolation level read uncommitted
  A | show transaction_isolation
  A |   -> read uncommitted
          B | begin
          B | update account set balance = 0 where id = 1
          B |   -> 1 row
  A | select balance from account where id = 1
  A |   -> 500
          B | rollback
  A | commit

RESULT: NO DIRTY READ. Session A read 500 while B's uncommitted 0 was in the table. Postgres accepts READ UNCOMMITTED and runs it as READ COMMITTED.
```

Postgres takes the level, even reports it back, and then behaves as READ COMMITTED. **There are no
dirty reads in Postgres, at any level.** If an interviewer asks "which level allows dirty reads?",
the textbook answer is READ UNCOMMITTED, and the good answer adds "but not in Postgres".

### Check

```bash
./check 1
```

```
  [PASS] READ COMMITTED: non-repeatable read. One transaction reads a balance twice and gets 500, then 600
  ...
  5 of 5 pass
```

These pass on purpose. They pin down what Postgres does, so you can see what the fixes change.

---

## Task 2: Fix the Statement With REPEATABLE READ

**The idea:** the three queries must all look at the database as it was at one moment. That is what
REPEATABLE READ gives you: the transaction takes one **snapshot** and every query reads from it.

Before you start, `./check 2` says 3 of 6 pass. The three that pass describe Postgres. The three
that fail describe your code.

### The change

In `StatementService`:

```java
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public Statement statement(String owner) {
```

Import `org.springframework.transaction.annotation.Isolation`. That is the whole fix.

### After

```bash
./demo
```

```
  A | StatementService.statement("alice")
  A |   -> transaction isolation level: repeatable read
  ...
The statement the customer gets:
    current         500
    savings         500
    -------------------
    2 accounts     1000

RESULT: OK. 2 lines, 2 accounts, and the lines add up to the total: 1000. What session B committed is not in this statement. It will be in the next one.
```

Three things to notice:

- **B was not blocked.** Its deposit went through and is committed. A just does not look at it.
- **The statement is not "wrong" for showing 500.** It is a correct picture of the moment the
  transaction took its snapshot. The next statement shows 600.
- `./demo --anomaly phantom` is also `OK` now. The SQL standard lets REPEATABLE READ have phantoms.
  **Postgres does not have them**: a snapshot does not contain rows that were inserted later.

`./check 2` now says 6 of 6 pass.

### You did not have to use an isolation level

One statement always sees one moment, even at READ COMMITTED. If you read the lines once and add
them up in Java, or fetch everything in one query, there is no second query to disagree with the
first. That is often the simplest fix. You need REPEATABLE READ when one piece of work really needs
several queries that must agree, like a report or an export.

---

## Task 3: Inspect the Snapshot

You need two terminals, both in `start/`.

### Step 1: READ COMMITTED holds nothing between statements

Terminal 1: `./demo --level read-committed --pause`. Terminal 2, while it says "Paused for 15
seconds": `./inspect`.

```
SESSIONS INSIDE A TRANSACTION (snapshot_xmin is filled in while the session holds a snapshot)
  pid   session                  state                snapshot_xmin  own_xid  waits_for_pid  in_transaction_ms  last_statement
  ----  -----------------------  -------------------  -------------  -------  -------------  -----------------  ----------------------------------------
  2844  demo A (read committed)  idle in transaction                                         8958               select balance from account where id = 1
```

Session A is in the middle of a transaction and has already run a SELECT. `snapshot_xmin` is empty:
it is holding **no snapshot**. At READ COMMITTED a snapshot lives for one statement. The next
statement takes a new one, and sees whatever is committed by then.

(Postgres has no column that shows another session's isolation level. The demo's sessions put their
own level into their name, which is why you can read it here.)

### Step 2: REPEATABLE READ keeps one

`./demo --level repeatable-read --pause` in terminal 1, `./inspect` in terminal 2:

```
ACCOUNTS (what a NEW transaction would read. xmin = the transaction that wrote this version of
          the row, xmax = a transaction that has locked or replaced it, 0 = none)
  id  name     balance  xmin  xmax
  --  -------  -------  ----  ----
  1   current  600      965   0
  2   savings  500      964   0

SESSIONS INSIDE A TRANSACTION (snapshot_xmin is filled in while the session holds a snapshot)
  pid   session                   state                snapshot_xmin  own_xid  waits_for_pid  in_transaction_ms  last_statement
  ----  ------------------------  -------------------  -------------  -------  -------------  -----------------  ----------------------------------------
  5071  demo A (repeatable read)  idle in transaction  965                                    8324               select balance from account where id = 1
```

Your numbers will differ. Read them like this:

- Every transaction that writes gets a number, a **transaction id**. Every row version remembers the
  transaction that wrote it: that is `xmin` in ACCOUNTS. The balance of 600 was written by
  transaction 965, session B's deposit.
- Session A's `snapshot_xmin` is 965. It means: "my snapshot contains what transactions **before**
  965 committed". Transaction 965 itself is not in it.
- So when A reads account 1 again, Postgres skips the version written by 965 and gives A the older
  version, the one with 500. Postgres keeps old versions of rows for exactly this purpose. The
  technique is called MVCC, multi-version concurrency control.

That is also why nobody waited. B wrote a **new version** of the row, A keeps reading the **old
version**. Readers do not block writers and writers do not block readers.

### Step 3: your application

`./demo --pause` in terminal 1, `./inspect` in terminal 2:

```
  2976  bank-app (repeatable read)  idle in transaction  915  ...
```

The application's session reports `repeatable read` and holds a snapshot. That is your annotation
at work.

### Step 4: when is the snapshot taken?

`./demo --anomaly dirty-read --level repeatable-read --pause`, then `./inspect`:

```
  3915  demo A (repeatable read)  idle in transaction                                         13957              show transaction_isolation
```

A has run `begin isolation level repeatable read`, and `snapshot_xmin` is still empty. **The
snapshot is taken by the first query, not by `begin`.**

### Step 5: when does A see the 600?

In its next transaction. The snapshot ends with the transaction. `StatementIT.theNextStatementSeesTheDeposit`
proves it for the application.

### What to take away

- READ COMMITTED: a new snapshot for every statement.
- REPEATABLE READ: one snapshot, taken at the first query, kept until the transaction ends.
- Nobody waits. The price is paid somewhere else: while a snapshot is open, Postgres cannot clean
  up row versions that the snapshot might still need. A transaction that stays open for an hour
  makes every busy table grow. Keep them short.

---

## Task 4: Stop the Lost Update, and Retry the Serialization Failure

### Before

```bash
./demo --anomaly lost-update
```

```
  A | WithdrawalService.withdraw(1, 100)
  A |   -> attempt 1, transaction isolation level: read committed
  A |   -> funds checked: enough for 100
          B | WithdrawalService.withdraw(1, 100)
          ...
          B |   -> returns 400, the new balance of account 1
  A |   -> returns 400, the new balance of account 1

RESULT: LOST UPDATE. 2 withdrawals of 100 were paid out, but the balance only went from 500 to 400.
```

Open `Withdrawer.withdraw`. It reads the balance, subtracts **in Java**, and writes the result:

```java
Account account = accounts.findById(accountId)...;                    // READ this account
long available = accounts.totalBalance(account.getOwner());           // READ all the customer's accounts
if (available < amount) { throw new InsufficientFundsException(...); } // CHECK the rule
checkpoint.reached(Checkpoint.FUNDS_CHECKED);                         // ...time passes...
account.setBalance(account.getBalance() - amount);                    // WRITE a value computed from the READ
```

Both sessions read 500, both wrote 500 - 100 = 400. The bank paid out 200 and took 100 off the
account. (This is the bug of [`../07-locking`](../07-locking/). There you fixed it with locks. Here
you fix it with an isolation level, and see what is different.)

### Step 1: REPEATABLE READ

In `Withdrawer`:

```java
@Transactional(isolation = Isolation.REPEATABLE_READ)
public long withdraw(long accountId, long amount) {
```

Import `org.springframework.transaction.annotation.Isolation`.

### Look before you go on

Do not write the retry yet. Run:

```bash
./demo --anomaly lost-update
```

```
  A | WithdrawalService.withdraw(1, 100)
  A |   -> attempt 1, transaction isolation level: repeatable read
  A |   -> funds checked: enough for 100
          B | WithdrawalService.withdraw(1, 100)
          ...
          B |   -> returns 400, the new balance of account 1
  A |   -> THROWS org.springframework.dao.CannotAcquireLockException
  A |   -> caused by PSQLException, SQLSTATE 40001: ERROR: could not serialize access due to concurrent update

Withdrawals paid out:  1 x 100
Current (id 1):        400

RESULT: NOT HANDLED. The data is right, because Postgres cancelled session A's transaction. But the customer got a CannotAcquireLockException. Nothing was wrong with the request: it has to be run again.
```

The data is already correct: one withdrawal paid out, balance 400. Session A wanted to update a row
that had changed since A took its snapshot. At READ COMMITTED Postgres lets that through. At
REPEATABLE READ it refuses: A's decision was made from a picture that is no longer true.

This error has a name and a number: a **serialization failure**, SQLSTATE **`40001`**. It does not
mean the request was wrong. It means "this transaction cannot finish, run it again".

Note the Java exception. Spring Boot 4 with Hibernate 7 turns `40001` into
`CannotAcquireLockException`. The name is misleading: no lock was involved and nobody waited. Its
parent classes are `PessimisticLockingFailureException`, then `ConcurrencyFailureException`.

Half a fix: the data is safe and the customer sees a crash.

### Step 2: retry in WithdrawalService

Replace the `withdraw` method in `WithdrawalService`:

```java
public long withdraw(long accountId, long amount) {
    for (int attempt = 1; ; attempt++) {
        try {
            return withdrawer.withdraw(accountId, amount);
        } catch (ConcurrencyFailureException e) {
            if (attempt == MAX_ATTEMPTS) {
                throw new WithdrawalConflictException(accountId, attempt, e);
            }
        }
    }
}
```

Import `org.springframework.dao.ConcurrencyFailureException`.

Three things to notice:

- **The retry is in `WithdrawalService`, not in `Withdrawer`.** `withdrawer.withdraw(...)` is a call
  to another Spring bean, so every attempt goes through the transactional proxy and gets a brand
  new transaction, with a new snapshot. A cancelled transaction cannot be continued. If the loop
  were inside the `@Transactional` method, it would not even see the exception (Task 5 shows why).
- **Only the serialization failure is retried.** `InsufficientFundsException` is not caught: trying
  again cannot put money in the account.
- **It gives up.** After 3 attempts it throws `WithdrawalConflictException`. A retry loop with no
  limit can spin for ever under load.

### After

```bash
./demo --anomaly lost-update
```

```
          B |   -> returns 400, the new balance of account 1
  A |   -> attempt 2 (a new transaction: the last one did not commit), transaction isolation level: repeatable read
  A |   -> funds checked: enough for 100
  A |   -> returns 300, the new balance of account 1

Withdrawals paid out:  2 x 100
Current (id 1):        300

RESULT: OK. 2 x 100 paid out, both accounts together went from 1000 to 800. Session A was cancelled once and ran again in a new transaction.
```

`./check 4` now says 8 of 8 pass. Run `./check` too: Task 2 must still be green.

---

## Task 5: Inspect the Serialization Failure

### Step 1: which statement fails

```bash
./demo --anomaly lost-update --level repeatable-read
```

```
  A | begin isolation level repeatable read
  A | select balance from account where id = 1
  A |   -> 500
          B | begin isolation level repeatable read
          B | select balance from account where id = 1
          B |   -> 500
          B |   # 500 - 100 = 400, worked out in the application
          B | update account set balance = 400 where id = 1
          B |   -> 1 row
          B | commit
  A |   # 500 - 100 = 400, worked out in the application
  A | update account set balance = 400 where id = 1
  A |   -> ERROR, SQLSTATE 40001: could not serialize access due to concurrent update
  A | rollback
  A |   # this transaction was cancelled, so there is nothing to commit
```

A and B did exactly the same thing. B committed first, so B won. The rule is **first committer
wins**: the transaction that tries to change a row which someone else changed after its snapshot is
the one that is cancelled.

### Step 2: the same steps at READ COMMITTED

```bash
./demo --anomaly lost-update --level read-committed
```

No error. A's `update account set balance = 400` simply runs, on top of B's 400. READ COMMITTED has
no snapshot to compare with, so it cannot know that A's 400 was computed from an old 500.

### Step 3: where the exception comes from

In `Withdrawer.withdraw`, `account.setBalance(...)` only changes a Java object. Hibernate sends the
UPDATE when the transaction **commits**, which is after the method has returned. So the exception
does not come from any line inside `withdraw`. It comes out of the **call** to
`withdrawer.withdraw(...)`.

A `try`/`catch` inside the `@Transactional` method would never see it. This is the second reason
the retry has to be outside.

### Step 4: the retry reads again

In the `After` output of Task 4, attempt 2 "returns 300". It did not reuse the 500 from attempt 1.
It is a new transaction with a new snapshot, it read 400, and it wrote 400 - 100 = 300. A retry
that reuses values from the failed attempt brings the lost update straight back.

### Step 5: what must not be inside

Everything in a cancelled transaction is rolled back by the database. An email, an HTTP call or a
message to a queue is not. If `withdraw` sent an email, the customer would get one per attempt, for
one withdrawal. Whatever cannot be rolled back goes after the commit, outside the method that is
retried.

### What to take away

- SQLSTATE `40001` = serialization failure = "run this transaction again".
- Spring: `CannotAcquireLockException`. Catch `ConcurrencyFailureException`.
- The retry is a new transaction that reads everything again, in another bean, with a limit.
- Nobody waited at any point. The loser paid by doing its work twice.

---

## Task 6: Fix the Write Skew With SERIALIZABLE

### Before

Your code is at REPEATABLE READ with a retry. Now the two withdrawals use **different** accounts:

```bash
./demo --anomaly write-skew
```

```
  A | WithdrawalService.withdraw(1, 800)
  A |   -> attempt 1, transaction isolation level: repeatable read
  A |   -> funds checked: enough for 800
          B | WithdrawalService.withdraw(2, 800)
          ...
          B |   -> returns -300, the new balance of account 2
  A |   -> returns -300, the new balance of account 1

Withdrawals paid out:  2 x 800
Current (id 1):        -300
Savings (id 2):        -300
Both together:         -600

RESULT: WRITE SKEW. Both withdrawals of 800 went through and the accounts hold -600 together. The rule is broken, and neither transaction changed a row the other one changed.
```

The rule says the accounts together must stay at 0 or above. They hold -600, and REPEATABLE READ
did not object.

### Why REPEATABLE READ cannot see it

```bash
./demo --anomaly write-skew --level repeatable-read
```

```
  A | begin isolation level repeatable read
  A | select balance from account where owner = 'alice' order by id
  A |   -> 500, 500
  A |   # 500 + 500 = 1000, enough for 800: go ahead
          B | begin isolation level repeatable read
          B | select balance from account where owner = 'alice' order by id
          B |   -> 500, 500
          B |   # 500 + 500 = 1000, enough for 800: go ahead
  A | update account set balance = balance - 800 where id = 1
  A |   -> 1 row
          B | update account set balance = balance - 800 where id = 2
          B |   -> 1 row
  A | commit
          B | commit

RESULT: WRITE SKEW. Both sessions saw 1000, both withdrew 800, both committed. ...
```

In Task 4 both sessions wrote **the same row**, and that is what REPEATABLE READ checks. Here A
writes row 1 and B writes row 2. No row is written twice. Each decision was right for the snapshot
it was made from. Together they are wrong.

This is **write skew**: two transactions read overlapping data, decide, and write **different**
rows. The classic example is a hospital rule "at least one doctor must be on call": two doctors
each see two on call, and both sign off.

### The change

In `Withdrawer`:

```java
@Transactional(isolation = Isolation.SERIALIZABLE)
public long withdraw(long accountId, long amount) {
```

### After

```bash
./demo --anomaly write-skew
```

```
  A | WithdrawalService.withdraw(1, 800)
  A |   -> attempt 1, transaction isolation level: serializable
  A |   -> funds checked: enough for 800
          ...
          B |   -> returns -300, the new balance of account 2
  A |   -> refused, InsufficientFundsException: Cannot withdraw 800 from account 1: the customer's accounts hold 200 in total

Both together:         200

RESULT: OK. 1 x 800 paid out, both accounts together went from 1000 to 200. Session A passed the check, was cancelled, ran again in a new transaction and was refused.
```

Session A's first attempt was cancelled with `40001`. The retry you wrote in Task 4 caught it and
ran A again. The second attempt read 200, and 200 is not enough for 800: a clean
`InsufficientFundsException`. You did not have to change the retry at all.

```bash
./check 6
./check
```

`./check` should now print 31 passes and no failures.

---

## Task 7: Inspect SERIALIZABLE

### Step 1: nobody waits, one is cancelled

```bash
./demo --anomaly write-skew --level serializable
```

The steps are the same as at REPEATABLE READ until the end:

```
  A | commit
          B | commit
          B |   -> ERROR, SQLSTATE 40001: could not serialize access due to read/write dependencies among transactions
          B |   -> Detail: Reason code: Canceled on identification as a pivot, during commit attempt.
          B |   -> Hint: The transaction might succeed if retried.
```

SERIALIZABLE promises: the result is the same as if the transactions had run **one after another**,
in some order. "A then B" would have refused B. "B then A" would have refused A. Both going through
fits neither order, so Postgres cancels one.

It does not get there by running them one at a time. Both sessions read at once and both wrote at
once. Postgres remembers **what each transaction read**, and notices that each one read something
the other one then changed. The hint tells you what to do about it.

### Step 2: the memory of what was read

`./demo --anomaly write-skew --level serializable --pause` in terminal 1, `./inspect` in terminal 2:

```
SESSIONS INSIDE A TRANSACTION (snapshot_xmin is filled in while the session holds a snapshot)
  pid   session                state                snapshot_xmin  own_xid  waits_for_pid  in_transaction_ms  last_statement
  ----  ---------------------  -------------------  -------------  -------  -------------  -----------------  --------------------------------------------
  3376  demo A (serializable)  idle in transaction  936            936                     8944               update account set balance = balance - 800 w
  3377  demo B (serializable)  idle in transaction  936            937                     8883               update account set balance = balance - 800 w

PREDICATE LOCKS (SIReadLock: what SERIALIZABLE sessions have read. They block nobody)
  pid   session                relation  covers    page  row_on_page
  ----  ---------------------  --------  --------  ----  -----------
  3376  demo A (serializable)  account   relation
  3377  demo B (serializable)  account   relation
```

- Both sessions hold a snapshot, as at REPEATABLE READ. SERIALIZABLE in Postgres is REPEATABLE READ
  plus this tracking.
- Each one holds a `SIReadLock` on `account`. Despite the name it is not a lock: `waits_for_pid` is
  empty for both. It is a note that says "I read this".
- `covers` says `relation`: the whole table. The table has two rows, so Postgres read all of it,
  and the note covers all of it. A wider note means more transactions look like they conflict.
  SERIALIZABLE may cancel a transaction that would have been fine. That is allowed: it must never
  let a wrong result through, and a retry costs only time.

### Step 3: a committed transaction still matters

`./demo --anomaly write-skew --pause` (your application) in terminal 1, `./inspect` in terminal 2:

```
  3445  bank-app (serializable)  idle in transaction  940  ...

PREDICATE LOCKS (SIReadLock: what SERIALIZABLE sessions have read. They block nobody)
  3445  bank-app (serializable)  account   relation
  3446  bank-app                 account   relation
```

Session 3446 is not in the list of open transactions: it is session B, and it has committed. Its
note is still there, because session A overlapped with it and is not finished. Postgres needs it to
judge A.

### Step 4: it can fail at COMMIT

In step 1 every statement of session B worked and the **`commit`** failed. In your application's
run the failure came earlier, at the UPDATE. Both happen. `WriteSkewIT.aFailedCommitIsAlsoCannotAcquireLockException`
shows that Spring raises the same `CannotAcquireLockException` when the commit fails, so one `catch`
covers both. It also means: at SERIALIZABLE, do not believe a transaction worked until the commit
has returned.

### Step 5: the level is fixed when the transaction starts

`WriteSkewIT.anInnerIsolationLevelIsIgnored`: a "serializable" block that runs inside a transaction
that is already open runs at **that** transaction's level, here `read committed`. No error, no
warning. If a colleague calls `withdrawer.withdraw` from their own `@Transactional` method, the
withdrawal joins their transaction and your `SERIALIZABLE` is ignored.

### Step 6: everybody has to take part

A READ COMMITTED transaction is not covered. Postgres only gives the guarantee among transactions
that are all SERIALIZABLE. We ran the
write skew with session A at SERIALIZABLE and session B at READ COMMITTED: both committed, and the
accounts ended at -600.

### What to take away

- SERIALIZABLE = "as if one after another". Postgres checks it afterwards instead of making
  transactions queue.
- A transaction can be cancelled at any statement, including COMMIT. Always `40001`, always retry.
- Costs: retries, some of them unnecessary, and bookkeeping for everything that is read.
- It only works if every transaction that touches the data uses it, and if the level is set where
  the transaction starts.

---

## Task 8: Compare Every Level, and Isolation Levels vs Locking

### Step 1: the whole table

```bash
./demo --matrix
```

```
Every anomaly at every isolation level: plain SQL in two sessions, 20 runs.

                       read-uncommitted  read-committed    repeatable-read   serializable
  dirty-read           no                no                no                no
  non-repeatable-read  HAPPENS           HAPPENS           no                no
  phantom              HAPPENS           HAPPENS           no                no
  lost-update          HAPPENS           HAPPENS           40001             40001
  write-skew           HAPPENS           HAPPENS           HAPPENS           40001

RESULT: read-uncommitted and read-committed behave the same. Levels with no anomaly left: serializable.
```

The SQL standard only says what each level **must** prevent:

| SQL standard | dirty read | non-repeatable read | phantom read |
|---|---|---|---|
| READ UNCOMMITTED | allowed | allowed | allowed |
| READ COMMITTED | prevented | allowed | allowed |
| REPEATABLE READ | prevented | prevented | allowed |
| SERIALIZABLE | prevented | prevented | prevented |

Postgres is stricter in two places: READ UNCOMMITTED has no dirty reads, and REPEATABLE READ has no
phantoms. `40001` in the output means: it did not happen, because Postgres cancelled one
transaction. And the standard's table does not mention lost updates or write skew at all, which is
why people who only know the table are surprised by Task 6.

### Step 2: the same problem, solved with a lock

```bash
./demo --anomaly write-skew --for-update
```

```
  A | begin isolation level read committed
  A | select balance from account where owner = 'alice' order by id for update
  A |   -> 500, 500
  A |   # 500 + 500 = 1000, enough for 800: go ahead
          B | begin isolation level read committed
          B | select balance from account where owner = 'alice' order by id for update
          B |   -> WAITING. Another session holds a lock this statement needs
  A | update account set balance = balance - 800 where id = 1
  A |   -> 1 row
  A | commit
          B |   -> (stopped waiting) -300, 500
          B |   # -300 + 500 = 200, not enough for 800: refuse
          B | rollback

RESULT: OK. Session B had to wait for A's row locks, then read 200 and refused its withdrawal. The accounts hold 200 together.
```

The lowest useful level, and no write skew. `for update` made A's read **lock both rows**. B could
not read them until A was finished, and then it read the truth.

### Step 3: side by side

| | `--for-update` (a lock) | `--level serializable` (an isolation level) |
|---|---|---|
| B's read | waits | returns at once, from B's snapshot |
| Who loses | nobody is cancelled. B waits, then decides with fresh data | B is cancelled with `40001` |
| What the code needs | nothing more | a retry |
| Cost | B, and everyone behind it, queues while A works | wasted work, redone on retry |

### Step 4: see the lock

`./demo --anomaly write-skew --for-update --pause`, then `./inspect`:

```
  1   current  500      943   944
  2   savings  500      943   944
  3523  demo A (read committed)  idle in transaction                 944                     9029               update account set balance = balance - 800 w
  3525  demo B (read committed)  active               944                     3523           8979               select balance from account where owner = 'a
```

`waits_for_pid` says session 3525 is waiting for 3523. `xmax` of both rows is 944, which is A's
`own_xid`: that is how Postgres marks a row as locked by transaction 944. Compare with Task 7 step
2, where `waits_for_pid` was empty.

### Step 5: what a lock cannot do

"A customer may have at most 3 accounts." Two sessions each count 2 and each insert one. `SELECT ...
FOR UPDATE` locks the rows it **returns**. The row the other session is about to insert does not
exist yet, so there is nothing to lock. SERIALIZABLE handles it: it tracks that both sessions read
"alice's accounts", and cancels the second one at commit with `40001`.

### Isolation levels vs locking

They are different tools, and interviews are full of answers that mix them up.

- An **isolation level** is a setting of one transaction. It decides what that transaction may
  **see**. Raising it adds no waiting in Postgres. When two transactions cannot both be right, one
  is **cancelled** and has to retry.
- A **lock** is a claim on specific rows. It makes other transactions **wait**. It works at any
  isolation level, including the default.

| Situation | Reach for |
|---|---|
| A report or export: several queries that must agree | REPEATABLE READ, read only. Nothing to retry, nothing to lock |
| You will change one known row, based on its value (stock, a seat, one balance) | A lock: `SELECT ... FOR UPDATE`, or better one conditional `UPDATE`. See [`../07-locking`](../07-locking/) |
| A rule over several rows that all exist, and conflicts are common | Lock those rows with `FOR UPDATE`, always in the same order |
| A rule over a set of rows, or over rows that may not exist yet | SERIALIZABLE and a retry |

For this project's withdrawal either tool works, because the two account rows exist and can be
locked. The lock makes the second customer wait. SERIALIZABLE makes the second customer's request
run twice.

`./check 8` says 2 of 2 pass. They passed from the start: they pin down "a lock makes B wait" and
"an isolation level cancels B instead".

### How to say it in an interview

> "The isolation level decides what a transaction can see of other transactions. Postgres defaults
> to READ COMMITTED: every statement sees what was committed when that statement started, so two
> queries in one transaction can disagree. That gives you non-repeatable reads and phantoms. READ
> UNCOMMITTED exists, but Postgres runs it as READ COMMITTED, so there are no dirty reads.
> REPEATABLE READ takes one snapshot for the whole transaction, which is what I use for a report.
> It also stops lost updates: if I update a row that changed after my snapshot, I get SQLSTATE
> 40001. It does not stop write skew, where two transactions read the same rows and write different
> ones. For that I need SERIALIZABLE, which cancels one of them, again with 40001. So with either
> of the two higher levels I need a retry, outside the transaction, with a limit. And none of this
> is locking: a higher isolation level does not make anyone wait in Postgres, it cancels the
> loser. If I know which row I am going to change, I would rather lock it with SELECT FOR UPDATE,
> or do it in one UPDATE."

Then be ready for the follow-ups in "Questions to Ask Afterwards" at the end of
[`INTERVIEW_TASKS.md`](INTERVIEW_TASKS.md).

---

## Clean up

```bash
docker compose down
```
