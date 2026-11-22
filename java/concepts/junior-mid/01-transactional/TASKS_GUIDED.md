# Guided Tasks — @Transactional

The same ten tasks as [`INTERVIEW_TASKS.md`](INTERVIEW_TASKS.md), step by step: the command to
run, the output you should see, why it happens, and the change to make.

Commands are for macOS and Linux. On Windows type `.\demo`, `.\inspect` and `.\check`.

The output below is copied from real runs. Proxy class names like `$Proxy128`, and anything timed,
will differ on your machine. The balances, the timelines and the `RESULT` lines should match.

**Before you start**

```bash
cd start
docker compose up -d
```

**The bank.** Every run starts from the same four accounts: Alice 100, Bob 0, Carol 0 and
**frozen** (any deposit or withdrawal fails), Acme Ltd 1000.

**One idea to carry through every task.** `@Transactional` is not a keyword. It is a note on a
method. Something has to read the note and act on it, and that something is a **proxy** that
stands between the caller and your object. So for every bug, ask three questions:

1. Did the call go **through the proxy**?
2. If yes, was a transaction **opened**, or did the method join one?
3. **How did the method end**, as the proxy saw it: returned, or threw, and threw what?

---

## Task 1: See the Money Disappear

No code in this task. You are collecting evidence.

### Run it

```bash
./demo
```

### What you should see

```
Scenario: transfer (Tasks 1 to 3: self-invocation)
Alice sends 50 to Carol. Carol's account is frozen, so the deposit fails
after the money has left Alice's account.

ACCOUNTS BEFORE
  id  owner     balance  frozen
  --  --------  -------  ------
  1   Alice     100
  2   Bob       0
  3   Carol     0        yes
  4   Acme Ltd  1000

CALL
  transferService.transfer(ALICE, CAROL, 50)
  threw AccountFrozenException: Account 3 (Carol) is frozen

ACCOUNTS AFTER
  id  owner     balance  frozen
  --  --------  -------  ------
  1   Alice     50
  2   Bob       0
  3   Carol     0        yes
  4   Acme Ltd  1000

AUDIT LOG
  (no rows)

RESULT: MONEY LOST. Transfer failed. Sender balance: 50. Receiver balance: 0. 50 is missing.
```

The transfer failed with an exception. Alice has 50 less. Carol has nothing. The bank held 1100
and now holds 1050.

### Find it in the code

Open `TransferService`:

```java
public void transfer(long fromId, long toId, long amount) {
    ...
    moveMoney(fromId, toId, amount);
}

@Transactional
public void moveMoney(long fromId, long toId, long amount) {
    Account from = accounts.require(fromId);
    from.withdraw(amount);
    accounts.save(from);

    Account to = accounts.require(toId);
    to.deposit(amount);          // fails here when the receiver is frozen
    accounts.save(to);
}
```

`moveMoney` is `@Transactional`. The demo called `transfer`, which is not, and `transfer` calls
`moveMoney`. It looks fine.

### See the SQL

```bash
./demo --sql
```

```
CALL
  transferService.transfer(ALICE, CAROL, 50)
        sql> select a1_0.id,a1_0.balance,a1_0.frozen,a1_0.owner from accounts a1_0 where a1_0.id=?
        sql> select a1_0.id,a1_0.balance,a1_0.frozen,a1_0.owner from accounts a1_0 where a1_0.id=?
        sql> update accounts set balance=?,frozen=?,owner=? where id=?
        sql> select a1_0.id,a1_0.balance,a1_0.frozen,a1_0.owner from accounts a1_0 where a1_0.id=?
  threw AccountFrozenException: Account 3 (Carol) is frozen
```

One `update`: Alice's debit. Then Carol is loaded, the deposit throws, and nothing puts Alice's
balance back.

### See the transactions

```bash
./inspect
```

The second half of the output:

```
TIMELINE of transferService.transfer(ALICE, CAROL, 50)
  inside TransferService.transfer   transaction: NONE
  inside TransferService.moveMoney   transaction: NONE
  BEGIN     SimpleJpaRepository.findById (read-only)
  COMMIT    SimpleJpaRepository.findById
  BEGIN     SimpleJpaRepository.save
  COMMIT    SimpleJpaRepository.save
  BEGIN     SimpleJpaRepository.findById (read-only)
  COMMIT    SimpleJpaRepository.findById
  threw AccountFrozenException: Account 3 (Carol) is frozen
```

Read it line by line:

- `inside TransferService.moveMoney   transaction: NONE`. The method with `@Transactional` on it
  ran with **no transaction**.
- Three transactions did run, and none of them is yours. Each was opened by a repository method.
  Spring Data's repository methods are transactional themselves: when nobody has opened a
  transaction, each call opens one, does its work and commits.
- The second one is `SimpleJpaRepository.save`: Alice's debit. It was **committed** on the spot.
  When the deposit failed two lines later there was nothing left to roll back.

(Why does `save` run a `select` first? Without a transaction the account you loaded is no longer
tracked by Hibernate, so `save` loads it again before writing it.)

Now look at the first half, BEANS:

```
  bean                  proxy?          class of the object Spring hands out  the class behind it
  --------------------  --------------  ------------------------------------  --------------------
  ...
  transferService       yes, subclass   TransferService$$SpringCGLIB$$0       TransferService
```

`transferService` **is** a proxy. So Spring did see the annotation. Then why was there no
transaction? That is Task 2.

---

## Task 2: Fix the Self-Invocation

### How `@Transactional` works

Spring does not change your class. At startup, for every bean that has `@Transactional` somewhere,
it generates a **subclass** (`TransferService$$SpringCGLIB$$0`) and gives an object of that subclass
to every other bean that asks for a `TransferService`. That object is the **proxy**. It holds a
reference to your real object. In effect the generated class looks like this:

```java
class TransferService$$SpringCGLIB$$0 extends TransferService {

    TransferService target;              // your real object

    @Override
    public void moveMoney(long fromId, long toId, long amount) {
        // moveMoney has @Transactional
        open a transaction (or join the one that is already open)
        try {
            target.moveMoney(fromId, toId, amount);
            commit
        } catch (RuntimeException | Error e) {
            roll back
            throw e;
        }
    }

    @Override
    public void transfer(long fromId, long toId, long amount) {
        // transfer has no @Transactional: nothing to do
        target.transfer(fromId, toId, amount);
    }
}
```

Now follow the demo's call:

```
demo  ->  proxy.transfer()          no annotation on transfer: just pass the call on
            ->  target.transfer()   your code
                  ->  this.moveMoney()    "this" is the target, NOT the proxy
```

Inside your object, `moveMoney(...)` means `this.moveMoney(...)`, and `this` is your object. The
call never leaves the class, so it never passes through the proxy, so nobody reads the annotation.
This is called **self-invocation**.

The rule: **only a call that comes through the proxy can start a transaction**. In practice that
means a call from another bean.

### Before

```bash
./check 2
```

```
Task 2: Fix the self-invocation
  [FAIL] Transfer: when the receiver's account is frozen, the sender keeps the money and nothing is missing
           [Alice's balance after the failed transfer]
           expected: 100L
           but was: 50L
  [FAIL] Transfer: the whole transfer runs in ONE database transaction, and the failure rolls it back
           [database transactions opened by one transfer]
           expected: 1L
           but was: 3L
  [PASS] Transfer: a transfer that succeeds moves the money
  1 of 3 pass
```

The third test passes, and that is the trap. When nothing fails, the missing transaction is
invisible. This bug survives testing and shows up in production.

### The change

In `TransferService`, put `@Transactional` on the method that is called from outside:

```java
@Transactional
public void transfer(long fromId, long toId, long amount) {
    TransactionTrace.record("TransferService.transfer");
    if (fromId == toId) {
        throw new IllegalArgumentException("Cannot transfer to the same account");
    }
    moveMoney(fromId, toId, amount);
}
```

That is the whole fix. Now the proxy opens the transaction when `transfer` is called.
`this.moveMoney()` still skips the proxy, and it no longer matters: the transaction is already
open on this thread, and everything `moveMoney` does happens inside it.

**Other fixes**, for when the outer method must not be transactional:

- Move `moveMoney` to **another bean** and inject it. The call then goes through that bean's proxy.
  (Task 4 uses exactly this split: a job that is not transactional calls a service that is.)
- Use `TransactionTemplate` and open the transaction in code: `template.executeWithoutResult(...)`.
- Inject the bean into itself and call `self.moveMoney()`. It works. Most teams consider it a smell.

### After

```bash
./demo
```

```
ACCOUNTS AFTER
  id  owner     balance  frozen
  --  --------  -------  ------
  1   Alice     100
  2   Bob       0
  3   Carol     0        yes
  4   Acme Ltd  1000

RESULT: OK. Transfer failed and was rolled back. Sender balance: 100. Receiver balance: 0. Nothing is missing.
```

```bash
./check 2
```

```
Task 2: Fix the self-invocation
  [PASS] Transfer: when the receiver's account is frozen, the sender keeps the money and nothing is missing
  [PASS] Transfer: the whole transfer runs in ONE database transaction, and the failure rolls it back
  [PASS] Transfer: a transfer that succeeds moves the money
  3 of 3 pass
```

---

## Task 3: Inspect the Proxy

### Step 1 and 2: who is a proxy

```bash
./inspect
```

```
BEANS
  bean                  proxy?          class of the object Spring hands out  the class behind it
  --------------------  --------------  ------------------------------------  --------------------
  accountService        yes, subclass   AccountService$$SpringCGLIB$$0        AccountService
  atmService            yes, subclass   AtmService$$SpringCGLIB$$0            AtmService
  auditService          yes, subclass   AuditService$$SpringCGLIB$$0          AuditService
  billService           yes, subclass   BillService$$SpringCGLIB$$0           BillService
  visibilityCaller      no              VisibilityCaller                      VisibilityCaller
  visibilityExhibit     yes, subclass   VisibilityExhibit$$SpringCGLIB$$0     VisibilityExhibit
  feeService            yes, subclass   FeeService$$SpringCGLIB$$0            FeeService
  payrollService        yes, subclass   PayrollService$$SpringCGLIB$$0        PayrollService
  salaryPayer           yes, subclass   SalaryPayer$$SpringCGLIB$$0           SalaryPayer
  standingOrderJob      no              StandingOrderJob                      StandingOrderJob
  standingOrderService  yes, subclass   StandingOrderService$$SpringCGLIB$$0  StandingOrderService
  transferService       yes, subclass   TransferService$$SpringCGLIB$$0       TransferService
  ledger                yes, subclass   Ledger$$SpringCGLIB$$0                Ledger
  splitTransferService  yes, subclass   SplitTransferService$$SpringCGLIB$$0  SplitTransferService
  auditRepository       yes, interface  $Proxy130                             SimpleJpaRepository
  accountRepository     yes, interface  $Proxy128                             SimpleJpaRepository
```

- `TransferService$$SpringCGLIB$$0` is a subclass of your `TransferService`, generated at startup.
  CGLIB is the library that generates it.
- `standingOrderJob` and `visibilityCaller` are **not** proxies. Neither class has
  `@Transactional` anywhere. Spring only wraps a bean when there is something to do.
- The repositories are proxies too, of another kind (`yes, interface`): you only wrote an
  interface, and Spring generated the object behind it. The real work is done by Spring Data's
  `SimpleJpaRepository`, which is where the transactions named `SimpleJpaRepository.save` in Task 1
  came from.

### Step 3 and 4: the timeline after the fix

```
TIMELINE of transferService.transfer(ALICE, CAROL, 50)
  BEGIN     TransferService.transfer
      inside TransferService.transfer   transaction: TransferService.transfer
      inside TransferService.moveMoney   transaction: TransferService.transfer
  ROLLBACK  TransferService.transfer
  threw AccountFrozenException: Account 3 (Carol) is frozen
```

One transaction instead of three. It is named after `transfer`, the method whose proxy call opened
it. `moveMoney` runs inside that same transaction: `transaction: TransferService.transfer`. And it
ends in `ROLLBACK`, because a `RuntimeException` came out of `transfer`.

`moveMoney` did not get a transaction because of its own annotation. That annotation is still
never read on this path. It runs in a transaction because one is already open on the thread. The
repository calls join it for the same reason: there is no `SimpleJpaRepository.save` line any more.

### Step 5: where did the UPDATE go

```bash
./inspect --sql
```

```
  BEGIN     TransferService.transfer
      inside TransferService.transfer   transaction: TransferService.transfer
      inside TransferService.moveMoney   transaction: TransferService.transfer
        sql> select a1_0.id,a1_0.balance,a1_0.frozen,a1_0.owner from accounts a1_0 where a1_0.id=?
        sql> select a1_0.id,a1_0.balance,a1_0.frozen,a1_0.owner from accounts a1_0 where a1_0.id=?
  ROLLBACK  TransferService.transfer
```

There is no `update` at all. Inside a transaction, Hibernate does not send your changes one by
one. It keeps them in memory and sends them just before the commit (this is called the flush).
The commit never came, so the `update` was never sent. The rollback had nothing to undo in the
database.

### Step 6: is the annotation on moveMoney needed?

Remove `@Transactional` from `moveMoney` and run `./check 2`: still 3 of 3. On this path the
annotation does nothing. It only matters if another bean calls `moveMoney` directly. Put it back.

### Step 7

```bash
./check 3
```

```
Task 3: Inspect the proxy (passes from the start: it documents how Spring behaves)
  [PASS] Proxy: the TransferService that Spring injects is a generated subclass of your class, not your class
  [PASS] Proxy: a call from another bean opens a transaction, the same annotation reached through this.method() does not
  2 of 2 pass
```

### What to take away

- Other beans hold a proxy, not your object. The proxy opens, commits and rolls back.
- Only a call through the proxy can start a transaction. `this.method()` never does.
- Put `@Transactional` on the method that is called from outside the class.
- A transaction belongs to the thread. Everything called while it is open runs inside it.

---

## Task 4: Fix the Rollback Rules

In Task 2 there was no transaction. In this task there is one, the call goes through the proxy,
and the proxy **commits** when you wanted a rollback. Two ways to get there.

### Part 1: the bill

```bash
./demo --scenario bill
```

```
CALL
  billService.payBill(ALICE, CAROL, 50)
  threw BillRejectedException: Biller Carol cannot receive payments
...
RESULT: MONEY LOST. Bill payment failed. Sender balance: 50. Receiver balance: 0. 50 is missing.
```

```bash
./inspect --scenario bill
```

```
TIMELINE of billService.payBill(ALICE, CAROL, 50)
  BEGIN     BillService.payBill
      inside BillService.payBill   transaction: BillService.payBill
  COMMIT    BillService.payBill
  threw BillRejectedException: Biller Carol cannot receive payments
```

A transaction was opened. The method threw. And the transaction ended in **`COMMIT`**.

Open `BillRejectedException`:

```java
public class BillRejectedException extends Exception {
```

It extends `Exception`, not `RuntimeException`. It is a **checked** exception.

**Spring's default rule: roll back for `RuntimeException` and `Error`. Commit for a checked
exception.** The thinking behind it: a checked exception is part of the method's signature, an
expected outcome the caller is forced to handle, so it is treated like a kind of return value.
Whether you agree or not, that is the rule, and it surprises most people once.

Two tests state the rule. They pass from the start:

```
  [PASS] Rollback rule: a plain @Transactional rolls back for a RuntimeException and for an Error
  [PASS] Rollback rule: a plain @Transactional does NOT roll back for a checked exception: it commits
```

**The change.** In `BillService`:

```java
@Transactional(rollbackFor = BillRejectedException.class)
public void payBill(long payerId, long billerId, long amount) throws BillRejectedException {
```

The other fix is to make `BillRejectedException` extend `RuntimeException`. Both pass the tests.
Many teams only use unchecked exceptions in their services for this reason. Some write
`rollbackFor = Exception.class` everywhere.

### Part 2: the standing order

```bash
./demo --scenario standing-order
```

```
CALL
  standingOrderJob.run(ALICE, CAROL, 50)
  returned false (the payment failed)
...
RESULT: MONEY LOST. Standing order failed. Sender balance: 50. Receiver balance: 0. 50 is missing.
```

The job said "failed", correctly. And Alice lost 50.

```bash
./inspect --scenario standing-order
```

```
TIMELINE of standingOrderJob.run(ALICE, CAROL, 50)
  BEGIN     StandingOrderService.execute
      inside StandingOrderService.execute   transaction: StandingOrderService.execute
  COMMIT    StandingOrderService.execute
  returned false (the payment failed)
```

`COMMIT` again. Open `StandingOrderService.execute`:

```java
@Transactional
public boolean execute(long fromId, long toId, long amount) {
    try {
        ...debit...
        ...credit...          // throws AccountFrozenException
        return true;
    } catch (RuntimeException e) {
        return false;
    }
}
```

The exception is a `RuntimeException` this time. But it is caught **inside** the method. Look at
the proxy code in Task 2: the proxy only knows how your method ended. This one ended with
`return false`. A method that returns has succeeded, as far as the proxy can tell, so it commits
the half that was done: the debit.

**The exception is the signal.** If it does not leave the transactional method, there is no
rollback.

**The change.** The job must still return true or false and never throw. So move the `try/catch`
out of the transaction, into the job.

`StandingOrderService`:

```java
@Transactional
public void execute(long fromId, long toId, long amount) {
    TransactionTrace.record("StandingOrderService.execute");

    Account from = accounts.require(fromId);
    from.withdraw(amount);
    accounts.save(from);

    Account to = accounts.require(toId);
    to.deposit(amount);
    accounts.save(to);
}
```

`StandingOrderJob`:

```java
public boolean run(long fromId, long toId, long amount) {
    try {
        standingOrders.execute(fromId, toId, amount);
        return true;
    } catch (RuntimeException e) {
        // By the time we get here the transaction has already been rolled back.
        return false;
    }
}
```

The order of events is now: exception leaves `execute`, passes through the proxy, the proxy rolls
back and rethrows, the job catches it. The job is not transactional and does not need to be.

There is a second fix that keeps the `try/catch` where it was: in the `catch`, call
`TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()`. It also passes the tests.
It ties your business code to Spring's transaction API, so prefer the first fix when you can.

### After

```bash
./demo --scenario bill
./demo --scenario standing-order
```

```
RESULT: OK. Bill payment failed and was rolled back. Sender balance: 100. Receiver balance: 0. Nothing is missing.
RESULT: OK. Standing order failed and was rolled back. Sender balance: 100. Receiver balance: 0. Nothing is missing.
```

```bash
./check 4
```

```
Task 4: Fix the rollback rules
  [PASS] Rollback rule: BillService.payBill rolls back when BillRejectedException is thrown
  [PASS] Rollback rule: a plain @Transactional rolls back for a RuntimeException and for an Error
  [PASS] Rollback rule: a plain @Transactional does NOT roll back for a checked exception: it commits
  [PASS] Standing order: a run that succeeds returns true and moves the money
  [PASS] Standing order: when it fails, the sender keeps the money and nothing is missing
  [PASS] Standing order: the job reports a failure as false and never throws
  [PASS] Bill: when the bill is rejected with a checked exception, the payer keeps the money
  [PASS] Bill: a bill that is accepted moves the money
  8 of 8 pass
```

Run `./check` too: Task 2 must still be green.

---

## Task 5: Inspect Commit and Rollback

### Step 1 to 3: the fixed timelines

```bash
./inspect --scenario bill
```

```
  BEGIN     BillService.payBill
      inside BillService.payBill   transaction: BillService.payBill
  ROLLBACK  BillService.payBill
  threw BillRejectedException: Biller Carol cannot receive payments
```

Same exception, same code path. The only difference is the last word: `ROLLBACK` where it said
`COMMIT`.

```bash
./demo --scenario bill --sql
```

```
  billService.payBill(ALICE, CAROL, 50)
        sql> select a1_0.id,a1_0.balance,a1_0.frozen,a1_0.owner from accounts a1_0 where a1_0.id=?
        sql> select a1_0.id,a1_0.balance,a1_0.frozen,a1_0.owner from accounts a1_0 where a1_0.id=?
  threw BillRejectedException: Biller Carol cannot receive payments
```

Before the fix there was a third line, `update accounts set balance=? ...`, sent at commit time.
Now there is no `update`. As in Task 3, the change was still in memory when the rollback came.

```bash
./inspect --scenario standing-order
```

```
  BEGIN     StandingOrderService.execute
      inside StandingOrderService.execute   transaction: StandingOrderService.execute
  ROLLBACK  StandingOrderService.execute
  returned false (the payment failed)
```

`ROLLBACK` first, then `returned false`. The job caught the exception **after** the proxy had
rolled back.

### Step 4 to 6: rollback-only

Open the exhibit:

```java
// PayrollService
@Transactional
public int payEveryone(long employerId, List<Long> employeeIds, long salary) {
    int paid = 0;
    for (long employeeId : employeeIds) {
        try {
            salaryPayer.pay(employerId, employeeId, salary);     // another bean, @Transactional
            paid++;
        } catch (AccountFrozenException e) {
            // Skip this employee and carry on with the next one.
        }
    }
    return paid;
}
```

This looks like the fix you just made, turned around: the `try/catch` is in the caller, and the
method that throws is a `@Transactional` method of another bean. So Alice and Bob should be paid
and Carol skipped.

```bash
./demo --scenario payroll
```

```
CALL
  payrollService.payEveryone(ACME, [ALICE, BOB, CAROL], 100)
  threw UnexpectedRollbackException: Transaction silently rolled back because it has been marked as rollback-only

ACCOUNTS AFTER
  id  owner     balance  frozen
  --  --------  -------  ------
  1   Alice     100
  2   Bob       0
  3   Carol     0        yes
  4   Acme Ltd  1000

RESULT: NOBODY PAID. payEveryone caught the exception and carried on, but the commit was refused: UnexpectedRollbackException. Alice: 100. Bob: 0.
```

Nobody was paid, and the exception is one that no line of your code throws.

```bash
./inspect --scenario payroll
```

```
TIMELINE of payrollService.payEveryone(ACME, [ALICE, BOB, CAROL], 100)
  BEGIN     PayrollService.payEveryone
      inside PayrollService.payEveryone   transaction: PayrollService.payEveryone
      inside SalaryPayer.pay   transaction: PayrollService.payEveryone
      inside SalaryPayer.pay   transaction: PayrollService.payEveryone
      inside SalaryPayer.pay   transaction: PayrollService.payEveryone
  ROLLBACK  PayrollService.payEveryone (commit was asked for, but the transaction was marked rollback-only)
  threw UnexpectedRollbackException: Transaction silently rolled back because it has been marked as rollback-only
```

What happened, in order:

1. `payEveryone` opened a transaction.
2. `salaryPayer.pay` was called three times. Each call went through `SalaryPayer`'s proxy. Its
   `@Transactional` has the default propagation, `REQUIRED`: **use the caller's transaction if
   there is one**. There was one. So all three ran `inside ... transaction:
   PayrollService.payEveryone`. There is one `BEGIN` in the whole timeline.
3. Carol's `pay` threw. `SalaryPayer`'s proxy saw a `RuntimeException` and had to roll back. It
   could not: the transaction is not its own. So it did the one thing it can do. It **marked the
   shared transaction "rollback-only"** and passed the exception on.
4. `payEveryone` caught the exception and returned 2.
5. `PayrollService`'s proxy saw a normal return and asked for a commit. The transaction manager
   found the mark, rolled back instead, and threw `UnexpectedRollbackException` so the caller
   would know the commit did not happen.

A transaction is all or nothing. Once one participant has said "roll back", catching the
exception cannot take that back. And notice step 3 again: there is no "inner transaction" with
`REQUIRED`. There is one transaction with several methods in it.

### Step 7: what would you do

It depends on what the business wants.

- **All or nothing** (nobody is paid unless everybody is): do not catch the exception. The code
  already behaves this way, just with a confusing error.
- **Each salary on its own**: each salary must be its own transaction. Either remove
  `@Transactional` from `payEveryone`, so each `pay` call opens and commits its own, or make `pay`
  `REQUIRES_NEW` (Task 6). Then Alice and Bob keep their salary when Carol fails. What you give
  up: if the run crashes half way, half of the staff has been paid, and you need a way to know
  who, so you can resume.

### Check

```bash
./check 5
```

```
Task 5: Inspect commit and rollback (passes from the start: it documents how Spring behaves)
  [PASS] Rollback-only: when every account can be paid, everyone is paid and the one transaction commits
  [PASS] Rollback-only: the outer method catches the inner failure, and its commit still fails with UnexpectedRollbackException. Nobody is paid
  2 of 2 pass
```

### What to take away

- Default rule: rollback for `RuntimeException` and `Error`, commit for a checked exception.
- The proxy only sees how the method ended. Catch the exception inside and it commits.
- Catch outside the transactional method, in a caller that is not part of the transaction.
- `REQUIRED` means join. A failure in a joined method marks the whole transaction rollback-only.
- `UnexpectedRollbackException` means: you asked for a commit on a transaction that someone had
  already condemned.

---

## Task 6: Fix the Propagation

### Before

```bash
./demo --scenario atm
```

```
CALL
  atmService.withdraw(ALICE, 500)
  threw InsufficientFundsException: Account 1 holds 100, cannot take out 500
...
AUDIT LOG
  (no rows)

RESULT: AUDIT ROW LOST. The withdrawal was refused and rolled back, and the audit row was rolled back with it. Audit rows: 0.
```

The rollback is correct: Alice still has 100. But `AtmService.withdraw` wrote an audit row before
it tried to take the money, and that row is gone.

```bash
./inspect --scenario atm
```

```
TIMELINE of atmService.withdraw(ALICE, 500)
  BEGIN     AtmService.withdraw
      inside AtmService.withdraw   transaction: AtmService.withdraw
      inside AuditService.record   transaction: AtmService.withdraw
  ROLLBACK  AtmService.withdraw
  threw InsufficientFundsException: Account 1 holds 100, cannot take out 500
```

`inside AuditService.record   transaction: AtmService.withdraw`. The audit row was written in the
withdrawal's transaction. It is the payroll again: `AuditService.record` is `@Transactional` with
the default propagation, `REQUIRED`, so it joined. One transaction, one rollback, and it took the
audit row with it.

### Propagation

Propagation answers one question: **this method is called while a transaction is already open.
What now?**

| Propagation | A transaction is already open | No transaction is open |
|---|---|---|
| `REQUIRED` (the default) | join it | open one |
| `REQUIRES_NEW` | pause it, open a second one, commit or roll back the second one, resume the first | open one |

There are five more (`SUPPORTS`, `MANDATORY`, `NOT_SUPPORTED`, `NEVER`, `NESTED`). These two are
the ones you will use and the ones you will be asked about.

### The change

In `AuditService`:

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void record(String action, String detail) {
    TransactionTrace.record("AuditService.record");
    auditLog.save(new AuditEntry(action, detail));
}
```

Import `org.springframework.transaction.annotation.Propagation`.

This works because `record` is a method of **another bean**: `AtmService` calls it through
`AuditService`'s proxy, and the proxy reads the annotation. If `record` were a private helper
inside `AtmService`, the same annotation would do nothing. That is Task 2 again.

### After

```bash
./demo --scenario atm
```

```
AUDIT LOG
  id  action                  detail
  --  ----------------------  ---------------------
  1   ATM_WITHDRAWAL_ATTEMPT  account 1, amount 500

RESULT: OK. The withdrawal was refused and rolled back. The audit row survived. Audit rows: 1.
```

```bash
./check 6
```

```
Task 6: Fix the propagation
  [PASS] ATM: the audit row is written in a transaction of its own, which commits while the withdrawal's rolls back
  [PASS] ATM: a refused withdrawal leaves the balance alone, and its audit row survives the rollback
  [PASS] ATM: a withdrawal that succeeds takes the money and writes one audit row
  3 of 3 pass
```

---

## Task 7: Inspect REQUIRED and REQUIRES_NEW

### Step 1 and 2: two transactions

```bash
./inspect --scenario atm --sql
```

```
TIMELINE of atmService.withdraw(ALICE, 500)
  BEGIN     AtmService.withdraw
      inside AtmService.withdraw   transaction: AtmService.withdraw
      BEGIN     AuditService.record
          inside AuditService.record   transaction: AuditService.record
        sql> insert into audit_log (action,created_at,detail) values (?,?,?)
      COMMIT    AuditService.record
        sql> select a1_0.id,a1_0.balance,a1_0.frozen,a1_0.owner from accounts a1_0 where a1_0.id=?
  ROLLBACK  AtmService.withdraw
  threw InsufficientFundsException: Account 1 holds 100, cannot take out 500
```

- Two `BEGIN` lines. The second transaction is opened while the first is still open.
- Inside `AuditService.record` the active transaction is now `AuditService.record`, its own.
- The `insert` is sent and **committed** before the withdrawal even loads the account.
- Then the withdrawal fails and its transaction rolls back. The audit row was committed three
  lines earlier. A commit is final. Nothing that happens later can undo it.

### Step 3: the cost

While `AuditService.record` runs, the withdrawal's transaction is paused. It is still open, and it
still holds its database connection. The audit transaction needs a **second connection**. So one
request holds two connections at that moment.

The connection pool here has 10 (the HikariCP default). If 10 requests are all inside an outer
transaction and all ask for a second connection at once, each waits for a connection that only
the others could give back. Nobody moves until a timeout. `REQUIRES_NEW` on a busy path is a
known way to drain a pool.

A second trap, for later: if the outer transaction has **changed a row** and the inner one tries
to change the same row, the inner one waits for the outer one's lock, and the outer one is paused
waiting for the inner one. The request hangs.

### Step 4 and 5: REQUIRES_NEW in the wrong place

Open the exhibit:

```java
// SplitTransferService
@Transactional
public void transfer(long fromId, long toId, long amount) {
    ledger.debit(fromId, amount);     // Ledger.debit  is @Transactional(propagation = REQUIRES_NEW)
    ledger.credit(toId, amount);      // Ledger.credit is @Transactional(propagation = REQUIRES_NEW)
}
```

No self-invocation: `ledger` is another bean. No checked exception, no `try/catch`. Everything is
`@Transactional`.

```bash
./demo --scenario requires-new-trap
```

```
RESULT: MONEY LOST. Transfer failed. Sender balance: 50. Receiver balance: 0. 50 is missing.
```

The headline bug of Task 1 is back.

```bash
./inspect --scenario requires-new-trap
```

```
TIMELINE of splitTransferService.transfer(ALICE, CAROL, 50)
  BEGIN     SplitTransferService.transfer
      inside SplitTransferService.transfer   transaction: SplitTransferService.transfer
      BEGIN     Ledger.debit
          inside Ledger.debit   transaction: Ledger.debit
      COMMIT    Ledger.debit
      BEGIN     Ledger.credit
          inside Ledger.credit   transaction: Ledger.credit
      ROLLBACK  Ledger.credit
  ROLLBACK  SplitTransferService.transfer
  threw AccountFrozenException: Account 3 (Carol) is frozen
```

Three transactions. The debit **committed**. The credit failed and rolled back, which undid
nothing, because the credit had done nothing yet. The outer transaction rolled back, which undid
nothing either: the outer transaction itself never changed a row. All the work happened in the
inner ones.

This is the answer to the interview question:

> **`REQUIRED` vs `REQUIRES_NEW`: the outer transaction crashes. What happens to the inner one?**
>
> With `REQUIRED` there is no inner transaction. The inner method joined the outer one, so its
> work is rolled back with everything else.
>
> With `REQUIRES_NEW` the inner method ran in a separate transaction. It had already committed
> when the outer one crashed. Its work stays.

For an audit row, "stays" is what you want. For a debit, "stays" is lost money. `REQUIRES_NEW`
means "this does **not** belong to the caller's unit of work". A debit and its credit are one
unit of work.

And the other direction, the inner one fails: with `REQUIRES_NEW` only the inner transaction
rolls back, and if the outer method catches the exception it can still commit. With `REQUIRED`
you get the rollback-only mark of Task 5. Both are pinned down by tests:

```bash
./check 7
```

```
Task 7: Inspect REQUIRED and REQUIRES_NEW (passes from the start: it documents how Spring behaves)
  [PASS] REQUIRES_NEW: the inner debit commits on its own, so when the outer transaction fails later, 50 is missing
  [PASS] REQUIRES_NEW: when the inner transaction fails and the outer one catches the exception, the outer one still commits
  2 of 2 pass
```

### Step 6: back to the payroll

`REQUIRES_NEW` on `SalaryPayer.pay` would give every salary its own transaction. Carol's failure
would roll back only Carol's, the `catch` in `payEveryone` would finally do what its author
hoped, and Alice and Bob would be paid.

The price is the one you just saw. If `payEveryone` fails **after** the loop, Alice and Bob keep
their salaries: those transactions are committed. That may be exactly right for a payroll. Decide
it on purpose.

### What to take away

- `REQUIRED`: one transaction, shared. Everything succeeds or fails together.
- `REQUIRES_NEW`: a separate transaction that commits or rolls back on its own.
- Use `REQUIRES_NEW` for work that must survive the caller's failure: audit rows, error records.
- Never use it to split one business operation.
- It costs a second connection while the caller waits.

---

## Task 8: Fix the Annotations That Do Nothing

### Part 1: the fee

```bash
./demo --scenario fee
```

```
CALL
  feeService.chargeMonthlyFee(ALICE)
  threw NullPointerException: Cannot invoke "com.amigoscode.transactional.account.AccountRepository.require(long)" because "this.accounts" is null
...
RESULT: FEE NOT CHARGED. chargeMonthlyFee threw NullPointerException. Alice: 100.
```

`this.accounts` is null. Open `FeeService`:

```java
private final AccountRepository accounts;

public FeeService(AccountRepository accounts) {
    this.accounts = accounts;
}

@Transactional
public final void chargeMonthlyFee(long accountId) {
    Account account = accounts.require(accountId);
```

The field is `final` and set in the only constructor. It cannot be null. And yet.

```bash
./inspect --scenario fee
```

```
TIMELINE of feeService.chargeMonthlyFee(ALICE)
  inside FeeService.chargeMonthlyFee   transaction: NONE
  threw NullPointerException: Cannot invoke "com.amigoscode.transactional.account.AccountRepository.require(long)" because "this.accounts" is null
```

`transaction: NONE` as well. The call came from another bean, so it did go to the proxy.

**Why.** The proxy is a subclass of `FeeService`. To add the transaction it has to **override**
`chargeMonthlyFee`. The method is `final`, and Java does not let a subclass override a final
method. So the proxy has no version of its own, and the call runs the inherited code, your code,
**on the proxy object itself**. Not on your real object.

The proxy object is a shell. Spring creates it without calling your constructor, because it only
exists to pass calls on to the real object. Its `accounts` field was never set. Hence `null`.

Spring warned you at startup. The tools hide startup logs, so run the application once:

```bash
./mvnw -q spring-boot:run
```

```
WARN ... o.s.aop.framework.CglibAopProxy : Public final method [public final void com.amigoscode.transactional.fee.FeeService.chargeMonthlyFee(long)] cannot get proxied via CGLIB, consider removing the final marker or using interface-based JDK proxies.
```

(There is a second warning like it for `VisibilityExhibit.finalMethod`. That one is an exhibit for
Task 9 and stays.)

**The change.** Remove `final`:

```java
@Transactional
public void chargeMonthlyFee(long accountId) {
```

A `final` **class** is the same problem, except that Spring fails at startup because it cannot
create the subclass at all. This is why Kotlin projects, where classes are final by default, use
the `kotlin-spring` compiler plugin.

### Part 2: the rename

```bash
./demo --scenario rename
```

```
CALL
  accountService.rename(ALICE, "Alice Smith")
  returned normally
...
RESULT: CHANGE LOST. rename returned normally, but the owner is still "Alice".
```

No exception. No log line. No change.

```bash
./inspect --scenario rename
```

```
TIMELINE of accountService.rename(ALICE, "Alice Smith")
  BEGIN     AccountService.rename (read-only)
      inside AccountService.rename   transaction: AccountService.rename (read-only)
  COMMIT    AccountService.rename
  returned normally
```

There is a transaction and it committed. Read the brackets: `(read-only)`. Open `AccountService`:

```java
@Service
@Transactional(readOnly = true)
public class AccountService {

    public long balanceOf(long accountId) { ... }

    public void rename(long accountId, String newOwner) {
        Account account = accounts.require(accountId);
        account.rename(newOwner);
        accounts.save(account);
    }
}
```

`@Transactional` on a class applies to every public method of it. The class is read-only, which
is right for `balanceOf`. Then someone added `rename` and did not notice.

```bash
./demo --scenario rename --sql
```

```
  accountService.rename(ALICE, "Alice Smith")
        sql> select a1_0.id,a1_0.balance,a1_0.frozen,a1_0.owner from accounts a1_0 where a1_0.id=?
  returned normally
```

One `select`. No `update`, even though the code calls `save`.

**What `readOnly = true` does.** It tells Hibernate: nothing will be changed in this transaction,
so do not keep track of changes and do not send any. That saves memory and time on reads. The
other side of it: when you do change an entity, Hibernate never looks, so it never writes. It
does not throw. It does not warn.

**The change.** An annotation on a method overrides the one on the class:

```java
@Transactional
public void rename(long accountId, String newOwner) {
```

### After

```bash
./demo --scenario fee
./demo --scenario rename
```

```
RESULT: OK. The fee was charged. Alice: 95.
RESULT: OK. The owner is now "Alice Smith".
```

```bash
./check 8
./check
```

```
Task 8: Fix the annotations that do nothing
  [PASS] Fee: chargeMonthlyFee runs in one database transaction, which commits
  [PASS] Fee: charging the monthly fee takes 5 from the account
  [PASS] Rename: balanceOf still runs in a read-only transaction
  [PASS] Rename: the new owner name is written to the database
  4 of 4 pass
```

`./check` should now end with `All tasks: 32 of 32 pass`.

---

## Task 9: Inspect What a Proxy Cannot Do

### Step 1 to 3: which methods can be transactional

```bash
./demo --scenario visibility
```

```
  method           how the call arrives             transaction active  injected fields
  ---------------  -------------------------------  ------------------  ---------------
  public           through the proxy                yes                 set
  protected        through the proxy                yes                 set
  package-private  through the proxy                yes                 set
  public final     runs on the proxy object itself  NO                  NULL
  private          this.privateMethod()             NO                  set

RESULT: 3 of 5 methods ran in a transaction. The other 2 carry @Transactional too, and it did nothing.
```

`VisibilityExhibit` has five methods with the same body and the same `@Transactional`. Another
bean in the same package, `VisibilityCaller`, calls each of them.

Everything follows from one fact: **the proxy is a subclass**.

- **public, protected, package-private**: a subclass can override all three. The proxy does, and
  the transaction is there.
- **final**: cannot be overridden. The code runs on the proxy object, with no transaction and with
  `null` fields. This is the `NullPointerException` of Task 8.
- **private**: cannot be overridden, and cannot be called from another class at all. The only
  caller is the class itself, through `this`. This is the self-invocation of Task 2.

> **"`@Transactional` only works on public methods."** You will read this in many articles and
> hear it in interviews. It was true up to Spring 5. Since Spring 6, protected and package-private
> methods are transactional too when the proxy is a subclass, which is the default in Spring Boot.
> The table above was produced on Spring Framework 7. What still does not work: `private`, `final`
> and `static`. (With an interface-based proxy only the interface's methods can be intercepted,
> and those are public.)
>
> In an interview, say what is true today and that it used to be different. Then explain it from
> "the proxy is a subclass". That is worth more than the list.

Public is still the sensible choice: the method has to be called from another bean anyway.

### Step 4: the fee, fixed

```bash
./inspect --scenario fee
```

```
TIMELINE of feeService.chargeMonthlyFee(ALICE)
  BEGIN     FeeService.chargeMonthlyFee
      inside FeeService.chargeMonthlyFee   transaction: FeeService.chargeMonthlyFee
  COMMIT    FeeService.chargeMonthlyFee
  returned normally
```

### Step 5: the rename, fixed

```bash
./inspect --scenario rename
```

```
TIMELINE of accountService.rename(ALICE, "Alice Smith")
  BEGIN     AccountService.rename
      inside AccountService.rename   transaction: AccountService.rename
  COMMIT    AccountService.rename
  returned normally
```

`(read-only)` is gone. And `./demo --scenario rename --sql` now shows the `update`:

```
        sql> select a1_0.id,a1_0.balance,a1_0.frozen,a1_0.owner from accounts a1_0 where a1_0.id=?
        sql> update accounts set balance=?,frozen=?,owner=? where id=?
```

### Step 6: what readOnly does and does not do

```bash
./check 9
```

```
Task 9: Inspect what a proxy cannot do (passes from the start: it documents how Spring behaves)
  [PASS] readOnly: a changed entity is silently not written. No exception, no UPDATE
  [PASS] Visibility: public, protected and package-private @Transactional methods all get a transaction
  [PASS] readOnly: an UPDATE statement sent inside a read-only transaction is refused by Postgres
  [PASS] Visibility: a final method gets no transaction and sees null fields, a private method gets no transaction
  4 of 4 pass
```

Two different things happen in a read-only transaction, and the tests in `ProxyLimitsIT` show
both:

- You **change an entity**: nothing is written, nothing is thrown. Hibernate was told not to look.
- You send an **`UPDATE` statement yourself** (plain SQL, or a `@Modifying` query): Spring also set
  the database connection to read-only, and Postgres refuses the statement with
  `cannot execute UPDATE in a read-only transaction`.

So the loud failure exists only for statements you write. The quiet one is the one that bites.

What `readOnly = true` is **not**: it is not a security feature, and it is not required for
reading. It is an optimisation and a statement of intent. Use it on methods that only read.

### What to take away

- The proxy is a subclass. It can only intercept what a subclass can override.
- `final` and `private` methods are never transactional through the proxy. `final` also gives you
  `null` fields.
- "Only public" is out of date since Spring 6.
- `readOnly = true` on a method that changes entities loses the change without an error.

---

## Task 10: Name the Pitfall

Use the three questions from the top of this file: through the proxy? transaction opened or
joined? how did the method end?

| # | Case | What happens | Scenario |
|---|---|---|---|
| 1 | Public method without `@Transactional` calls a `@Transactional` method of the same class; the second half throws | No transaction. Each repository call commits on its own. The first half stays | `transfer` |
| 2 | `@Transactional` method throws `IOException` | Checked exception: **commit**. The first half stays | `bill` |
| 3 | `@Transactional` method catches everything and returns `false` | The method returned: **commit**. The first half stays | `standing-order` |
| 4 | Outer calls inner (`REQUIRED`) on another bean; inner throws; outer catches | Inner marks the shared transaction rollback-only. Outer's commit fails with `UnexpectedRollbackException`. Nothing is saved | `payroll` |
| 5 | Same, inner is `REQUIRES_NEW` | Only the inner transaction rolls back. The outer one commits its own work | `./check 7`, second test |
| 6 | Inner `REQUIRES_NEW` succeeds, then outer throws | Inner is already committed and stays. Outer rolls back | `requires-new-trap`, and `atm` |
| 7 | `@Transactional public final` method | Not overridden by the proxy: no transaction, and the fields are `null` | `fee` |
| 8 | Class is `readOnly = true`, method changes an entity | Commit, and the change is never written. No error | `rename` |
| 9 | Slow HTTP call inside the transaction, nothing fails | No data bug. The transaction holds a database connection, and any row locks it has taken, for 30 seconds. Under load the connection pool runs out and unrelated requests wait. And if the transaction fails afterwards, the remote call cannot be rolled back |  not in this project |

For case 9 the fix is to keep the remote call out of the transaction: call the provider first,
then open a short transaction to store the result. The Locking project shows what a slow call
does while a row lock is held.

### All the scenarios, start against solution

| Scenario | In `start/` | In `solution/` |
|---|---|---|
| `transfer` | MONEY LOST, 50 missing | OK, rolled back |
| `bill` | MONEY LOST, 50 missing | OK, rolled back |
| `standing-order` | MONEY LOST, 50 missing | OK, rolled back |
| `payroll` | NOBODY PAID (exhibit) | the same |
| `atm` | AUDIT ROW LOST | OK, audit row survived |
| `requires-new-trap` | MONEY LOST, 50 missing (exhibit) | the same |
| `fee` | FEE NOT CHARGED, `NullPointerException` | OK, Alice 95 |
| `rename` | CHANGE LOST | OK, "Alice Smith" |
| `visibility` | 3 of 5 (exhibit) | the same |

### How to say it in an interview

**"What is a transaction?"**

> "A group of database changes that succeed or fail as one unit. The usual example is a bank
> transfer: the debit and the credit must both happen or neither. The properties are ACID. Atomic:
> all or nothing. Consistent: the data goes from one valid state to another. Isolated: other
> transactions do not see my half-finished work. Durable: once it is committed it survives a
> crash."

**"How does `@Transactional` work internally?"**

> "Spring wraps the bean in a proxy. By default that is a generated subclass of my class, and it
> is the proxy that gets injected into other beans. When another bean calls a `@Transactional`
> method, the call lands on the proxy first. The proxy opens a transaction, or joins the one that
> is already open on the thread, then calls my real method. If the method returns, it commits. If
> it throws a `RuntimeException` or an `Error`, it rolls back.
>
> Most of the pitfalls follow from that. A call to a method on `this` does not go through the
> proxy, so the annotation is ignored: that is self-invocation. A checked exception commits by
> default, so I use `rollbackFor`. If I catch the exception inside the method, the proxy sees a
> normal return and commits. And because the proxy is a subclass, it cannot intercept private or
> final methods."

**"`REQUIRED` vs `REQUIRES_NEW`: the outer transaction crashes. What happens to the inner one?"**

> "With `REQUIRED`, the default, there is no inner transaction. The inner method joined the outer
> one, so it is rolled back with it. With `REQUIRES_NEW` the outer transaction is paused and the
> inner method runs in a separate one, which has already committed by the time the outer one
> crashes, so its changes stay. I use `REQUIRES_NEW` for things like an audit row that must
> survive the failure. I would not use it for part of the same business operation, and I keep in
> mind that it needs a second connection while the outer one waits."

Then be ready for the follow-ups in "Questions to Ask Afterwards" at the end of
[`INTERVIEW_TASKS.md`](INTERVIEW_TASKS.md).

---

## Clean up

```bash
docker compose down
```
