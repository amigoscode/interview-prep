# Interview Tasks — @Transactional

> **In `start/`, `./check` FAILS on purpose: 22 of 32 checks pass.** Tasks 2, 4, 6 and 8 turn the
> other 10 green. Tasks 1, 3, 5, 7, 9 and 10 are inspection tasks: you run things, look, and answer
> questions.

Do the tasks in order. Every fix is followed by an inspection task: a green test tells you the fix
works, the inspection tells you **why**, and the why is what an interviewer asks for.

Stuck, or want the explanation? The same task number in [`TASKS_GUIDED.md`](TASKS_GUIDED.md) walks
you through it with the expected output.

Commands are for macOS and Linux. On Windows type `.\demo`, `.\inspect` and `.\check`.

**Rules:** work in `start/`. Never edit, delete or disable a test. Do not change the packages
`payroll`, `trap`, `exhibit`, `trace` and `demo`. Leave the `TransactionTrace.record(...)` lines
where they are.

**The bank.** Every demo run and every test starts from the same four accounts:

| id | owner | balance | |
|---|---|---|---|
| 1 | Alice | 100 | |
| 2 | Bob | 0 | |
| 3 | Carol | 0 | frozen: any deposit or withdrawal fails |
| 4 | Acme Ltd | 1000 | |

---

## Task 1: See the Money Disappear (10 min)

**Story:** *A customer complains: "My transfer failed with an error, and the money is gone from my
account." The developer says that cannot happen, the transfer is `@Transactional`. Before you
change anything, reproduce it and find out what the database did.*

**Steps:**
1. `docker compose up -d`, then `./demo`. Did the transfer fail? What are the two balances? How
   much money is missing from the bank?
2. Open `TransferService`. Which method carries `@Transactional`? Which method did the demo call?
3. `./demo --sql`. Which statement changed Alice's balance? Is there a statement that puts it back?
4. `./inspect`. Read the TIMELINE. Was a transaction active inside `moveMoney`? How many database
   transactions were opened, and which methods opened them?
5. In the BEANS table, find `transferService`. Is it a proxy?

**Acceptance Criteria** (no tests: you answer)
- [ ] You can say how much money is missing and which SQL statement made it permanent
- [ ] You can say whether a transaction was active inside `moveMoney`
- [ ] You can say who opened the three transactions that did run
- [ ] You have a guess at why the annotation on `moveMoney` did nothing

**Hints:**
- The bean is a proxy and the method is annotated. Look at **who calls** `moveMoney`
- With no transaction around them, each repository call is a small transaction of its own

---

## Task 2: Fix the Self-Invocation (10 min)

**Story:** *A failed transfer must change nothing. Fix `TransferService`.*

**Acceptance Criteria** (`./check 2`)
- [ ] When the receiver's account is frozen, the sender keeps the money and nothing is missing
      — `SelfInvocationIT.failedTransferChangesNoBalance`
- [ ] The whole transfer runs in ONE database transaction, and the failure rolls it back
      — `SelfInvocationIT.failedTransferIsOneTransactionThatRollsBack`
- [ ] A transfer that succeeds moves the money
      — `SelfInvocationIT.successfulTransferMovesTheMoney` (already passing: keep it that way)
- [ ] `./demo` prints `RESULT: OK`

**Hints:**
- `moveMoney(...)` inside `transfer` is `this.moveMoney(...)`. What is `this`: the proxy, or your
  object?
- The transaction has to start at the method that is called **from outside the class**
- There is more than one fix. The smallest one is one line

---

## Task 3: Inspect the Proxy (15 min)

**Story:** *"How does `@Transactional` work internally?" is the most common question on this
topic. Your fix passes the tests. Now collect what you need to answer it.*

**Steps:**
1. `./inspect`. In BEANS, what is the class of the object Spring hands out for `transferService`?
   What is its relationship to your `TransferService` class?
2. Find a bean that is **not** a proxy. What is different about its class?
3. Read the TIMELINE. Compare it with Task 1 step 4. How many transactions now? Which method is
   the transaction named after?
4. `moveMoney` still carries `@Transactional`, and it is still called as `this.moveMoney(...)`.
   Why does it run inside a transaction now?
5. `./inspect --sql`. In Task 1 you saw an `update`. Where is it now? Why?
6. Remove `@Transactional` from `moveMoney`, run `./check 2`, then put it back. What changed?
7. `./check 3` and read the two test names. Open `ProxyIT` and see how a test can tell a proxy from
   your class.

**Acceptance Criteria** (`./check 3`, already passing: these tests document how Spring behaves)
- [ ] You can explain, without the word "magic", what Spring does at startup to a bean with
      `@Transactional` and what happens on each call
- [ ] You can explain why `this.moveMoney()` never starts a transaction
- [ ] You can name three ways to fix self-invocation
- [ ] `ProxyIT.theInjectedBeanIsAProxy` and `ProxyIT.onlyCallsThroughTheProxyOpenATransaction` pass

---

## Task 4: Fix the Rollback Rules (20 min)

**Story:** *Two more "the money is gone" tickets. In both, the call goes through the proxy and a
transaction really is opened. In both, it is **committed**. (1) A bill payment that is rejected.
(2) The nightly standing-order job, which must never crash. Fix `BillService`, then
`StandingOrderService` and `StandingOrderJob`.*

Run `./demo --scenario bill` and `./demo --scenario standing-order` first.

**Acceptance Criteria** (`./check 4`)
- [ ] When the bill is rejected with a checked exception, the payer keeps the money
      — `BillPaymentIT.rejectedBillChangesNoBalance`
- [ ] `BillService.payBill` rolls back when `BillRejectedException` is thrown
      — `RollbackRulesTest.payBillRollsBackForBillRejectedException`
- [ ] When a standing order fails, the sender keeps the money and nothing is missing
      — `StandingOrderIT.failedStandingOrderChangesNoBalance`
- [ ] The job reports a failure as `false` and never throws
      — `StandingOrderIT.theJobReportsFailureWithoutThrowing` (already passing: keep it that way)
- [ ] The four other tests of Task 4 still pass (accepted bill, successful standing order, and two
      that document Spring's default rule)
- [ ] `./demo --scenario bill` and `./demo --scenario standing-order` print `RESULT: OK`

**Hints:**
- Open `BillRejectedException`. What does it extend? Read the two passing tests in
  `RollbackRulesTest`: they state Spring's default rule
- `@Transactional` has an attribute for this
- For the standing order: the proxy decides between commit and rollback by looking at **how the
  method ended**. How does `execute` end when the payment fails?
- The job's contract (true or false, never an exception) must stay. Where can the `try/catch` live
  so that the proxy still sees the exception? You may change the signature of
  `StandingOrderService.execute`

---

## Task 5: Inspect Commit and Rollback (15 min)

**Story:** *Same again: the tests are green, now show what Spring does. Then meet the error
message that confuses everyone the first time.*

**Steps:**
1. `./inspect --scenario bill`. How does the transaction end? Undo your fix for a moment (or run
   it in a fresh copy of `start/`) and compare the last line of the timeline.
2. `./demo --scenario bill --sql`. Before your fix there was an `update`. Is there one now? Was
   anything "undone" in the database?
3. `./inspect --scenario standing-order`. The job still returns `false`. Where is the exception
   caught now, relative to the `ROLLBACK` line?
4. Now the exhibit. Open `PayrollService` and `SalaryPayer`. Predict: Acme pays Alice, Bob and
   Carol; Carol is frozen; the exception is caught. Who gets paid?
5. `./demo --scenario payroll`. Who got paid? Which exception came out, and from where? It is not
   thrown by any line in `payEveryone`.
6. `./inspect --scenario payroll`. How many transactions were opened for three salaries? Read the
   `ROLLBACK` line.
7. How would you change the code so Alice and Bob are paid when Carol fails? Name two ways. What
   does each one give up?

**Acceptance Criteria** (`./check 5`, already passing: these tests document how Spring behaves)
- [ ] You can state Spring's default rollback rule in one sentence
- [ ] You can explain why catching an exception inside a `@Transactional` method causes a commit
- [ ] You can explain what "marked as rollback-only" means and who marked the transaction
- [ ] You can explain why catching the exception in `payEveryone` did not help
- [ ] `PayrollIT.catchingTheInnerExceptionDoesNotSaveTheTransaction` passes

---

## Task 6: Fix the Propagation (10 min)

**Story:** *Security wants every cash machine attempt in the audit log, **especially** the refused
ones. `AtmService.withdraw` writes the audit row first, then takes the money. When the withdrawal
is refused, the audit row is gone too. Fix `AuditService`.*

Run `./demo --scenario atm` first.

**Acceptance Criteria** (`./check 6`)
- [ ] A refused withdrawal leaves the balance alone, and its audit row survives the rollback
      — `AtmIT.auditRowSurvivesARefusedWithdrawal`
- [ ] The audit row is written in a transaction of its own, which commits while the withdrawal's
      rolls back — `AtmIT.auditRowIsWrittenInItsOwnTransaction`
- [ ] A withdrawal that succeeds takes the money and writes one audit row
      — `AtmIT.successfulWithdrawalTakesTheMoneyAndIsAudited` (already passing: keep it that way)
- [ ] `./demo --scenario atm` prints `RESULT: OK`

**Hints:**
- `AuditService.record` is already `@Transactional`. With the default propagation, what does it do
  when the caller already has a transaction?
- `@Transactional(propagation = ...)`. Which value means "always open a new one"?
- Would the same annotation work if `record` were a method of `AtmService`? Remember Task 2

---

## Task 7: Inspect REQUIRED and REQUIRES_NEW (15 min)

**Story:** *"`REQUIRED` vs `REQUIRES_NEW`: the outer transaction crashes. What happens to the inner
one?" Collect the evidence for both answers.*

**Steps:**
1. `./inspect --scenario atm`. How many transactions? In which order do they end, and how? Inside
   `AuditService.record`, which transaction is active? Compare with what you saw before the fix.
2. `./inspect --scenario atm --sql`. When is the audit `insert` sent, relative to the `COMMIT` of
   the audit transaction and the `ROLLBACK` of the withdrawal?
3. While `AuditService.record` runs, what is the withdrawal's transaction doing? How many database
   connections does this one request hold at that moment?
4. Now the exhibit. Open `SplitTransferService` and `Ledger`. Every method is `@Transactional`
   and every call goes through a proxy. Predict the balances after Alice sends 50 to frozen Carol.
5. `./demo --scenario requires-new-trap`, then `./inspect --scenario requires-new-trap`. Which
   transaction committed? Which two rolled back? Where is the 50?
6. Go back to the payroll in Task 5. Would `REQUIRES_NEW` on `SalaryPayer.pay` make Alice and Bob
   get paid? What would then happen if `payEveryone` failed after the loop?

**Acceptance Criteria** (`./check 7`, already passing: these tests document how Spring behaves)
- [ ] You can answer the question in the story for `REQUIRED` and for `REQUIRES_NEW`
- [ ] You can say when `REQUIRES_NEW` is the right choice and give an example
- [ ] You can explain why `REQUIRES_NEW` on the debit lost money
- [ ] You can name one cost of `REQUIRES_NEW` that has nothing to do with correctness
- [ ] `RequiresNewTrapIT.innerCommitSurvivesTheOuterRollback` and
      `RequiresNewTrapIT.outerCommitSurvivesTheInnerRollback` pass

---

## Task 8: Fix the Annotations That Do Nothing (15 min)

**Story:** *Two last tickets. (1) Charging the monthly fee crashes with a `NullPointerException` on
a field that the constructor clearly sets. (2) Renaming an account returns normally, logs nothing,
and the name never changes. Fix `FeeService` and `AccountService`.*

Run `./demo --scenario fee` and `./demo --scenario rename` first.

**Acceptance Criteria** (`./check 8`)
- [ ] Charging the monthly fee takes 5 from the account — `FeeIT.monthlyFeeIsCharged`
- [ ] `chargeMonthlyFee` runs in one database transaction, which commits
      — `FeeIT.monthlyFeeRunsInOneTransaction`
- [ ] The new owner name is written to the database — `RenameIT.renameIsWritten`
- [ ] `balanceOf` still runs in a read-only transaction
      — `RenameIT.lookupsStayReadOnly` (already passing: keep it that way)
- [ ] `./demo --scenario fee` and `./demo --scenario rename` print `RESULT: OK`

**Hints:**
- Fee: the proxy is a **subclass** of `FeeService`. What can a subclass not do to a `final` method?
  On which object does the method then run, and was that object's constructor ever called?
- Fee: run `./mvnw -q spring-boot:run` and read the `WARN` lines. Spring told you at startup
- Rename: look at the annotation on the **class**. Which methods does it apply to?
- Rename: an annotation on a method overrides the one on the class

---

## Task 9: Inspect What a Proxy Cannot Do (15 min)

**Story:** *"`@Transactional` only works on public methods." You will hear that in interviews. Find
out what is true on the version of Spring you are running.*

**Steps:**
1. `./demo --scenario visibility`. Five methods, same body, same annotation. Which ran in a
   transaction?
2. Open `VisibilityExhibit` and `VisibilityCaller`. For the two methods that got no transaction,
   explain why in terms of what a subclass can and cannot override.
3. For the `final` method the table says `injected fields: NULL`. Which object did that method run
   on? Connect it to the `NullPointerException` of Task 8.
4. `./inspect --scenario fee`. What changed compared with before your fix?
5. `./inspect --scenario rename`. Before your fix the timeline said `(read-only)`. Run
   `./demo --scenario rename --sql`: is there an `update` now? Was there one before?
6. `./check 9` and read the two `readOnly` test names. What happens to a changed entity in a
   read-only transaction? What happens to a hand-written `UPDATE`? Why are the two different?

**Acceptance Criteria** (`./check 9`, already passing: these tests document how Spring behaves)
- [ ] You can say which of public, protected, package-private, private and final methods can be
      transactional on Spring Framework 7, and which answer is out of date
- [ ] You can explain why a `final` method sees `null` fields
- [ ] You can say what `readOnly = true` does, and what it does not do
- [ ] The four tests of `ProxyLimitsIT` pass

---

## Task 10: Name the Pitfall (15 min)

**Story:** *In an interview you get a snippet and one question: "does this roll back?" Practise the
method: is there a transaction, does the call go through the proxy, and how does the method end?*

**Steps:** for each case, say what is in the database afterwards and why. Then run the scenario
that proves it.

1. A public method without `@Transactional` calls a `@Transactional` method of the same class. The
   second half of the work throws a `RuntimeException`.
2. A `@Transactional` method throws `IOException` half way.
3. A `@Transactional` method catches every exception, logs it and returns `false`.
4. A `@Transactional` method calls a `@Transactional` method of **another** bean, which throws a
   `RuntimeException`. The outer method catches it and returns normally.
5. Same as 4, but the inner method is `REQUIRES_NEW`.
6. An outer `@Transactional` method calls an inner `REQUIRES_NEW` method, which succeeds. Then the
   outer method throws a `RuntimeException`.
7. A `@Transactional public final` method.
8. A class with `@Transactional(readOnly = true)` and a method that changes an entity.
9. A `@Transactional` method calls a payment provider over HTTP between two database writes. The
   provider takes 30 seconds to answer. Nothing fails. What is the problem?

**Acceptance Criteria** (no tests: you answer)
- [ ] You can answer all nine without running anything
- [ ] For each of 1 to 8 you can name the scenario of this project that shows it
- [ ] You can give the model answer to "how does `@Transactional` work?" in under a minute

---

## Questions to Ask Afterwards

Answer out loud, in full sentences, before reading the answer in brackets.

- What is a transaction? (A group of database changes that is treated as one unit. ACID in plain
  words. Atomic: all of the changes happen or none of them do. Consistent: the database goes from
  one valid state to another, and constraints are never left broken. Isolated: other transactions
  do not see my half-finished work. Durable: once the commit has returned, the changes survive a
  crash. A transfer is the standard example: the debit and the credit must both happen or neither.)
- How does `@Transactional` work internally? (At startup Spring wraps the bean in a proxy, by
  default a generated subclass of my class, and injects the proxy everywhere. When another bean
  calls a `@Transactional` method, the call lands on the proxy. The proxy asks the transaction
  manager for a transaction, calls my real method, and then commits if the method returned or
  rolls back if it threw a `RuntimeException` or an `Error`. The transaction is bound to the
  current thread, which is how the repositories find it.)
- A `@Transactional` method is called from another method of the same class. Does it run in a
  transaction? (Only if the calling method already has one. The call is `this.method()`, and
  `this` is my object, not the proxy, so the annotation on the called method is never looked at.
  Fixes: put `@Transactional` on the method that is called from outside, move the method to
  another bean, or use `TransactionTemplate`.)
- Which exceptions cause a rollback? (By default `RuntimeException` and `Error`. A checked
  exception commits. Change it with `rollbackFor`, or throw an unchecked exception.)
- What happens if I catch the exception inside the `@Transactional` method? (The method returns
  normally, so the proxy commits whatever was done before the failure. To keep the rollback, let
  the exception leave the method and catch it in the caller.)
- What is `UnexpectedRollbackException`? (An inner `@Transactional` method joined my transaction
  and threw. Its proxy could not roll back, because the transaction is not its own, so it marked
  the shared transaction "rollback-only". I caught the exception and returned normally, my proxy
  tried to commit, found the mark, rolled back instead and threw this to tell me the commit did
  not happen.)
- `REQUIRED` vs `REQUIRES_NEW`: the outer transaction crashes. What happens to the inner one?
  (With `REQUIRED` there is no inner transaction: the inner method joined the outer one, so its
  work is rolled back with it. With `REQUIRES_NEW` the inner method ran in a separate transaction
  that had already committed when the outer one failed, so its work stays.)
- And the other way round: the inner one fails? (With `REQUIRES_NEW` only the inner transaction
  rolls back; the outer one can catch the exception and still commit. With `REQUIRED` the shared
  transaction is marked rollback-only, and the outer commit fails even if I catch the exception.)
- When would you use `REQUIRES_NEW`? (For work that must survive a failure of the caller: an
  audit row, a failed-login counter, an outbox or error record. Not for parts of one business
  operation that must succeed or fail together. It also needs a second database connection while
  the outer transaction waits, so under load it can drain the connection pool.)
- Does `@Transactional` work on private methods? On final methods? (No to both. The proxy is a
  subclass, and a subclass can override neither. A `final` method is worse than "no transaction":
  it runs on the proxy object itself, whose fields were never set, so it fails with a
  `NullPointerException`. Protected and package-private methods do work since Spring 6.)
- What does `readOnly = true` do? (It is a hint. Hibernate stops tracking changes and never
  flushes, so loading is cheaper and changes to entities are silently not written. The connection
  is set read-only too, so on Postgres a direct `UPDATE` statement is refused. It is not a
  security feature and it does not make a read "consistent".)
- Why is a slow HTTP call inside a transaction a problem? (The transaction holds a database
  connection, and any row locks it took, for as long as the call lasts. Thirty slow calls and the
  connection pool is empty, so every other request waits. And the remote call cannot be rolled
  back if the transaction later fails. Do the remote call before or after the transaction.)
- Does `@Transactional` stop two requests from updating the same row at the same time? (No. A
  transaction makes my own statements succeed or fail together. Two transactions can still read
  the same value and overwrite each other. That needs locking: see the Locking project.)
- Why are the integration tests in this project not `@Transactional`? (A transactional test wraps
  every call in the test's own transaction and rolls it back at the end. The self-invocation bug
  would vanish, because `moveMoney` would join the test's transaction. Every bug in this project
  would be hidden.)

## Tips for Interviewers

- The most common weak answer is "Spring manages the transaction for you". Ask what object the
  controller holds, and what `this` is inside the service. A candidate who says "proxy" and "the
  call has to come from outside" understands it.
- Ask the candidate to predict the output of `./demo --scenario payroll` before running it. Most
  expect Alice and Bob to be paid. The follow-up, "which line throws
  `UnexpectedRollbackException`?", has the answer "none of mine: the proxy, after my method
  returned".
- For `REQUIRED` vs `REQUIRES_NEW`, ask both directions: the outer fails, then the inner fails.
  Many candidates know one and guess the other.
- Watch for `REQUIRES_NEW` used as a reflex to "make the error go away". `requires-new-trap` is
  that reflex applied to a debit. Ask what must be atomic with what.
- "Only public methods" is not a wrong answer, it is an old one. Give credit, then ask what the
  proxy is and what a subclass can override. A strong candidate reasons to private and final
  without having memorised a list.
- For senior candidates: where do you put a call to a payment provider, and what happens to the
  connection pool if you put it inside the transaction?
