# Guided Tasks — Idempotency

The same nine tasks as [`INTERVIEW_TASKS.md`](INTERVIEW_TASKS.md), step by step: the command to
run, the output you should see, why it happens, and the change to make.

Commands are for macOS and Linux. On Windows type `.\demo`, `.\inspect` and `.\check`.

The output below is copied from real runs. Timings, thread names, process ids, the key (a new UUID
every run) and which click wins will differ on your machine. The counts and the `RESULT` line should
match. In the SQL output, the long column list of one `select` is shortened to `...`.

**Before you start**

```bash
cd start
docker compose up -d
```

---

## Task 1: See the Double Charge

No code in this task. You are collecting evidence.

### Run it

```bash
./demo
```

### What you should see

```
Scenario: timeout | Idempotency-Key: bca91fb7-c608-43f2-9cf0-37d941c0d5b9 | card processor: 100 ms
Maria (customer 1) has 100.00 and pays 25.00 once.
The answer takes too long: the client gives up after 1000 ms and sends the same request again.

  +    0 ms  Client   -> POST /api/payments  25.00  (attempt 1)
  + 1004 ms  Client   !! no answer after 1000 ms: TIMEOUT. Was Maria charged? The client cannot know
  + 1746 ms  (lost)   <- 201 payment #1, balance after 75.00   <- the server did answer. Nobody was listening
  + 2005 ms  Client   -> POST /api/payments  25.00  (attempt 2, same key)
  + 2133 ms  Client   <- 201 payment #2, balance after 50.00

Payments Maria wanted to make:   1  (25.00)
Requests the client sent:        2
Card processor was called:       2 times
Payment rows in the database:    2
Idempotency-key rows:            0
Balance before:                  100.00
Balance after:                   50.00  (it should be 75.00)
Time taken:                      2133 ms

RESULT: DOUBLE CHARGE. 1 payment sent twice. Customer charged 2 times.
```

Maria wanted to pay once. She was charged twice. No error anywhere: both requests got a `201`.

### What just happened

In this run the card processor is slow for the first call (1.5 seconds). The client only waits 1
second.

| Time | Client | Server |
|---|---|---|
| 0 ms | sends attempt 1 | starts the payment, calls the card processor |
| 1000 ms | gives up waiting: **timeout** | still waiting for the card processor |
| about 1700 ms | (not listening any more) | card charged, payment #1 committed, answers `201` |
| 2000 ms | sends attempt 2 | sees a `POST /api/payments`: a new payment |
| about 2100 ms | gets `201 payment #2` | payment #2 committed |

Look at the line marked `(lost)`. The first request **succeeded**. Only its answer was lost,
because the client had stopped listening.

### Timeout means unknown

At +1000 ms the client knows one thing: no answer came. That can mean three different things.

1. The request never reached the server. Nothing happened.
2. The server is still working on it, or it crashed half way.
3. The server finished, and the answer got lost on the way back.

The client sees exactly the same thing in all three cases. So it cannot say "the payment failed".
It can only say "I do not know". If it does not retry, Maria may not have paid. If it retries, Maria
may pay twice. Retrying is the right thing to do, **if** the server can recognise a retry.

### See the SQL

```bash
./demo --sql
```

```
  +    0 ms  Client   -> POST /api/payments  25.00  (attempt 1)
            21:57:29.033 [exec-2] select c1_0.balance_cents from customer c1_0 where c1_0.id=?
  + 1010 ms  Client   !! no answer after 1000 ms: TIMEOUT. Was Maria charged? The client cannot know
            21:57:30.653 [exec-2] update customer c1_0 set balance_cents=(c1_0.balance_cents-?) where c1_0.id=? and c1_0.balance_cents>=?
            21:57:30.712 [exec-2] insert into payment (amount_cents,created_at,customer_id) values (?,?,?)
            21:57:30.745 [exec-2] select c1_0.balance_cents from customer c1_0 where c1_0.id=?
  + 1925 ms  (lost)   <- 201 payment #1, balance after 75.00   <- the server did answer. Nobody was listening
  + 2030 ms  Client   -> POST /api/payments  25.00  (attempt 2, same key)
            21:57:30.952 [exec-3] select c1_0.balance_cents from customer c1_0 where c1_0.id=?
            21:57:31.078 [exec-3] update customer c1_0 set balance_cents=(c1_0.balance_cents-?) where c1_0.id=? and c1_0.balance_cents>=?
            21:57:31.099 [exec-3] insert into payment (amount_cents,created_at,customer_id) values (?,?,?)
            21:57:31.110 [exec-3] select c1_0.balance_cents from customer c1_0 where c1_0.id=?
```

`exec-2` handled attempt 1, `exec-3` handled attempt 2. They ran the same four statements. Nothing
in there asks "have I seen this request before?".

### Find it in the code

The client **did** send an `Idempotency-Key` header: the same UUID on both attempts. Open
`PaymentController`: it reads the header and passes it on. Open `PaymentService.pay`:

```java
public PaymentResult pay(String idempotencyKey, PaymentRequest request) {
    return PaymentResult.created(charge(request));
}
```

The key is ignored.

```bash
./inspect
```

```
CUSTOMERS
  id  name   balance  payments  total_charged
  --  -----  -------  --------  -------------
  1   Maria  50.00    2         50.00
  2   Jamal  100.00   0         0.00

PAYMENTS
  id  customer_id  amount  created_at
  --  -----------  ------  ------------
  1   1            25.00   21:57:30.661
  2   1            25.00   21:57:31.098

IDEMPOTENCY KEYS (one row per key the server remembers, with the answer it gave)
  none. The server remembers no request.
```

Two payment rows that look like two honest payments. The server has no memory of requests.

### Which HTTP methods are idempotent

An operation is **idempotent** if doing it several times leaves the server in the same state as
doing it once.

| Method | Safe (changes nothing) | Idempotent | Why |
|---|---|---|---|
| GET, HEAD, OPTIONS | yes | yes | They only read |
| PUT | no | yes | "Replace this resource with this body." Twice gives the same resource |
| DELETE | no | yes | Deleted twice is still deleted (the second answer may be a 404, the state is the same) |
| POST | no | **no** | "Process this", usually "create a new one". Twice creates two |
| PATCH | no | not in general | "Add 1" is not, "set to 5" is |

A retried GET, PUT or DELETE does no harm. A retried POST repeats the effect, because nothing in
the request says **which** payment this is. The idempotency key adds exactly that: a name for the
operation.

### Check

```bash
./check 1
```

```
Task 1: See the double charge
  [PASS] No key: the same payment sent twice charges the customer twice (this documents the bug)
  1 of 1 pass
```

This test passes on purpose, and it keeps passing: a request **without** a key stays unprotected.
`./demo --no-key` will always show the double charge, so you can compare.

---

## Task 2: Fix It With an Idempotency Key

**The idea:** the client makes up a unique key for each payment, before the first attempt, and
sends the same key on every retry. The server remembers the keys it has processed. A key it has
seen before means "this is a retry": do not charge, answer with the payment that exists.

### Before

```bash
./check 2
```

```
Task 2: Fix it with an idempotency key
  [FAIL] Key: the server remembers the key in the idempotency_key table, with the payment it belongs to
           [rows in idempotency_key]
           expected: 1
           but was: 0
  [FAIL] Key: the retry is answered with the payment that already exists, not a new one
           [payment id in the retry's answer]
           expected: 1L
           but was: 2L
  [PASS] Key: the same key from two different customers is two payments (a key is scoped to its customer)
  [PASS] Key: two different keys are two payments
  [FAIL] Key: a retry with the same key charges the customer once
           [payment rows for one payment sent 3 times]
           expected: 1
           but was: 3
  2 of 5 pass
```

### The change

Everything you need exists: the `idempotency_key` table, the `IdempotencyRecord` entity and the
finder in `IdempotencyRecordRepository`. In `PaymentService`, replace the body of `pay` and add one
helper:

```java
@Transactional
public PaymentResult pay(String idempotencyKey, PaymentRequest request) {
    if (idempotencyKey == null) {
        // No key, no protection: the server cannot tell a retry from a second payment.
        return PaymentResult.created(charge(request));
    }

    // CHECK: has this customer already used this key?
    Optional<IdempotencyRecord> existing =
            keys.findByCustomerIdAndIdempotencyKey(request.customerId(), idempotencyKey);
    if (existing.isPresent()) {
        return alreadyPaid(existing.get());
    }

    PaymentResponse response = charge(request);                               // CHARGE
    keys.save(new IdempotencyRecord(request.customerId(), idempotencyKey, response.paymentId()));   // REMEMBER
    return PaymentResult.created(response);
}

/** The key was used before: answer 200 and describe the payment as it is now. */
private PaymentResult alreadyPaid(IdempotencyRecord record) {
    Payment payment = payments.findById(record.getPaymentId()).orElseThrow();
    long balanceNow = customers.findBalance(payment.getCustomerId()).orElseThrow();
    return new PaymentResult(200, new PaymentResponse(
            payment.getId(), payment.getCustomerId(), payment.getAmountCents(), balanceNow), false);
}
```

Imports: `java.util.Optional` and `com.amigoscode.idempotency.key.IdempotencyRecord`.

Three things to notice:

- **The key is saved in the same transaction as the payment.** `pay` is `@Transactional`. Either
  both rows are committed or neither is. If the payment could commit without the key, the next
  retry would charge again.
- **The lookup is by customer and key.** A key only means something for the customer who sent it.
  Task 8 comes back to this.
- **No key, no protection.** `null` means the client sent no header. That path is unchanged.

### After

```bash
./demo
```

```
  +    0 ms  Client   -> POST /api/payments  25.00  (attempt 1)
  + 1002 ms  Client   !! no answer after 1000 ms: TIMEOUT. Was Maria charged? The client cannot know
  + 1715 ms  (lost)   <- 201 payment #1, balance after 75.00   <- the server did answer. Nobody was listening
  + 2008 ms  Client   -> POST /api/payments  25.00  (attempt 2, same key)
  + 2039 ms  Client   <- 200 payment #1, balance after 75.00

Payments Maria wanted to make:   1  (25.00)
Requests the client sent:        2
Card processor was called:       1 time
Payment rows in the database:    1
Idempotency-key rows:            1
Balance before:                  100.00
Balance after:                   75.00
Time taken:                      2039 ms

RESULT: OK. 1 payment sent twice. Customer charged 1 time.
```

Attempt 2 was answered in 31 ms with payment **#1**. The card processor was called once.

```bash
./inspect
```

```
IDEMPOTENCY KEYS (one row per key the server remembers, with the answer it gave)
  customer_id  idempotency_key                       request_hash  payment_id  response_status  response_body  expires_in
  -----------  ------------------------------------  ------------  ----------  ---------------  -------------  ----------
  1            70f78a89-c8e2-4c33-be01-1dcc166e9306                1                                           23h 59m
```

The server now remembers: customer 1 used this key, and it led to payment 1. (The empty columns are
for Tasks 6 and 7.)

```bash
./check 2
```

5 of 5 pass. `./demo --no-key` still prints `RESULT: DOUBLE CHARGE`: that is expected.

---

## Task 3: Inspect the Double Click

The retry after a timeout is safe. Now send the two copies **at the same moment**.

### Step 1: run it

```bash
./demo --scenario double-click
```

```
Scenario: double-click | Idempotency-Key: a91b7153-02e1-4b19-9abb-5dfa0537070c | card processor: 100 ms
Maria (customer 1) has 100.00 and pays 25.00 once.
She double-clicks "Pay": the same request is sent twice at the same moment.

  +    0 ms  Click 1  -> POST /api/payments  25.00
  +    0 ms  Click 2  -> POST /api/payments  25.00
  +  283 ms  Click 1  <- 201 payment #1, balance after 75.00
  +  284 ms  Click 2  <- 201 payment #2, balance after 50.00

Payments Maria wanted to make:   1  (25.00)
Requests the client sent:        2
Card processor was called:       2 times
Payment rows in the database:    2
Idempotency-key rows:            2
Balance before:                  100.00
Balance after:                   50.00  (it should be 75.00)
Time taken:                      284 ms

RESULT: DOUBLE CHARGE. 1 payment sent twice. Customer charged 2 times.
```

Same key on both requests, and Maria is charged twice again.

### Step 2: the key table

```bash
./inspect
```

```
IDEMPOTENCY KEYS (one row per key the server remembers, with the answer it gave)
  customer_id  idempotency_key                       request_hash  payment_id  response_status  response_body  expires_in
  -----------  ------------------------------------  ------------  ----------  ---------------  -------------  ----------
  1            a91b7153-02e1-4b19-9abb-5dfa0537070c                1                                           23h 59m
  1            a91b7153-02e1-4b19-9abb-5dfa0537070c                2                                           23h 59m
```

The **same key twice**, pointing at two different payments. The table that should make a key unique
does not stop a second row.

### Step 3: the SQL

```bash
./demo --scenario double-click --sql
```

```
  [exec-3] select ir1_0.id,... from idempotency_key ir1_0 where ir1_0.customer_id=? and ir1_0.idempotency_key=?
  [exec-2] select ir1_0.id,... from idempotency_key ir1_0 where ir1_0.customer_id=? and ir1_0.idempotency_key=?
  [exec-2] select c1_0.balance_cents from customer c1_0 where c1_0.id=?
  [exec-3] select c1_0.balance_cents from customer c1_0 where c1_0.id=?
  [exec-3] update customer c1_0 set balance_cents=(c1_0.balance_cents-?) where c1_0.id=? and c1_0.balance_cents>=?
  [exec-2] update customer c1_0 set balance_cents=(c1_0.balance_cents-?) where c1_0.id=? and c1_0.balance_cents>=?
  [exec-2] insert into payment (amount_cents,created_at,customer_id) values (?,?,?)
  [exec-2] select c1_0.balance_cents from customer c1_0 where c1_0.id=?
  [exec-2] insert into idempotency_key (created_at,customer_id,expires_at,idempotency_key,payment_id,request_hash,response_body,response_status) values (?,?,?,?,?,?,?,?)
  [exec-3] insert into payment (amount_cents,created_at,customer_id) values (?,?,?)
  [exec-3] select c1_0.balance_cents from customer c1_0 where c1_0.id=?
  [exec-3] insert into idempotency_key (created_at,customer_id,expires_at,idempotency_key,payment_id,request_hash,response_body,response_status) values (?,?,?,?,?,?,?,?)
```

The first two lines are the whole story. Both threads ran the `select ... from idempotency_key`
before either had run its `insert into idempotency_key`. Both found nothing.

### Step 4: what just happened

| Time | Click 1 | Click 2 | Rows for this key |
|---|---|---|---|
| 1 | CHECK: key known? **no** | | 0 |
| 2 | | CHECK: key known? **no** | 0 |
| 3 | CHARGE (100 ms) | CHARGE (100 ms) | 0 |
| 4 | REMEMBER the key | | 1 |
| 5 | | REMEMBER the key | 2 |

This is **check-then-act**: the code reads something, decides, and acts on the decision later. In
between, the answer to the check can change. The retry after a timeout was safe only because the
first request had finished (and committed its key) before the second one started.

A `SELECT` cannot fix this, however you write the Java around it. Each request can only see rows
that are **committed**. While the first request is still charging, its key row does not exist for
anyone else.

### Step 5: ten copies

```bash
./demo --scenario double-click --requests 10
```

```
  +  308 ms  Click 2  <- 201 payment #2, balance after 50.00
  +  308 ms  Click 1  <- 201 payment #1, balance after 75.00
  +  312 ms  Click 7  <- 201 payment #3, balance after 25.00
  +  317 ms  Click 6  <- 201 payment #4, balance after 0.00
  +  322 ms  Click 8  <- 402 INSUFFICIENT_FUNDS
  ...
Card processor was called:       10 times
Payment rows in the database:    4
Balance after:                   0.00  (it should be 75.00)

RESULT: DOUBLE CHARGE. 1 payment sent 10 times. Customer charged 4 times.
```

All ten passed the check. The charges only stopped because Maria ran out of money: 4 x 25.00 is
everything she had. The card processor was called ten times.

### Step 6: "it only happens because the card processor is slow"

```bash
./demo --scenario double-click --processor-ms 0
```

```
RESULT: DOUBLE CHARGE. 1 payment sent twice. Customer charged 2 times.
```

Run it a few times. With a card processor that takes 0 ms the gap between CHECK and REMEMBER is a
few milliseconds, and two requests still land inside it. A smaller gap makes the bug rarer, not
gone.

---

## Task 4: Fix the Race With a UNIQUE Constraint

**The idea:** two requests cannot both be trusted to decide "this key is new". The database is the
one place where they meet, so let it decide. A UNIQUE constraint makes a second row impossible. And
the key row is written **first**, so the request that cannot write it never gets as far as the card
processor.

### Before

```bash
./check 4
```

1 of 5 pass.

### Step 1: the constraint

In `src/main/resources/schema.sql`, replace the `TODO Task 4` comment in the `idempotency_key`
table with a constraint (and add a comma to the line above it):

```sql
    expires_at      timestamptz not null default now() + interval '24 hours',
    constraint uq_idempotency_key unique (customer_id, idempotency_key)
);
```

`schema.sql` runs every time the application starts, so the next `./demo` has the constraint.

### Look before you go on

Change nothing else yet. Run:

```bash
./demo --scenario double-click
```

```
  +    0 ms  Click 2  -> POST /api/payments  25.00
  +    1 ms  Click 1  -> POST /api/payments  25.00
  +  402 ms  Click 1  <- 201 payment #1, balance after 75.00
  +  410 ms  Click 2  <- 500 (could not execute statement [ERROR: duplicate key value violates unique constraint "uq_ide...)

Payments Maria wanted to make:   1  (25.00)
Requests the client sent:        2
Card processor was called:       2 times
Payment rows in the database:    1
Idempotency-key rows:            1
Balance before:                  100.00
Balance after:                   75.00
Server errors (500):             1
Time taken:                      410 ms

RESULT: CARD CHARGED TWICE. The database has 1 payment, but the card processor was called 2 times. A rollback does not undo that.
RESULT: SERVER ERROR. 1 of 2 requests got a 500.
```

The database is correct now: one payment, one key row, balance 75.00. The second request's
`insert into idempotency_key` hit the constraint, and its whole transaction was rolled back,
payment row included.

But two things are still wrong:

- **The card processor was called twice.** The second request found out it was a duplicate at the
  very end, after it had charged the card. A database rollback does not un-charge a card.
- **The client got a 500** for a payment that worked.

The constraint is in the right place. The insert is in the wrong place.

### Step 2: an insert that can lose

In `IdempotencyRecordRepository`, replace the `TODO Task 4` comment:

```java
@Modifying
@Query(nativeQuery = true, value = "insert into idempotency_key (customer_id, idempotency_key) "
        + "values (:customerId, :idempotencyKey) "
        + "on conflict (customer_id, idempotency_key) do nothing")
int insertIfAbsent(@Param("customerId") long customerId,
                   @Param("idempotencyKey") String idempotencyKey);
```

`on conflict ... do nothing` is Postgres for "if this would break the constraint, skip it and do
not raise an error". The method returns the number of rows inserted: 1 means the key is yours, 0
means somebody else has it.

### Step 3: claim the key first

In `PaymentService.pay`, replace everything from `// CHECK` down to the `return`:

```java
// CLAIM the key before charging. Of two requests with the same key, the database lets
// exactly one insert the row. The other one waits here, then inserts nothing.
boolean claimed = keys.insertIfAbsent(request.customerId(), idempotencyKey) == 1;
IdempotencyRecord record = keys
        .findByCustomerIdAndIdempotencyKey(request.customerId(), idempotencyKey)
        .orElseThrow();
if (!claimed) {
    return alreadyPaid(record);
}

PaymentResponse response = charge(request);                               // CHARGE
record.setPaymentId(response.paymentId());                                // REMEMBER
return PaymentResult.created(response);
```

The `java.util.Optional` import is no longer needed. `alreadyPaid` stays.

The order is now CLAIM, CHARGE, REMEMBER, all in one transaction:

- The winner inserts the key row, charges, and writes the payment id into the row. (`record` is a
  managed entity: Hibernate writes the change at commit. No `save` call is needed.)
- The loser inserts 0 rows and returns before `charge` is ever called.

> **Why not `save` and catch the exception?** You can catch `DataIntegrityViolationException`, but
> in Postgres a failed statement aborts the whole transaction: every statement after it fails with
> "current transaction is aborted". You would have to catch the exception outside the
> transactional method and start a new transaction to read the stored row. `ON CONFLICT DO NOTHING`
> reports the conflict as a row count instead of an error, so the transaction stays usable.

### After

```bash
./demo --scenario double-click
```

```
  +    0 ms  Click 2  -> POST /api/payments  25.00
  +    1 ms  Click 1  -> POST /api/payments  25.00
  +  406 ms  Click 1  <- 201 payment #1, balance after 75.00
  +  410 ms  Click 2  <- 200 payment #1, balance after 75.00

Payments Maria wanted to make:   1  (25.00)
Requests the client sent:        2
Card processor was called:       1 time
Payment rows in the database:    1
Idempotency-key rows:            1
Balance before:                  100.00
Balance after:                   75.00
Time taken:                      410 ms

RESULT: OK. 1 payment sent twice. Customer charged 1 time.
```

One charge, one call to the card processor, no 500. Both clicks are told about payment #1.

```bash
./check 4
```

```
Task 4: Fix the race with a UNIQUE constraint
  [PASS] Race: a payment that fails leaves no key behind, so the same key can be tried again later
  [PASS] Race: the database itself refuses a second row for the same customer and key (UNIQUE constraint)
  [PASS] Race: two requests with the same key at the same moment charge the customer once, and the card once
  [PASS] Race: the request that loses is answered with the winner's payment, not with an error
  [PASS] Race: 8 requests with the same key at the same moment: one payment row, one charge, 8 answers
  5 of 5 pass
```

Run `./check 2` too: it must still be green.

---

## Task 5: Inspect the Insert-First Fix

### Step 1: the SQL that claims the key

```bash
./demo --scenario double-click --sql
```

```
  19:00:45.503 [exec-2] insert into idempotency_key (customer_id, idempotency_key) values (?, ?) on conflict (customer_id, idempotency_key) do nothing
  19:00:45.503 [exec-3] insert into idempotency_key (customer_id, idempotency_key) values (?, ?) on conflict (customer_id, idempotency_key) do nothing
  19:00:45.561 [exec-2] select ir1_0.id,... from idempotency_key ir1_0 where ir1_0.customer_id=? and ir1_0.idempotency_key=?
  19:00:45.591 [exec-2] select c1_0.balance_cents from customer c1_0 where c1_0.id=?
  19:00:45.706 [exec-2] update customer c1_0 set balance_cents=(c1_0.balance_cents-?) where c1_0.id=? and c1_0.balance_cents>=?
  19:00:45.717 [exec-2] insert into payment (amount_cents,created_at,customer_id) values (?,?,?)
  19:00:45.728 [exec-2] select c1_0.balance_cents from customer c1_0 where c1_0.id=?
  19:00:45.735 [exec-2] update idempotency_key set payment_id=? where id=?
  19:00:45.743 [exec-3] select ir1_0.id,... from idempotency_key ir1_0 where ir1_0.customer_id=? and ir1_0.idempotency_key=?
  19:00:45.751 [exec-3] select p1_0.id,p1_0.amount_cents,p1_0.created_at,p1_0.customer_id from payment p1_0 where p1_0.id=?
  19:00:45.755 [exec-3] select c1_0.balance_cents from customer c1_0 where c1_0.id=?
```

Both threads **send** the same `insert` at the same moment (`.503`). `exec-2` goes on at once and
does the whole payment. `exec-3` sent four statements in total: the insert, and three selects to
build its answer. No `update customer`, no `insert into payment`.

### Step 2: the loser waited

Look at the timestamps for `exec-3`. Its insert was sent at `.503`. Its next statement ran at
`.743`, right after `exec-2`'s last statement at `.735`. For those 240 ms `exec-3` was stuck
**inside the insert**.

Postgres cannot answer `exec-3`'s insert yet. Another transaction has inserted the same key and has
not committed. If that transaction commits, `exec-3` must insert nothing. If it rolls back,
`exec-3` may insert. So Postgres makes `exec-3` wait until it knows.

### Step 3: watch it wait

You need two terminals, both in `start/`.

Terminal 1:

```bash
./demo --scenario double-click --pause
```

Terminal 2, within 10 seconds:

```bash
./inspect
```

```
CUSTOMERS
  id  name   balance  payments  total_charged
  --  -----  -------  --------  -------------
  1   Maria  100.00   0         0.00
  2   Jamal  100.00   0         0.00

PAYMENTS
  none

IDEMPOTENCY KEYS (one row per key the server remembers, with the answer it gave)
  none. The server remembers no request.

SESSIONS INSIDE A TRANSACTION (blocked_by_pid = the session this one is waiting for)
  pid    state                waiting_on           blocked_by_pid  in_transaction_ms  last_statement
  -----  -------------------  -------------------  --------------  -----------------  ------------------------------------------------------------
  34102  active               Lock: transactionid  34101           2111               insert into idempotency_key (customer_id, idempotency_key) v
  34101  idle in transaction  Client: ClientRead                   2108               select c1_0.balance_cents from customer c1_0 where c1_0.id=$
```

Read it row by row:

- **Session 34101** is the winner. It is `idle in transaction`: it inserted the key, read the
  balance, and the application is now busy with the 10-second card processor.
- **Session 34102** is the loser. It is `active`, waiting on a `Lock`, and `blocked_by_pid` says it
  waits for 34101. Its last statement is the `insert into idempotency_key`. It has been inside that
  one statement for 2 seconds.
- The `idempotency_key` table looks **empty** from outside. The winner's row is not committed yet,
  so `./inspect` cannot see it. This is exactly why the `SELECT` in Task 2 could not work, and why
  the insert can: a unique index also knows about rows that are not committed yet.

Tip: `./inspect --watch` refreshes every second.

### Step 4: what the loser answers

Back in terminal 1:

```
  +10654 ms  Click 1  <- 201 payment #1, balance after 75.00
  +10658 ms  Click 2  <- 200 payment #1, balance after 75.00
```

The loser was answered 4 ms after the winner, with the winner's payment. It waited, then it told
the client the truth.

This is one of two valid designs for "what does the second request get while the first is still in
progress":

| | Wait, then return the result (this project) | Answer at once with 409 |
|---|---|---|
| How | Key row and charge in one transaction. The second insert waits on the unique index | Commit the key row first with a status such as `IN_PROGRESS`, do the work, then mark it done. A duplicate that finds `IN_PROGRESS` gets `409 Conflict` |
| The duplicate gets | The real answer, a little later | "Request in progress, try again" |
| Cost | The waiting request holds a database connection, and the transaction is open during the card call | More code, and you need a rule for rows that stay `IN_PROGRESS` after a crash |
| Use it when | The work is short | The work is a slow call to another service |

### Step 5: thirty copies

```bash
./demo --scenario double-click --requests 30
```

```
Answers:                         200 x 29, 201 x 1
Payments Maria wanted to make:   1  (25.00)
Requests the client sent:        30
Card processor was called:       1 time
Payment rows in the database:    1
Idempotency-key rows:            1

RESULT: OK. 1 payment sent 30 times. Customer charged 1 time.
```

One `201`, twenty-nine `200`, one call to the card processor. While they waited, the losers each
held a database connection (the pool has 10, so most of them were waiting for a connection first).
That is the cost of this design.

### Step 6: when the winner fails

If the winner's payment fails, for example with insufficient funds, its transaction rolls back and
its key row disappears with it. The waiting insert then **succeeds**: the waiting request becomes
the new owner of the key and tries the payment itself.

That is the reason the key row and the payment are in one transaction. A key row must never say
"done" for a payment that does not exist. The test
`ConcurrentDuplicatesIT.failedPaymentLeavesNoKeyBehind` pins this down.

### What to take away

- The key is claimed by an `INSERT`, not checked by a `SELECT`. The UNIQUE constraint decides.
- The loser waits inside its insert until the winner commits, then inserts 0 rows.
- The loser never reaches the card processor.
- Key row and payment commit together or not at all.

---

## Task 6: Replay the Same Response

**The idea:** for the client, the retry **is** the request. It should get what the original got.

### Before

```bash
./demo --scenario replay
```

```
Scenario: replay | Idempotency-Key: 88afaf60-7a87-4ce1-bc17-03d94078e756 | card processor: 100 ms
Maria (customer 1) has 100.00 and pays 25.00 once.
Then she makes a second, different payment of 10.00. Then the client retries the first payment.

  +    0 ms  Client   -> POST /api/payments  25.00  (payment A, key A)
  +  323 ms  Client   <- 201 payment #1, balance after 75.00
  +  323 ms  Client   -> POST /api/payments  10.00  (payment B, key B)
  +  459 ms  Client   <- 201 payment #2, balance after 65.00
  +  459 ms  Client   -> POST /api/payments  25.00  (payment A again, key A)
  +  485 ms  Client   <- 200 payment #1, balance after 65.00

RESULT: DIFFERENT ANSWER. The original said 201 payment #1, balance after 75.00. The retry said 200 payment #1, balance after 65.00.
```

Maria is charged correctly (two payments, 35.00). But the retry of payment A got a different
answer:

- `200` where the original said `201`. A client that checks for `201 Created` treats the retry as
  something else.
- `balance after 65.00` where the original said `75.00`. `alreadyPaid` builds the answer from the
  data as it is **now**. The response of the original cannot be rebuilt later: the world has moved
  on.

```bash
./check 6
```

1 of 5 pass.

### Step 1: store the answer, send it again

The columns `response_status` and `response_body` already exist. In `PaymentService.pay`, replace
everything from `if (!claimed)` down to the `return`:

```java
if (!claimed) {
    // Send the stored answer again: same status, same body.
    return PaymentResult.replay(record.getResponseStatus(),
            PaymentResponse.fromJson(record.getResponseBody()));
}

PaymentResponse response = charge(request);                               // CHARGE
PaymentResult result = PaymentResult.created(response);
record.setPaymentId(response.paymentId());                                // REMEMBER the payment
record.setResponseStatus(result.status());                                // ...and the answer
record.setResponseBody(response.toJson());
return result;
```

Delete the `alreadyPaid` method: nothing calls it any more.

The answer is stored in the same row, in the same transaction, as the key. `PaymentResult.replay`
builds a result with `replayed = true`.

### Step 2: the header

In `PaymentController.pay`, replace the `TODO Task 6` and the `return`:

```java
ResponseEntity.BodyBuilder response = ResponseEntity.status(result.status());
if (result.replayed()) {
    // Same status and body as the original. The header is the only way to tell them apart.
    response.header(IDEMPOTENT_REPLAY, "true");
}
return response.body(result.body());
```

Status and body are identical to the original, so a client needs no second code path. The header
is there for logs, debugging and the curious.

### After

```bash
./demo --scenario replay
```

```
  +    0 ms  Client   -> POST /api/payments  25.00  (payment A, key A)
  +  522 ms  Client   <- 201 payment #1, balance after 75.00
  +  523 ms  Client   -> POST /api/payments  10.00  (payment B, key B)
  +  684 ms  Client   <- 201 payment #2, balance after 65.00
  +  687 ms  Client   -> POST /api/payments  25.00  (payment A again, key A)
  +  715 ms  Client   <- 201 payment #1, balance after 75.00  [Idempotent-Replay: true]

RESULT: OK. The retry got the original answer, byte for byte (201 payment #1, balance after 75.00), marked Idempotent-Replay: true.
```

And the scenario from Task 1:

```bash
./demo
```

```
  + 1009 ms  Client   !! no answer after 1000 ms: TIMEOUT. Was Maria charged? The client cannot know
  + 2321 ms  (lost)   <- 201 payment #1, balance after 75.00   <- the server did answer. Nobody was listening
  + 2324 ms  Client   -> POST /api/payments  25.00  (attempt 2, same key)
  + 2361 ms  Client   <- 201 payment #1, balance after 75.00  [Idempotent-Replay: true]
```

The answer that was lost is delivered after all. The client's question "was Maria charged?" is
answered by retrying.

```bash
./check 6
```

5 of 5 pass.

---

## Task 7: Same Key, Different Body

**The idea:** a key names one request. If the same key arrives with a different body, the client
has a bug, and the server must not paper over it.

### Before

```bash
./demo --scenario different-body
```

```
Scenario: different-body | Idempotency-Key: abbad7ef-cd98-4890-b675-d1d6560ab982 | card processor: 100 ms
Maria (customer 1) has 100.00 and pays 25.00 once.
Then a buggy client sends a payment of 40.00 with the SAME key.

  +    0 ms  Client   -> POST /api/payments  25.00
  +  871 ms  Client   <- 201 payment #1, balance after 75.00
  +  871 ms  Client   -> POST /api/payments  40.00  (same key, different amount)
  +  940 ms  Client   <- 201 payment #1, balance after 75.00  [Idempotent-Replay: true]

RESULT: WRONG ANSWER. The client asked to pay 40.00 and was told 201, with the result of the 25.00 payment. Nothing was charged for 40.00.
```

The client asked to pay 40.00 and got `201`. It believes the payment went through. Nothing was
charged. A silent wrong answer is worse than an error.

### Step 1: the hash

In `RequestHash`, replace the stub:

```java
public static String of(PaymentRequest request) {
    String canonical = "customerId=" + request.customerId() + ";amountCents=" + request.amountCents();
    try {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
        throw new IllegalStateException("Every JDK has SHA-256", e);
    }
}
```

Imports: `java.nio.charset.StandardCharsets`, `java.security.MessageDigest`,
`java.security.NoSuchAlgorithmException`, `java.util.HexFormat`.

The field names and the `;` matter. Without them, customer 1 paying 2500 and customer 12 paying 500
would both be hashed as `12500`.

### Step 2: store it when you claim the key

In `IdempotencyRecordRepository`, add the column and the parameter to `insertIfAbsent`:

```java
@Modifying
@Query(nativeQuery = true, value = "insert into idempotency_key (customer_id, idempotency_key, request_hash) "
        + "values (:customerId, :idempotencyKey, :requestHash) "
        + "on conflict (customer_id, idempotency_key) do nothing")
int insertIfAbsent(@Param("customerId") long customerId,
                   @Param("idempotencyKey") String idempotencyKey,
                   @Param("requestHash") String requestHash);
```

### Step 3: compare it when the claim fails

In `PaymentService.pay`:

```java
String requestHash = RequestHash.of(request);
boolean claimed = keys.insertIfAbsent(request.customerId(), idempotencyKey, requestHash) == 1;
IdempotencyRecord record = keys
        .findByCustomerIdAndIdempotencyKey(request.customerId(), idempotencyKey)
        .orElseThrow();
if (!claimed) {
    if (!requestHash.equals(record.getRequestHash())) {
        throw new IdempotencyKeyReusedException(idempotencyKey);
    }
    // Send the stored answer again: same status, same body.
    return PaymentResult.replay(record.getResponseStatus(),
            PaymentResponse.fromJson(record.getResponseBody()));
}
```

Imports: `com.amigoscode.idempotency.key.RequestHash` and
`com.amigoscode.idempotency.key.IdempotencyKeyReusedException`. `ApiExceptionHandler` already turns
the exception into a `422`.

Why `422` and not `409`? The request is well formed and nothing is in progress. It can never
succeed with this key, however often the client retries. `422 Unprocessable Content` says "fix your
request". (Some APIs use `409` or `400` here. The status matters less than refusing.)

### After

```bash
./demo --scenario different-body
```

```
  +    0 ms  Client   -> POST /api/payments  25.00
  +  883 ms  Client   <- 201 payment #1, balance after 75.00
  +  884 ms  Client   -> POST /api/payments  40.00  (same key, different amount)
  +  922 ms  Client   <- 422 IDEMPOTENCY_KEY_REUSED

RESULT: OK. The reused key was rejected with 422. Only the first payment (25.00) was charged.
```

```bash
./check 7
```

```
Task 7: Same key, different body
  [PASS] Mismatch: the same key with a different amount is rejected with IdempotencyKeyReusedException (a 422)
  [PASS] Mismatch: the hash of the request is stored with the key
  [PASS] Mismatch: the rejected request charges nothing, and the first payment is untouched
  [PASS] Hash: a different amount or a different customer gives a different hash
  [PASS] Hash: the same request always gives the same hash
  [PASS] Hash: it is a SHA-256 in hex (64 characters), not a copy of the request
  6 of 6 pass
```

```bash
./check
```

32 passes, no `[FAIL]`. Your `start/` now does what `solution/` does.

---

## Task 8: Inspect the Key Table: Scope and Expiry

No code in this task.

### Step 1: one row, column by column

```bash
./demo --scenario different-body
./inspect
```

```
IDEMPOTENCY KEYS (one row per key the server remembers, with the answer it gave)
  customer_id  idempotency_key                       request_hash     payment_id  response_status  response_body                                                               expires_in
  -----------  ------------------------------------  ---------------  ----------  ---------------  --------------------------------------------------------------------------  ----------
  1            a53c752b-d960-4e86-aa4f-a4e814cd546d  75fdd29a52da...  1           201              {"paymentId":1,"customerId":1,"amountCents":2500,"balanceAfterCents":7500}  23h 59m
```

| Column | What it is for | Task |
|---|---|---|
| `customer_id` + `idempotency_key` | Who sent which key. UNIQUE together | 2 and 4 |
| `request_hash` | Is a returning key the same request? (`./inspect` shows the first 12 characters) | 7 |
| `payment_id` | The payment this key led to | 2 |
| `response_status`, `response_body` | The answer to send again | 6 |
| `expires_in` | How long the server still remembers this key | this task |

### Step 2: why a hash

Here the request is two numbers. A real request can be large and can contain personal or card data.
A hash is always 64 characters, and it tells you "same or different" without keeping a second copy
of the data. That is all the server needs to know.

### Step 3: scope

The UNIQUE constraint is on `(customer_id, idempotency_key)`, and every lookup uses both.

If it were on the key alone, keys would be global. Then customer 2 could send a key that customer 1
has used, by accident or on purpose, and would receive customer 1's stored response. A key means
"my operation number X". It only makes sense together with whoever "my" is: the customer, or in a
public API the authenticated API client.

`IdempotencyKeyIT.keysAreScopedPerCustomer` checks it: the same key from Maria and from Jamal is
two payments.

### Step 4: lifetime

Every row has an `expires_at`, 24 hours after it was created (see `schema.sql` and
`IdempotencyRecord.TIME_TO_LIVE`). `IdempotencyCleanup.deleteExpired()` deletes every row whose time
is up. In SQL terms:

```sql
delete from idempotency_key where expires_at < now()
```

In a real application a scheduled job calls it, for example every hour. Here the demo and the tests
call it.

### Step 5: after expiry

```bash
./demo --scenario expired
```

```
Scenario: expired | Idempotency-Key: 9b8eda03-e3bf-40aa-8187-0bc08e8c4690 | card processor: 100 ms
Maria (customer 1) has 100.00 and pays 25.00 once.
A day later the key has expired and been deleted, and the client retries.

  +    0 ms  Client   -> POST /api/payments  25.00
  + 1355 ms  Client   <- 201 payment #1, balance after 75.00
  + 1427 ms  Server   .. 24 hours pass. The cleanup deletes 1 expired key row
  + 1429 ms  Client   -> POST /api/payments  25.00  (retry, same key)
  + 1700 ms  Client   <- 201 payment #2, balance after 50.00

Payments Maria wanted to make:   1  (25.00)
Requests the client sent:        2
Card processor was called:       2 times
Payment rows in the database:    2
Idempotency-key rows:            1
Balance before:                  100.00
Balance after:                   50.00  (it should be 75.00)

RESULT: CHARGED AGAIN. The key was gone, so the retry was a new payment. Customer charged 2 times.
```

Once the row is deleted, the server has forgotten the request. The same key is a new key, and the
retry is a new payment. This is not a bug in the code. It is the limit of the technique: the
protection lasts as long as the row.

### Step 6: how long

The lifetime has to be longer than the longest time a client keeps retrying. Clients retry for
seconds or minutes, and background jobs for a few hours, so 24 hours is a common choice. Keeping
keys for ever is not free: the table grows with every request, and so does its index.

If a payment must never happen twice, however late the retry, a key that expires is the wrong tool.
Use a business rule that never expires. Task 9, step 4.

```bash
./check 8
```

```
Task 8: Scope and expiry
  [PASS] Expiry: the cleanup deletes the keys whose expires_at is in the past and keeps the others
  [PASS] Expiry: once a key has expired and been deleted, a retry with it is a new payment and charges again (by design)
  2 of 2 pass
```

These pass from the start. They document the behaviour.

---

## Task 9: Where Else, and When You Do Not Need a Key Table

### Step 1: the email that was sent three times

A queue (SQS, RabbitMQ, Kafka) delivers a message **at least once**. The consumer reads the message,
sends the email, and then acknowledges the message. If it crashes after the email and before the
acknowledgement, or is simply too slow, the broker assumes the message was not processed and
delivers it again. The consumer sends the email again.

It is the payment problem with the roles swapped. The broker is the client that did not get an
answer, so it retries.

The fix is the same table with another name:

```sql
create table processed_message (
    message_id   text primary key,
    processed_at timestamptz not null default now()
);
```

```sql
insert into processed_message (message_id) values (?) on conflict do nothing
```

1 row inserted: do the work. 0 rows: you have seen this message, skip it and acknowledge. The
message id is the idempotency key. Do the insert in the same transaction as the consumer's own
database work.

"Exactly once" is, in practice, at-least-once delivery plus an idempotent consumer.

### Step 2: webhooks

A provider that calls your webhook retries until you answer `200`. If your answer gets lost, you
receive the same event twice. Every serious provider puts an **event id** in the payload. That is
your key: store it with a unique constraint, and answer `200` to an event you already have.

### Step 3: PUT needs no key

`PUT /api/customers/1` with the full customer says "customer 1 looks like this now". Sent three
times, customer 1 looks like that. The operation is idempotent by its meaning. This is **natural
idempotency**: no table, no key, nothing to expire.

### Step 4: a business key

If the rule is "one payment per order", the order id is the key, and the constraint belongs on the
payment itself:

```sql
alter table payment add column order_id text not null;
alter table payment add constraint uq_payment_order unique (order_id);
```

```sql
insert into payment (order_id, customer_id, amount_cents)
values (?, ?, ?)
on conflict (order_id) do nothing
```

0 rows inserted means this order is already paid. This never expires, needs no extra table, and it
also protects against two different clients paying the same order.

### Step 5: add or set

- `POST .../balance/add {"amountCents": 500}`: each copy adds 5.00. Not idempotent. Needs a key.
- `PUT .../balance {"balanceCents": 10500}`: each copy sets the same value. Idempotent.

When you design an API, prefer the second shape where the business allows it. ("Set" has its own
problem when two people write at once: that is the locking project.)

### Step 6: GET must be safe

`GET /api/orders/42/cancel` changes state with a method that promises to change nothing. Everything
on the web relies on that promise: browsers prefetch links, crawlers follow them, chat apps open a
link to build a preview, proxies cache GETs and clients retry them without asking. Any of them
cancels the order. A cancel is `POST /api/orders/42/cancel`, or a `PATCH` or `PUT` of the order's
status, or a `DELETE`.

### Natural idempotency or a key table

| | Natural idempotency | Idempotency-key table |
|---|---|---|
| How | The operation means the same thing twice: PUT a full resource, DELETE, set a status, or a UNIQUE business key with `INSERT ... ON CONFLICT DO NOTHING` | A generic table: client + key, UNIQUE, with the stored response |
| Extra storage | None, or one constraint | A row per request, plus a cleanup |
| Expires | Never | Yes |
| Returns the original response | No. You answer from the current state | Yes |
| Works for | Operations that have an identity of their own (an order id, a resource URL) | Any POST, including ones with no natural identity ("charge 25.00") |
| Use it when | You can find or design a business key | You cannot, or you offer a public API and want one rule for every endpoint |

Look for natural idempotency first. Reach for the key table when the operation has no identity of
its own.

### How to say it in an interview

> "An operation is idempotent if doing it twice has the same effect as doing it once. It matters
> because a client that gets a timeout cannot know whether the request was processed, so it has to
> retry. GET, PUT and DELETE are idempotent by definition, POST is not. To make a payment POST
> idempotent, the client generates a UUID per payment and sends it as an Idempotency-Key header on
> every attempt. On the server I have a table with a unique constraint on customer and key. In one
> transaction I insert the key first, then charge, then store the response in that row. If the
> insert finds the key already there, I do not charge: I return the stored response, with the same
> status and body. A plain lookup before the charge is not enough, because two concurrent requests
> would both pass it: the unique constraint is what decides. I also store a hash of the request and
> reject the same key with a different body, and the keys expire after about a day. The same idea
> covers a queue consumer: delivery is at least once, so I record the message id with a unique
> constraint and skip duplicates. And where there is a natural business key, like one payment per
> order, I would put the unique constraint on that and not use a key table at all."

Then be ready for the follow-ups in "Questions to Ask Afterwards" at the end of
[`INTERVIEW_TASKS.md`](INTERVIEW_TASKS.md).

---

## Clean up

```bash
docker compose down
```
