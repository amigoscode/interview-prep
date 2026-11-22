# Guided Tasks — Locking

The same seven tasks as [`INTERVIEW_TASKS.md`](INTERVIEW_TASKS.md), step by step: the command to
run, the output you should see, why it happens, and the change to make.

Commands are for macOS and Linux. On Windows type `.\demo`, `.\inspect` and `.\check`.

Timings, thread names and which buyer wins will differ on your machine. The counts and the `RESULT`
line should match.

**Before you start**

```bash
cd start
docker compose up -d
```

---

## Task 1: See the Lost Update

No code in this task. You are collecting evidence.

### Run it

```bash
./demo
```

### What you should see

```
Strategy: naive | buyers: 2 | stock: 1 | fraud check: 100 ms
Every buyer orders 1 unit of product 1 at the same moment.

  +    0 ms  Buyer 1  -> POST /api/orders
  +    0 ms  Buyer 2  -> POST /api/orders
  +  254 ms  Buyer 2  <- 201 order #2 confirmed
  +  254 ms  Buyer 1  <- 201 order #1 confirmed

Stock before:              1
Orders confirmed (201):    2
Sold out (409):            0
Order rows in the database: 2
Stock after:               0
Product version:           0
Time taken:                254 ms

RESULT: OVERSOLD. 2 orders were confirmed but only 1 item was in stock.
RESULT: LOST UPDATE. 2 units were sold, so stock should be -1, but the database says 0.
```

One PS5, two confirmed orders. And the stock says 0, which looks fine. That is what makes this bug
dangerous: nothing crashed, no error was logged, and the stock number looks right.

### See the SQL

```bash
./demo --sql
```

```
  [exec-3] select p1_0.stock from product p1_0 where p1_0.id=?
  [exec-2] select p1_0.stock from product p1_0 where p1_0.id=?
  [exec-2] update product p1_0 set stock=? where p1_0.id=?
  [exec-3] update product p1_0 set stock=? where p1_0.id=?
  [exec-3] insert into orders (created_at,product_id,quantity) values (?,?,?)
  [exec-2] insert into orders (created_at,product_id,quantity) values (?,?,?)
```

`exec-2` and `exec-3` are the two request threads, one per buyer.

### What just happened

| Time | Buyer 1 | Buyer 2 | Stock in the database |
|---|---|---|---|
| 1 | reads stock: **1** | | 1 |
| 2 | | reads stock: **1** | 1 |
| 3 | 1 >= 1, so carry on | 1 >= 1, so carry on | 1 |
| 4 | fraud check (100 ms) | fraud check (100 ms) | 1 |
| 5 | writes stock = 1 - 1 = **0** | | 0 |
| 6 | | writes stock = 1 - 1 = **0** | 0 |
| 7 | saves order | saves order | 0 |

Both buyers read before either wrote. Each one did the subtraction **in Java**, using the value it
read earlier, then wrote the result. Buyer 2's write overwrote Buyer 1's write with the same number.
One of the two updates is lost. This anomaly is called a **lost update**.

That is also why the stock is 0 and not -1: neither buyer ever asked the database to subtract. Each
told it "set the stock to 0".

### Find it in the code

Open `OrderPlacer.placeNaive`:

```java
int stock = products.findStock(productId)...;          // READ
if (stock < quantity) { throw new SoldOutException(productId); }   // CHECK
fraudCheck.review(productId, quantity);                // ...time passes...
products.writeStock(productId, stock - quantity);      // WRITE
```

The READ and the WRITE are two separate statements. Between them the database is free to let anyone
else read the same row. `@Transactional` does not help: a transaction makes your statements succeed
or fail together, it does not stop another transaction from reading the row in between. (Postgres
runs at the READ COMMITTED isolation level by default, which allows exactly this.)

### Make it worse

```bash
./demo --buyers 50 --stock 10
```

```
Orders confirmed (201):    50
Stock after:               5
RESULT: OVERSOLD. 50 orders were confirmed but only 10 items were in stock.
RESULT: LOST UPDATE. 50 units were sold, so stock should be -40, but the database says 5.
```

50 orders for 10 items, and the stock claims there are still 5 left. (Your "stock after" may be a
different number: it depends on how the requests happened to overlap.)

### "It only happens because of the 100 ms fraud check"

```bash
./demo --fraud-check-ms 0
```

Run it a few times. It still oversells. The gap between the read and the write shrinks to a
millisecond or less, but two requests can still land inside it. A smaller gap makes the bug rarer,
not gone. In production it shows up a few times a month, under load, and nobody can reproduce it.

### Check

```bash
./check 1
```

```
Task 1: See the lost update
  [PASS] Naive: two buyers both get the last item, and the stock only goes down by one (this documents the bug)
  1 of 1 pass
```

This test passes on purpose. It pins the bug down so you can see what the fixes change.

---

## Task 2: Fix It With a Pessimistic Lock

**The idea:** if the second buyer cannot read the stock until the first buyer has finished, it can
never act on a stale value. So lock the row at the READ.

### Before

```bash
./demo --strategy pessimistic
./check 2
```

`RESULT: OVERSOLD`, and 0 of 3 checks pass. `placePessimistic` has the same bug as `placeNaive`, just
written the usual JPA way: load the entity, change it, let Hibernate write it back.

### Step 1: add a locking query

In `ProductRepository`:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select p from Product p where p.id = :id")
Optional<Product> findByIdForUpdate(@Param("id") long id);
```

Imports: `jakarta.persistence.LockModeType` and `org.springframework.data.jpa.repository.Lock`.

`PESSIMISTIC_WRITE` tells Hibernate to add a row lock to the SELECT. In SQL that is
`SELECT ... FOR UPDATE`.

### Step 2: use it

In `OrderPlacer.placePessimistic`, change `findById` to `findByIdForUpdate`:

```java
@Transactional
public Order placePessimistic(long productId, int quantity) {
    Product product = products.findByIdForUpdate(productId)
            .orElseThrow(() -> new ProductNotFoundException(productId));
    return reserve(product, quantity);
}
```

That is the whole fix. The method was already `@Transactional`, and it has to be: a row lock lasts
until the transaction ends. Without a transaction the lock would be released the moment the SELECT
returned, and you would be back to the bug.

### After

```bash
./demo --strategy pessimistic
```

```
  +    0 ms  Buyer 1  -> POST /api/orders
  +    0 ms  Buyer 2  -> POST /api/orders
  +  257 ms  Buyer 2  <- 409 SOLD_OUT
  +  257 ms  Buyer 1  <- 201 order #1 confirmed

Orders confirmed (201):    1
Sold out (409):            1
Stock after:               0

RESULT: OK. Stock and orders agree: 1 - 1 = 0.
```

```bash
./check 2
```

```
Task 2: Fix it with a pessimistic lock
  [PASS] Pessimistic: the second buyer cannot even read the stock while the first buyer's transaction is open
  [PASS] Pessimistic: two buyers, one item: exactly one order is confirmed, the other buyer is told it is sold out
  [PASS] Pessimistic: 30 buyers, 10 items: exactly 10 orders, 20 sold out, stock ends at 0
  3 of 3 pass
```

---

## Task 3: Inspect the Pessimistic Lock

### Step 1: the SQL that takes the lock

```bash
./demo --strategy pessimistic --sql
```

```
  [exec-2] select p1_0.id,p1_0.name,p1_0.stock,p1_0.version from product p1_0 where p1_0.id=? for no key update of p1_0
  [exec-3] select p1_0.id,p1_0.name,p1_0.stock,p1_0.version from product p1_0 where p1_0.id=? for no key update of p1_0
  [exec-3] insert into orders (created_at,product_id,quantity) values (?,?,?)
  [exec-3] update product set name=?,stock=?,version=? where id=? and version=?
  +  257 ms  Buyer 2  <- 409 SOLD_OUT
  +  257 ms  Buyer 1  <- 201 order #1 confirmed
```

The SELECT now ends in `for no key update`. That is the lock.

> **Why not `for update`?** On Postgres, Hibernate uses `FOR NO KEY UPDATE`. It is the same idea
> with one difference: it does not block other transactions from inserting rows that reference this
> product through a foreign key. For two buyers after the same stock it behaves exactly like
> `FOR UPDATE`. In an interview, "SELECT FOR UPDATE" is the name everyone uses.

(Your `update` line may show `set name=?,stock=? where id=?` without `version`. The version part
appears after Task 4.)

### Step 2: who got through

Both threads **sent** the SELECT at the same moment, so both lines are logged. But only `exec-3` goes
on to INSERT and UPDATE. `exec-2`'s SELECT did not return: the database made it wait. When `exec-3`
committed, `exec-2`'s SELECT finally returned with the fresh row, `stock = 0`, and the Java check
`stock < quantity` threw `SoldOutException`. No stale read, so no lost update.

### Step 3: watch it wait

You need two terminals, both in `start/`.

Terminal 1:

```bash
./demo --strategy pessimistic --pause
```

Terminal 2, within 10 seconds:

```bash
./inspect
```

```
PRODUCT
  id  name  stock  version
  --  ----  -----  -------
  1   PS5   1      0

ORDERS
  order_rows  units_sold
  ----------  ----------
  0           0

SESSIONS INSIDE A TRANSACTION (blocked_by_pid = the session this one is waiting for)
  pid   state                waiting_on           blocked_by_pid  in_transaction_ms  last_statement
  ----  -------------------  -------------------  --------------  -----------------  ----------------------------------
  1239  active               Lock: transactionid  1238            6566               select p1_0.id,p1_0.name,p1_0.stoc
  1238  idle in transaction  Client: ClientRead                   6564               select p1_0.id,p1_0.name,p1_0.stoc
```

Read it row by row:

- **Session 1238** is `idle in transaction`. It ran its SELECT, got the lock, and the database is
  now waiting for the application to send the next statement. The application is busy with the
  10-second fraud check. The lock is held the whole time.
- **Session 1239** is `active` and waiting on a `Lock`, and `blocked_by_pid` says it is waiting for
  1238. Its last statement is the same SELECT. It has been stuck on it for 6.5 seconds.
- Stock is still 1 and there are no orders: nothing is committed yet.

Tip: `./inspect --watch` refreshes every second, so you can see the moment the lock is released.

### Step 4: when the loser gets its answer

Back in terminal 1:

```
  +10232 ms  Buyer 2  <- 201 order #1 confirmed
  +10233 ms  Buyer 1  <- 409 SOLD_OUT
```

The loser was answered **one millisecond** after the winner. It waited 10 seconds for a "sold out".
The lock is released when the winner's transaction commits, and not a moment before.

### Step 5 and 6: the cost

```bash
./demo --strategy pessimistic --buyers 50 --stock 10
./demo --strategy naive --buyers 50 --stock 10
```

| | Confirmed | Sold out | Time taken |
|---|---|---|---|
| naive | 50 (wrong) | 0 | about 0.8 s |
| pessimistic | 10 | 40 | about 1.4 s |

Correct, and slower. The 10 winners each hold the lock for a 100 ms fraud check, one after another:
10 x 100 ms = 1 second, in a queue.

There is a second cost you cannot see in the timing. Every waiting buyer is inside a transaction,
so it is holding a **database connection** while it waits. The connection pool here has 10
connections (the HikariCP default). With 50 buyers, 10 hold a connection and 40 wait for one. If
those 10 are all queueing for the same row, requests for *other* products cannot get a connection
either. A hot row can stall the whole application.

### What to take away

- The lock is taken by the SELECT and released at commit or rollback.
- The second buyer waits at its own read, then sees the committed value.
- Cost: buyers of the same product are processed one at a time, and waiters hold connections.
- Keep the transaction short. Anything slow inside it, like this fraud check, is time the lock is
  held.

---

## Task 4: Fix It With Optimistic Locking

**The idea:** do not lock anything. Let everyone read. Put a version number on the row, and make the
write conditional: "update this row only if it is still at the version I read". If someone else got
there first, the write changes 0 rows and you know you lost.

### Before

```bash
./demo --strategy optimistic
./check 4
```

`RESULT: OVERSOLD`, and 1 of 7 checks pass.

### Step 1: give Product a version

The `product` table already has a `version` column (see `src/main/resources/schema.sql`). Map it in
`Product`:

```java
@Version
private long version;
```

Import `jakarta.persistence.Version`. No getter or setter is needed: Hibernate manages the field.

### Look before you go on

Do not write the retry yet. Run:

```bash
./demo --strategy optimistic
```

```
  +  242 ms  Buyer 2  <- 201 order #1 confirmed
  +  242 ms  Buyer 1  <- 500 (Unexpected row count (expected row count 1 but was 0) [update product set name=?,stock=?,v...)

Orders confirmed (201):    1
Version conflicts detected: 1
Server errors:             1 [500]
Stock after:               0

RESULT: OK. Stock and orders agree: 1 - 1 = 0.
```

The data is already correct: one order, stock 0. The annotation alone stopped the lost update.

But the loser got a **500**. "Expected row count 1 but was 0" is Hibernate saying: I ran my UPDATE
with `where version = 0`, and it changed nothing, because the row is at version 1 now. The data is
safe and the customer sees a crash. Half a fix.

### Step 2: retry in OrderService

In `OrderService`, replace the `OPTIMISTIC` case so it calls a retry method:

```java
public Order placeOrder(Strategy strategy, long productId, int quantity) {
    return switch (strategy) {
        case NAIVE -> placer.placeNaive(productId, quantity);
        case PESSIMISTIC -> placer.placePessimistic(productId, quantity);
        case OPTIMISTIC -> placeOptimisticWithRetry(productId, quantity);
        case ATOMIC -> placer.placeAtomic(productId, quantity);
    };
}

private Order placeOptimisticWithRetry(long productId, int quantity) {
    for (int attempt = 1; ; attempt++) {
        try {
            return placer.placeOptimistic(productId, quantity);
        } catch (OptimisticLockingFailureException e) {
            if (attempt == MAX_ATTEMPTS) {
                throw new OrderConflictException(productId, attempt);
            }
        }
    }
}
```

Import `org.springframework.dao.OptimisticLockingFailureException`.

Three things to notice:

- **The retry is in `OrderService`, not in `OrderPlacer`.** `placer.placeOptimistic(...)` is a call
  to another Spring bean, so every attempt goes through the transactional proxy and gets a brand new
  transaction. The failed transaction is rolled back and its copy of the product is stale. A retry
  has to re-read. If you put the loop inside the `@Transactional` method, every attempt would run in
  the same doomed transaction with the same stale product.
- **Only the version conflict is retried.** `SoldOutException` is not caught: trying again cannot
  put stock back.
- **It gives up.** After 3 attempts it throws `OrderConflictException`, which `ApiExceptionHandler`
  already turns into a 409. A retry loop with no limit can spin for ever under load.

`OrderPlacer.placeOptimistic` does not change at all.

### After

```bash
./demo --strategy optimistic
```

```
  +  246 ms  Buyer 2  <- 201 order #1 confirmed
  +  250 ms  Buyer 1  <- 409 SOLD_OUT

Orders confirmed (201):    1
Sold out (409):            1
Gave up after retries (409): 0
Version conflicts detected: 1
Stock after:               0
Product version:           1

RESULT: OK. Stock and orders agree: 1 - 1 = 0.
```

The loser's first attempt failed the version check. Its second attempt re-read the product, saw
`stock = 0`, and answered "sold out". A clean 409.

```bash
./check 4
```

7 of 7 pass. Run `./check` too: Task 2 must still be green.

---

## Task 5: Inspect the Optimistic Lock

### Step 1: the SQL that detects the conflict

```bash
./demo --strategy optimistic --sql
```

```
  [exec-2] select p1_0.id,p1_0.name,p1_0.stock,p1_0.version from product p1_0 where p1_0.id=?
  [exec-3] select p1_0.id,p1_0.name,p1_0.stock,p1_0.version from product p1_0 where p1_0.id=?
  [exec-3] insert into orders (created_at,product_id,quantity) values (?,?,?)
  [exec-2] insert into orders (created_at,product_id,quantity) values (?,?,?)
  [exec-2] update product set name=?,stock=?,version=? where id=? and version=?
  [exec-3] update product set name=?,stock=?,version=? where id=? and version=?
  [exec-3] select p1_0.id,p1_0.name,p1_0.stock,p1_0.version from product p1_0 where p1_0.id=?
  +  246 ms  Buyer 2  <- 201 order #1 confirmed
  +  250 ms  Buyer 1  <- 409 SOLD_OUT
```

- The SELECT is plain. No `for update`. Nobody waits to read.
- The UPDATE ends in `and version=?`, and sets a new `version`. You did not write that SQL.
  Hibernate added it because of `@Version`.

### Step 2: what the loser does next

Both threads run the UPDATE with `where version = 0`. `exec-2` gets there first: 1 row changed, the
row is now at version 1. `exec-3`'s UPDATE finds no row with `id = 1 and version = 0`: **0 rows
changed**. Hibernate turns that into an exception, the transaction rolls back (its order INSERT is
undone with it), and `OrderService` tries again.

The last line is the retry: `exec-3` runs the SELECT a second time, in a new transaction. It reads
`stock = 0` and stops.

### Step 3: version and conflicts

The summary said `Product version: 1` and `Version conflicts detected: 1`. One successful sale, one
collision.

```bash
./demo --strategy optimistic --stock 2
```

```
  +  227 ms  Buyer 2  <- 201 order #1 confirmed
  +  335 ms  Buyer 1  <- 201 order #3 confirmed

Orders confirmed (201):    2
Version conflicts detected: 1
Stock after:               0
Product version:           2
```

With 2 in stock the loser's retry succeeds. Three details:

- Buyer 1 took 335 ms and Buyer 2 took 227 ms. The loser paid for the fraud check **twice**. A
  retry redoes all the work.
- The version went up by one per sale: 2 sales, version 2.
- The order ids are #1 and #3. The loser's first INSERT got id 2 and was rolled back. Sequences
  never give a number back, which is why ids have gaps.

### Step 4: nobody waits

Terminal 1: `./demo --strategy optimistic --pause`. Terminal 2: `./inspect`.

```
SESSIONS INSIDE A TRANSACTION (blocked_by_pid = the session this one is waiting for)
  pid   state                waiting_on          blocked_by_pid  in_transaction_ms  last_statement
  ----  -------------------  ------------------  --------------  -----------------  ---------------------------------
  2688  idle in transaction  Client: ClientRead                  7136               select p1_0.id,p1_0.name,p1_0.sto
  2695  idle in transaction  Client: ClientRead                  7135               select p1_0.id,p1_0.name,p1_0.sto
```

`blocked_by_pid` is empty for both. Compare with Task 3: there, one session was stuck behind the
other. Here both read freely and both are doing their fraud check at the same time. The conflict
has not been noticed yet. It will be noticed at the UPDATE.

### Step 5: where it hurts

```bash
./demo --strategy optimistic --buyers 50 --stock 10
```

```
Orders confirmed (201):    10
Sold out (409):            25
Gave up after retries (409): 15
Version conflicts detected: 90
```

15 buyers lost three times in a row and were told to try again. 90 version conflicts means 90
transactions did all their work, including the fraud check, and threw it away.

Now give it plenty of stock:

```bash
./demo --strategy optimistic --buyers 50 --stock 1000
```

```
Orders confirmed (201):    14
Gave up after retries (409): 36
Version conflicts detected: 118
```

**1,000 in stock, 50 buyers, and only 14 got an order.** 36 were turned away from a full warehouse.
Every sale changes the version, so in each round exactly one buyer wins and everyone who read the
old version loses. With 3 attempts, most buyers lose all three.

This is the weakness of optimistic locking: it assumes conflicts are rare. On a row that everybody
writes to, they are the normal case.

### Step 6: more attempts

Set `MAX_ATTEMPTS = 10` in `OrderService` and repeat. More buyers succeed, the run takes longer, and
the number of conflicts goes up: each retry is another transaction's worth of wasted work. Raising
the limit trades the database's time for a higher success rate. Put it back to 3 (a unit test
depends on it).

### What to take away

- Nothing is locked between the read and the write. Readers never wait.
- The conflict is detected by `UPDATE ... WHERE version = ?` changing 0 rows.
- The loser must retry in a new transaction and re-read. Each retry redoes all the work.
- Works well when conflicts are rare. Gets worse the more writers want the same row.

---

## Task 6: Fix It With One Atomic UPDATE

**The idea:** the bug lives in the gap between the READ and the WRITE. For a rule as simple as
"there must be enough stock", you can remove the READ. Ask the database to check and subtract in one
statement.

### Step 1: the query

In `ProductRepository`:

```java
@Modifying
@Query("""
        update Product p
           set p.stock = p.stock - :quantity,
               p.version = p.version + 1
         where p.id = :id
           and p.stock >= :quantity
        """)
int decrementStockIfAvailable(@Param("id") long id, @Param("quantity") int quantity);
```

It returns the number of rows changed: 1 if the stock was there, 0 if not.

`p.version = p.version + 1` keeps the optimistic strategy honest: a hand-written UPDATE bypasses
`@Version`, so you bump the version yourself. (If you skipped Task 4 and `Product` has no version
field yet, leave that line out.)

### Step 2: use it

Replace the body of `OrderPlacer.placeAtomic`:

```java
@Transactional
public Order placeAtomic(long productId, int quantity) {
    fraudCheck.review(productId, quantity);
    int rowsChanged = products.decrementStockIfAvailable(productId, quantity);
    if (rowsChanged == 0) {
        if (!products.existsById(productId)) {
            throw new ProductNotFoundException(productId);
        }
        throw new SoldOutException(productId);
    }
    return orders.save(new Order(productId, quantity));
}
```

0 rows can mean two things: the product does not exist, or there is not enough stock. The
`existsById` check tells them apart, and only runs on the unhappy path.

The fraud check moved **before** the UPDATE. The UPDATE locks the row until the transaction ends. If
the fraud check came after it, the row would be locked for the whole check, and you would have
rebuilt the pessimistic lock.

### Why this is safe

`stock - :quantity` is now computed by the database, at the moment of writing, from the current
value of the row. When two UPDATEs hit the same row, the second one waits for the first to commit,
then **re-checks its WHERE clause against the new row**. Stock is 0, `0 >= 1` is false, 0 rows
changed.

### After

```bash
./demo --strategy atomic --sql
```

```
  [exec-2] update product p1_0 set stock=(p1_0.stock-?),version=(p1_0.version+1) where p1_0.id=? and p1_0.stock>=?
  [exec-3] update product p1_0 set stock=(p1_0.stock-?),version=(p1_0.version+1) where p1_0.id=? and p1_0.stock>=?
  [exec-2] insert into orders (created_at,product_id,quantity) values (?,?,?)
  +  251 ms  Buyer 1  <- 201 order #1 confirmed
  [exec-3] select count(*) from product p1_0 where p1_0.id=?
  +  269 ms  Buyer 2  <- 409 SOLD_OUT

RESULT: OK. Stock and orders agree: 1 - 1 = 0.
```

No SELECT of the stock at all. `exec-3`'s UPDATE changed 0 rows, so it ran the `existsById` check
(`select count(*)`) and answered "sold out".

```bash
./check 6
./check
```

`./check` should now print 18 passes and no failures.

---

## Task 7: Compare All Three Under Load

### Step 1: 50 buyers, 10 items

```bash
./demo --strategy naive --buyers 50 --stock 10
./demo --strategy pessimistic --buyers 50 --stock 10
./demo --strategy optimistic --buyers 50 --stock 10
./demo --strategy atomic --buyers 50 --stock 10
```

Numbers from one run on a laptop. Yours will differ, the shape will not.

| Strategy | Confirmed | Sold out | Gave up | Time | Correct? |
|---|---|---|---|---|---|
| naive | 50 | 0 | 0 | 0.8 s | No |
| pessimistic | 10 | 40 | 0 | 1.4 s | Yes |
| optimistic | 10 | 25 | 15 | 1.3 s | Yes |
| atomic | 10 | 40 | 0 | 0.8 s | Yes |

### Step 2: 50 buyers, 1,000 items

Nobody is fighting over the last item now. Everyone *could* be served.

```bash
./demo --strategy pessimistic --buyers 50 --stock 1000
./demo --strategy optimistic --buyers 50 --stock 1000
./demo --strategy atomic --buyers 50 --stock 1000
```

| Strategy | Confirmed | Gave up | Time |
|---|---|---|---|
| pessimistic | 50 | 0 | 7.5 s |
| optimistic | 14 | 36 | 2.0 s |
| atomic | 50 | 0 | 0.8 s |

- **Pessimistic** served everyone, one at a time: 50 x (100 ms fraud check + overhead) is about
  7.5 seconds. Correct and slow.
- **Optimistic** was faster and turned away 36 of 50 buyers while 986 items were on the shelf.
- **Atomic** served everyone in the time of the naive version.

### Step 3: how long is the row locked?

- Pessimistic: from the SELECT to the commit. That includes the fraud check: 100 ms or more.
- Optimistic: only during the UPDATE and commit at the end.
- Atomic: only during the UPDATE and commit at the end, and it never has to retry.

### Step 4: a new rule

"A customer may buy at most 2 PS5s across all their orders."

That rule needs you to read the customer's existing orders and decide. It does not fit in the WHERE
clause of an UPDATE on `product`. The atomic update can still protect the stock, but not this rule.
You need to read, decide and write, which means a pessimistic lock (on the customer row, say) or an
optimistic version check. The fastest fix is also the first one to stop working when the rule grows.

### When to use which

| | Pessimistic | Optimistic | Atomic update |
|---|---|---|---|
| Who waits | Everyone queues at the read | Nobody | Only for the one statement |
| What the loser gets | The fresh value, after waiting | A failure at commit, then a retry | 0 rows changed |
| Cost | Locks and connections held for the whole transaction | Wasted work when conflicts are common | None |
| Limit | Risk of deadlocks and long queues | Buyers turned away on a hot row | Only fits a rule you can write in one WHERE |
| Use it when | Conflicts are common, or redoing the work is expensive (last seats on a flight, a bank transfer) | Conflicts are rare and reads far outnumber writes (editing a profile, a document) | The rule is a simple condition on the row itself (stock, balance, a counter) |

### How to say it in an interview

> "If two requests read the same row and then both write a value they computed from that read, the
> second write overwrites the first. That is a lost update, and the default isolation level allows
> it. There are three fixes. Pessimistic locking takes a row lock at read time with SELECT FOR
> UPDATE, so the second request waits and then sees the committed value. Optimistic locking takes no
> lock: the row has a version, the UPDATE has `WHERE version = ?`, and the loser gets zero rows
> updated and retries in a new transaction. And if the rule is simple enough, I would skip the read
> and do one conditional UPDATE. I would use pessimistic when conflicts are common, optimistic when
> they are rare, and the single UPDATE whenever the rule fits in a WHERE clause."

Then be ready for the follow-ups in "Questions to Ask Afterwards" at the end of
[`INTERVIEW_TASKS.md`](INTERVIEW_TASKS.md).

---

## Clean up

```bash
docker compose down
```
