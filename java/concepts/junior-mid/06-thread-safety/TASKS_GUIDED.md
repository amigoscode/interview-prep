# Guided Tasks — Thread Safety in Spring Beans

The same ten tasks as [`INTERVIEW_TASKS.md`](INTERVIEW_TASKS.md), step by step: the command to
run, the output you should see, why it happens, and the change to make.

Commands are for macOS and Linux. On Windows type `.\demo`, `.\inspect` and `.\check`.

All the output below was copied from real runs. **The exact numbers differ per run and per
machine**: how many visits are lost, which customer gets whose receipt, the timings, the thread
names and the `@1a2b3c` object ids. The `RESULT` line and the pass counts should match.

**Before you start**

```bash
cd start
```

No Docker, no database. Everything this project needs is the JDK.

---

## Task 1: See the Lost Visits

No code in this task. You are collecting evidence.

### Run it

```bash
./demo
```

### What you should see

```
Scenario: counter | requests: 1,000 | in flight at the same time: 64
Every request is POST /api/visits. Each one calls VisitCounter.hit() once: one more visit.

Requests sent:                     1,000
Answered 200 OK:                   1,000
Counter says:                      934
Lost:                              66
Time taken:                        204 ms

RESULT: LOST UPDATES. 1,000 requests were answered 200 OK, but the counter says 934. 66 visits are missing, and nothing failed.
```

1,000 requests, 1,000 times "200 OK", and 66 visits are gone. Run it again: a different number is
lost each time. That is the first sign of a thread-safety bug: the result is wrong, and it is wrong
in a different way on every run.

(If your machine prints `RESULT: OK`, send more: `./demo --requests 20000`.)

### One request at a time

```bash
./demo --threads 1
```

```
Scenario: counter | requests: 1,000 | in flight at the same time: 1

Counter says:                      1,000
Lost:                              0

RESULT: OK. 1,000 requests, and the counter says 1,000.
```

Same code, same 1,000 requests, nothing lost. The code is only wrong when two requests are inside
it at the same time.

### More requests

```bash
./demo --requests 20000
```

```
Counter says:                      19,077
Lost:                              923
```

### Find it in the code

Open `VisitCounter`:

```java
@Service
public class VisitCounter {

    private int count;

    public void hit() {
        count++;
    }
```

Two facts make this a bug.

**Fact 1: there is one `VisitCounter`.** `@Service` makes it a Spring bean, and the default scope
of a bean is **singleton**: Spring creates one object and gives that same object to everybody.
Tomcat runs every request on its own thread (up to 200 at a time by default). So all those threads
call `hit()` on the same object, and they all read and write the same `count` field.

**Fact 2: `count++` is not one step.** It is three:

1. READ the value of `count`
2. ADD 1 to it
3. WRITE the result back to `count`

A thread can be interrupted between any two of them, and on a machine with several cores two
threads really do run them at the same instant.

| Time | Thread A | Thread B | `count` |
|---|---|---|---|
| 1 | reads `count`: **41** | | 41 |
| 2 | | reads `count`: **41** | 41 |
| 3 | adds 1: 42 | adds 1: 42 | 41 |
| 4 | writes **42** | | 42 |
| 5 | | writes **42** | 42 |

Two visits, and the counter went up by one. This is a **lost update**. It is the same anomaly as in
the [Locking](../07-locking/) project, with a field in memory instead of a row in a database.

### Why nothing failed

Nothing went wrong from Java's point of view. Each thread read an `int`, added 1 and wrote an
`int`. There is no exception to throw and no error to log. Every request got its 200 OK. The only
symptom is a number that is too low.

### Why the tests did not catch it

```bash
./check
```

Look at the first block:

```
Baseline: one thread, one request at a time (passes from the start, must stay green)
  [PASS] One thread: page views are counted per page
  [PASS] One thread: a shipping label reads 'Ships on 05 Mar 2026'
  [PASS] One thread: a receipt shows the customer who paid, the amount and the points
  [PASS] One thread: 1,000 visits are counted as 1,000
  [PASS] One thread: three sales of 5.00 give 3 orders and 15.00 revenue
  [PASS] One thread: a price is fetched from the supplier once, then served from the cache
  [PASS] HTTP: GET /api/labels?shipDate=2026-03-05 returns 'Ships on 05 Mar 2026'
  ...
  12 of 12 pass
```

Twelve tests, all green, on a project with six thread-safety bugs. They all use one thread. Every
bean in this project is correct when one thread uses it. That is why these bugs reach production:
the normal kind of test cannot see them.

Further down, 11 checks fail. Those are the ones that use several threads at the same moment. The
last line of each block adds up to 18 of 29.

### Check

```bash
./check 1
```

```
Task 1: See the lost visits
  [PASS] Scope: 8 requests at the same moment are served by ONE controller and ONE VisitCounter (this is why fields are shared)
  [PASS] Scope: Spring builds one VisitCounter and hands the same object to everyone who asks
  2 of 2 pass
```

These tests pass on purpose. They pin down the cause: one object, many threads.

---

## Task 2: Fix the Counter

**The idea:** make read-add-write one step that no other thread can get into the middle of.

### Before

```bash
./check 2
```

```
Task 2: Fix the counter
  [FAIL] Counter: the VisitCounter bean, shared by 8 threads, counts all 800,000 visits and GET /api/visits reports them
           800,000 visits were recorded, but the counter says 332,739. 467,261 visits were lost.
  [FAIL] Counter: 8 threads x 100,000 visits = 800,000. Every visit is counted
           800,000 visits were recorded, but the counter says 336,950. 463,050 visits were lost.
  0 of 2 pass
```

More than half of the visits are lost when the threads do nothing but count.

### The change

In `VisitCounter`, replace the `int` with an `AtomicInteger`:

```java
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class VisitCounter {

    private final AtomicInteger count = new AtomicInteger();

    public void hit() {
        count.incrementAndGet();
    }

    public int count() {
        return count.get();
    }

    public void reset() {
        count.set(0);
    }
}
```

Two things changed:

- `incrementAndGet()` does the read, the add and the write as **one atomic operation**. No other
  thread can see or change the value in the middle.
- The field is now `final`. The reference never changes: it always points at the same
  `AtomicInteger`. Only the number inside it changes, and it changes safely.

### After

```bash
./demo
```

```
Counter says:                      1,000
Lost:                              0

RESULT: OK. 1,000 requests, and the counter says 1,000.
```

```bash
./check 2
```

```
Task 2: Fix the counter
  [PASS] Counter: the VisitCounter bean, shared by 8 threads, counts all 800,000 visits and GET /api/visits reports them
  [PASS] Counter: 8 threads x 100,000 visits = 800,000. Every visit is counted
  2 of 2 pass
```

### A note on these tests

There is no place inside `count++` where a test could pause one thread and let another one through,
so this test cannot force the bad interleaving the way the tests in Task 4 and Task 6 do. It uses
numbers instead: 8 threads that start at the same moment and make 100,000 calls each, repeated 5
times. On the broken code that loses hundreds of thousands of visits, every time.

---

## Task 3: Inspect the Counter

### Step 1: what is shared?

```bash
./inspect
```

The first table lists every bean with a verdict for each field. These are the rows that matter
now (the rest is left out here):

```
BEANS: every bean of this application, its scope, and what each field means for thread safety

  bean                  scope      field            type                     verdict  why
  --------------------  ---------  ---------------  -----------------------  -------  ---------------------------------------------------------------------------
  volatileVisitCounter  singleton  count            volatile int             MUTABLE  not final. volatile makes writes visible, it does not make ++ one step
  visitCounter          singleton  count            AtomicInteger            CAREFUL  thread-safe type. One call is atomic, two calls in a row are not
```

Before your fix the `visitCounter` row said:

```
  visitCounter          singleton  count            int                      MUTABLE  not final: every request thread can write it
```

`MUTABLE` means: a request thread can change this while another thread is using it. A field that
is not `final` is the first thing to look for.

Now it says `CAREFUL`, not `OK`. An `AtomicInteger` is still shared state that changes. It is safe
as long as every update is **one call**. Two calls in a row (`if (count.get() < 10) count.incrementAndGet()`)
would be a race again. You will meet that in Task 4.

### Step 2: who serves the requests?

The second table in the same output:

```
THREADS: 8 requests to GET /api/whoami at the same moment. Who served each one?

  request  thread  controller (singleton)     service (singleton)   request-scoped bean  prototype bean
  -------  ------  -------------------------  --------------------  -------------------  ------------------
  1        exec-1  WhoAmIController@2ffc11f9  VisitCounter@f8ceee9  RequestInfo #3       PrototypeHelper #1
  2        exec-4  WhoAmIController@2ffc11f9  VisitCounter@f8ceee9  RequestInfo #6       PrototypeHelper #1
  3        exec-5  WhoAmIController@2ffc11f9  VisitCounter@f8ceee9  RequestInfo #8       PrototypeHelper #1
  4        exec-2  WhoAmIController@2ffc11f9  VisitCounter@f8ceee9  RequestInfo #1       PrototypeHelper #1
  5        exec-8  WhoAmIController@2ffc11f9  VisitCounter@f8ceee9  RequestInfo #4       PrototypeHelper #1
  6        exec-3  WhoAmIController@2ffc11f9  VisitCounter@f8ceee9  RequestInfo #7       PrototypeHelper #1
  7        exec-6  WhoAmIController@2ffc11f9  VisitCounter@f8ceee9  RequestInfo #2       PrototypeHelper #1
  8        exec-7  WhoAmIController@2ffc11f9  VisitCounter@f8ceee9  RequestInfo #5       PrototypeHelper #1

  8 requests were served by 8 different threads.
  They all used 1 WhoAmIController and 1 VisitCounter: the fields of a singleton are shared by every thread.
```

Eight different threads (`exec-1` to `exec-8` are Tomcat's request threads). One
`WhoAmIController@2ffc11f9`, one `VisitCounter@f8ceee9`: the number after the `@` identifies the
object, and it is the same in every row. This is the picture to keep in your head for the rest of
the project. (The last two columns are for Task 7.)

### Step 3: "just make it volatile"

Open `VolatileVisitCounter`. It is the same as the broken counter with one extra word:

```java
private volatile int count;

public void hit() {
    count++;
}
```

```bash
./demo --scenario volatile
```

```
Scenario: volatile | requests: 1,000 | in flight at the same time: 64
Every request is POST /api/visits/volatile. Each one calls VolatileVisitCounter.hit() once: one more visit.

Requests sent:                     1,000
Answered 200 OK:                   1,000
Counter says:                      936
Lost:                              64
Time taken:                        195 ms

RESULT: LOST UPDATES. 1,000 requests were answered 200 OK, but the counter says 936. 64 visits are missing, and nothing failed.
```

Just as broken.

### Step 4: what volatile does and does not do

There are two separate problems with shared state:

- **Visibility**: when thread A writes a value, does thread B see it the next time it reads?
  Without help, not always: a thread may keep working with an old copy.
- **Atomicity**: can several steps happen as one, with no other thread in between?

`volatile` solves visibility only. Every read of a `volatile` field sees the latest write. But
`count++` is still READ, ADD, WRITE, and two threads can still both READ 41. They each see the
*latest* value. It is the same value, and they both write 42.

| | Visibility | Atomicity of `count++` |
|---|---|---|
| plain `int` | no | no |
| `volatile int` | yes | **no** |
| `AtomicInteger` | yes | yes |
| `synchronized` block | yes | yes |

### Step 5: what volatile is for

A value that one thread writes and others only read, where the new value does not depend on the
old one. The classic case is a flag:

```java
private volatile boolean shuttingDown;

public void stop() { shuttingDown = true; }        // one plain write
public boolean accepts() { return !shuttingDown; } // one plain read
```

No read-then-write, so there is nothing to get in between.

### Step 6: how AtomicInteger does it, and LongAdder

`incrementAndGet()` does not take a lock. It uses **compare-and-set**, a single CPU instruction:
"write 42, but only if the value is still 41". In Java terms it does this:

```java
int current;
do {
    current = get();                                  // READ
} while (!compareAndSet(current, current + 1));       // WRITE only if nobody changed it, else try again
```

If another thread got there first, the write fails and the loop reads again. It is optimistic
locking, done by the processor.

`AtomicLong` is the same for `long`. `LongAdder` is for a counter that very many threads increment
all the time and that is read rarely, such as a metrics counter. It keeps several cells and adds
them up when you ask for the sum, so threads do not all fight over one value. For a visit counter
`AtomicInteger` is fine.

### Check

```bash
./check 3
```

```
Task 3: Inspect the counter
  [PASS] Volatile: 8 threads x 100,000 visits on a volatile int, and visits are still lost (this documents the bug)
  1 of 1 pass
```

### What to take away

- One bean object, many threads. A field that changes is shared between all requests.
- `count++` is three steps. `volatile` does not change that.
- `AtomicInteger` makes one operation on one variable atomic, without a lock.

---

## Task 4: Fix the Maps

Two beans, three steps. Do them in order and look in between.

### Before

```bash
./demo --scenario pages
```

```
Scenario: pages | requests: 5,000 | in flight at the same time: 64
Request 1 views page-1, request 2 views page-2, and so on: POST /api/pages/page-N/views.
Every page is viewed exactly once, so the map should end up with one entry per request.

Requests sent:                     5,000
Answered 200 OK:                   5,000
Pages the map says it holds:       4,713   (size())
Pages you can actually look up:    4,998
Pages that vanished:               2   (for example page-2126)
Time taken:                        828 ms

RESULT: LOST ENTRIES. 2 of 5,000 pages were answered 200 OK and are not in the map.
RESULT: CORRUPTED MAP. size() says 4,713, but 4,998 pages can be looked up.
```

```bash
./demo --scenario hot-page
```

```
Scenario: hot-page | requests: 1,000 | in flight at the same time: 64
Every request views the same page: POST /api/pages/home/views.

Views of 'home':                   899
Lost:                              101

RESULT: LOST UPDATES. 1,000 requests viewed 'home', but the map says 899. 101 views are missing.
```

```bash
./demo --scenario price-cache
```

```
Scenario: price-cache | requests: 50 | in flight at the same time: 64
Every request is GET /api/prices/ps5. The price is cached, so the slow supplier
(50 ms per call) should be asked exactly once.

Requests sent:                     50
Answered 200 OK:                   50
Calls to the supplier:             50
Calls needed:                      1
Time taken:                        78 ms

RESULT: DUPLICATE WORK. 50 requests for the same price made 50 calls to the supplier. 49 of them were wasted.
```

```bash
./check 4
```

```
Task 4: Fix the maps
  [FAIL] Price cache: 12 requests for 3 products arrive together. The supplier is called 3 times, once per product
           12 requests for 3 products made 12 calls to the supplier. Expected 3.
  [FAIL] Price cache: 4 requests for the same product arrive together. The supplier is called once, and all 4 get the price
           4 requests for the price of 'ps5' made 4 calls to the supplier. Expected 1.
  [FAIL] Pages: 4 threads view 1,000 different pages each. All 4,000 pages are in the map, with 1 view each
           154 of 4,000 pages were viewed once and are missing from the map.
  [FAIL] Pages: 8 threads view the same page 50,000 times each. The page has exactly 400,000 views
           'home' was viewed 400,000 times, but the map says 96,549. 303,451 views were lost.
  0 of 4 pass
```

### Step 1: a map that survives several threads

Open `PageViewStats`:

```java
private final Map<String, Integer> viewsByPage = new HashMap<>();
```

The field is `final`. That does not help: `final` protects the **reference**, so nobody can point
the field at another map. The `HashMap` object behind it is as changeable as ever, and `HashMap` is
not built for several threads. Its Javadoc says so: "this implementation is not synchronized".

Change the type:

```java
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

private final Map<String, Integer> viewsByPage = new ConcurrentHashMap<>();
```

(Remove the `import java.util.HashMap;` line.) Change nothing else yet.

### Look before you go on

```bash
./demo --scenario pages
```

```
Pages the map says it holds:       5,000   (size())
Pages you can actually look up:    5,000
Pages that vanished:               0

RESULT: OK. 5,000 pages viewed, 5,000 pages in the map.
```

Fixed. No page is lost and `size()` is right.

```bash
./demo --scenario hot-page
```

```
Views of 'home':                   923
Lost:                              77

RESULT: LOST UPDATES. 1,000 requests viewed 'home', but the map says 923. 77 views are missing.
```

**Not fixed.** The map is thread-safe now and the counts are still wrong.

```bash
./check 4
```

```
  [PASS] Pages: 4 threads view 1,000 different pages each. All 4,000 pages are in the map, with 1 view each
  [FAIL] Pages: 8 threads view the same page 50,000 times each. The page has exactly 400,000 views
           'home' was viewed 400,000 times, but the map says 74,781. 325,219 views were lost.
  1 of 4 pass
```

This is the most important moment in the project. Look at `record`:

```java
Integer views = viewsByPage.get(page);                    // READ
if (views == null) {                                      // CHECK
    viewsByPage.put(page, 1);                             // WRITE
} else {
    viewsByPage.put(page, views + 1);                     // WRITE a value computed from a stale read
}
```

`ConcurrentHashMap` guarantees that each **call** is safe: `get` is atomic, `put` is atomic. It
guarantees nothing about what happens **between** two calls. Two threads both `get` 41, both
compute 42, both `put` 42. READ, ADD, WRITE: it is `count++` again, spread over two method calls.

This pattern has a name: **check-then-act** (or read-modify-write). A thread-safe collection does
not fix it.

### Step 2: read and write in one call

`ConcurrentHashMap` has methods that do the whole read-modify-write as one atomic operation.
For "add 1, or start at 1" it is `merge`:

```java
public void record(String page) {
    viewsByPage.merge(page, 1, Integer::sum);
}
```

Read it as: "put 1 for this page. If there is already a value, combine the old value and 1 with
`Integer::sum`." No other thread can touch that key while it runs.

```bash
./demo --scenario hot-page
```

```
Views of 'home':                   1,000
Lost:                              0

RESULT: OK. 1,000 requests, 1,000 views.
```

`./check 4` now says 2 of 4.

### Step 3: the cache

Open `PriceCache`. It was a `ConcurrentHashMap` from the start:

```java
public BigDecimal priceOf(String product) {
    if (!prices.containsKey(product)) {                       // CHECK
        prices.put(product, source.fetchPrice(product));      // ACT (slow)
    }
    return prices.get(product);
}
```

Check-then-act again, and this time the gap between the check and the act is a 50 ms call to the
supplier. 50 requests arrive together, all 50 see "not there", all 50 fetch.

The atomic method for "get it, or compute it if it is missing" is `computeIfAbsent`:

```java
public BigDecimal priceOf(String product) {
    return prices.computeIfAbsent(product, source::fetchPrice);
}
```

The first thread to ask for `ps5` runs `fetchPrice`. Every other thread that asks for `ps5` in the
meantime **waits** inside `computeIfAbsent`, and then gets the value the first thread stored. The
function runs at most once per key.

### After

```bash
./demo --scenario price-cache
```

```
Calls to the supplier:             1
Calls needed:                      1
Time taken:                        83 ms

RESULT: OK. 50 requests, 1 call to the supplier, 83 ms.
```

```bash
./check 4
```

```
Task 4: Fix the maps
  [PASS] Price cache: 12 requests for 3 products arrive together. The supplier is called 3 times, once per product
  [PASS] Price cache: 4 requests for the same product arrive together. The supplier is called once, and all 4 get the price
  [PASS] Pages: 4 threads view 1,000 different pages each. All 4,000 pages are in the map, with 1 view each
  [PASS] Pages: 8 threads view the same page 50,000 times each. The page has exactly 400,000 views
  4 of 4 pass
```

### A note on the price cache tests

Open `PriceCacheIT` and `support/Meeting`. These tests do not rely on luck. The supplier is
replaced by a stub, and the stub makes every thread that calls it wait until all the others have
called it too. With check-then-act code all four threads pass the check and all four arrive at the
supplier: four calls, every time. With `computeIfAbsent` only one thread gets there. The stub sees
that the other three are blocked and lets it carry on.

---

## Task 5: Inspect the Maps

### Step 1: why size() and the entries disagreed

In the `HashMap` run, `size()` said 4,713 and 4,998 pages could be looked up. A `HashMap` keeps its
size in a plain `int` field and updates it with `++size`. You know what happens to `++` on a shared
field. So the map lost count of its own entries, on top of losing two of them.

### Step 2: what can happen to a HashMap under several threads

1. **Lost entries.** Two threads put into the same bucket, or one puts while another is growing the
   table, and one entry is overwritten.
2. **A wrong size.** As above.
3. **Exceptions and endless loops.** The internal structure can be left broken. This is not theory.
   While this project was being written, the same test was run harder (8 threads, 5,000 pages
   each, 150 times). 14 of those runs ended with a thread stuck for ever, and 7 with an exception:

   ```
   racer-1 did not finish within 20 seconds. It is stuck in java.base/java.util.HashMap$TreeNode.balanceInsertion(HashMap.java:2399)
   racer-2 did not finish within 20 seconds. It is stuck in java.base/java.util.HashMap$TreeNode.find(HashMap.java:2041)
   java.lang.ClassCastException: class java.util.HashMap$Node cannot be cast to class java.util.HashMap$TreeNode
   ```

   A request thread that is stuck like this never returns. It burns a whole CPU core until the
   application is restarted.

If `./check 4` on the `HashMap` ever takes 20 seconds and prints "did not finish", or
`./demo --scenario pages` prints `RESULT: STUCK THREADS`, you have just seen number 3.

### Step 3: what ./inspect cannot see

```bash
./inspect
```

```
  pageViewStats         singleton  viewsByPage      Map (ConcurrentHashMap)  CAREFUL  thread-safe type. One call is atomic, two calls in a row are not
  priceCache            singleton  prices           Map (ConcurrentHashMap)  CAREFUL  thread-safe type. One call is atomic, two calls in a row are not
```

`priceCache` had exactly this line in `start/`, when it was broken. The report reads the **types**
of the fields. It cannot read your methods, so it cannot tell `computeIfAbsent` from
`containsKey` + `put`. That is why the verdict is `CAREFUL` and not `OK`, and why the last line of
the report says "It cannot see a check-then-act". A code reviewer has the same problem: the field
looks right.

### Step 4: the same three steps

`map.put(k, map.get(k) + 1)` is: READ (`get`), ADD (`+ 1`), WRITE (`put`). Exactly `count++` from
Task 1.

| Check-then-act | Atomic replacement |
|---|---|
| `map.put(k, map.get(k) + 1)` | `map.merge(k, 1, Integer::sum)` |
| `if (!map.containsKey(k)) map.put(k, v)` | `map.putIfAbsent(k, v)` |
| `if (!map.containsKey(k)) map.put(k, load(k))` | `map.computeIfAbsent(k, this::load)` |
| `if (map.get(k) != null) map.put(k, f(map.get(k)))` | `map.computeIfPresent(k, (key, old) -> f(old))` |
| anything else that reads then writes one key | `map.compute(k, (key, old) -> ...)` |

`Collections.synchronizedMap(new HashMap<>())` and `Hashtable` would not have fixed the counts
either. They lock the whole map for each call, which makes each call safe, and they leave the same
gap between two calls.

### Step 5: what the other 49 were doing

They were blocked inside `computeIfAbsent`, waiting for the first thread's `fetchPrice` to return.
Then each got the stored value. That is why the run took about as long as one supplier call
(83 ms) and not 50 of them.

### Step 6: what computeIfAbsent locks

```bash
./demo --scenario price-cache --requests 20 --products 20
```

```
Scenario: price-cache | requests: 20 | in flight at the same time: 64
The requests ask for 20 different products: GET /api/prices/product-N.
Prices are cached, so the slow supplier (50 ms per call) should be asked once per product.

Requests sent:                     20
Answered 200 OK:                   20
Calls to the supplier:             20
Calls needed:                      20
Time taken:                        127 ms

RESULT: OK. 20 requests, 20 calls to the supplier, 127 ms.
```

20 calls of 50 ms each took 127 ms, not 1,000 ms. So the 20 fetches ran mostly **at the same
time**. `computeIfAbsent` does not lock the map. It locks one **bucket** of the map: the small part
where that key lives. Different products are usually in different buckets and do not wait for each
other.

It is 127 ms and not 50 ms because a new `ConcurrentHashMap` has only 16 buckets. With 20 products
a few of them share a bucket, and those wait for each other.

### Step 7: "short and simple"

The Javadoc of `computeIfAbsent` says: "Some attempted update operations on this map by other
threads may be blocked while computation is in progress, so the computation should be short and
simple." Ours is a 50 ms network call. That is acceptable in a teaching project and worth a remark
in a real one: if the supplier hangs, every request for a product in the same bucket hangs with it.
In production you would use a cache library that is built for slow loads and gives you expiry as
well (Caffeine, or Spring's `@Cacheable(sync = true)` on top of it).

### What to take away

- `HashMap` in a singleton is a bug. Not "can be slow": wrong results, exceptions, stuck threads.
- `ConcurrentHashMap` makes each call atomic. It does not make two calls atomic.
- Check-then-act on a thread-safe collection is still a race. Use the one-call methods: `merge`,
  `computeIfAbsent`, `putIfAbsent`, `compute`.

---

## Task 6: Fix the Beans That Remember a Request

### Before: the receipt

```bash
./demo --scenario receipt
```

```
Scenario: receipt | requests: 100 | in flight at the same time: 64
Customer N asks for their own receipt: GET /api/receipts?customer=customer-N&amountCents=...
Every customer pays a different amount.

Requests sent:                     100
Answered 200 OK:                   100
Got their own receipt:             1
Got someone else's receipt:        99
    customer-1 paid 1001 and got the receipt of customer-30 for 1030
    customer-2 paid 1002 and got the receipt of customer-30 for 1030
    customer-3 paid 1003 and got the receipt of customer-30 for 1030
Time taken:                        86 ms

RESULT: DATA LEAK. 99 of 100 customers were shown another customer's receipt. Every answer was 200 OK.
```

This is the most damaging bug in the project. A lost visit is a wrong number. This is one customer
looking at another customer's data.

Now the same thing one request at a time:

```bash
./demo --scenario receipt --threads 1 --requests 10
```

```
Got their own receipt:             10
Got someone else's receipt:        0

RESULT: OK. Every customer got their own receipt.
```

Perfect. This is what the developer saw on their own machine, and what every test saw.

### Why

Open `ReceiptService`:

```java
private final LoyaltyClient loyalty;

private String customer;
private int amountCents;
private int points;

public Receipt receiptFor(String customer, int amountCents) {
    this.customer = customer;
    this.amountCents = amountCents;
    this.points = loyalty.pointsFor(customer, amountCents);   // slow: other requests arrive meanwhile
    return buildReceipt();
}

private Receipt buildReceipt() {
    return new Receipt(customer, amountCents, points);
}
```

It reads naturally. The fields are a convenient way to share data between `receiptFor` and
`buildReceipt` without passing parameters around. In a class you create with `new` for each
request, this is fine.

But this is a singleton. There is **one** `customer` field for the whole application.

| Time | Alice's request | Bob's request | `customer` field |
|---|---|---|---|
| 1 | `this.customer = "alice"` | | alice |
| 2 | calls the loyalty service (20 ms) | | alice |
| 3 | | `this.customer = "bob"` | **bob** |
| 4 | | calls the loyalty service (20 ms) | bob |
| 5 | `buildReceipt()` reads `customer` | | bob |
| 6 | **returns Bob's receipt to Alice** | | bob |

### The change: ReceiptService

Data that belongs to one request goes in **parameters and local variables**:

```java
@Service
public class ReceiptService {

    private final LoyaltyClient loyalty;

    public ReceiptService(LoyaltyClient loyalty) {
        this.loyalty = loyalty;
    }

    public Receipt receiptFor(String customer, int amountCents) {
        int points = loyalty.pointsFor(customer, amountCents);
        return buildReceipt(customer, amountCents, points);
    }

    private Receipt buildReceipt(String customer, int amountCents, int points) {
        return new Receipt(customer, amountCents, points);
    }
}
```

The three fields are gone. `customer`, `amountCents` and `points` now live on the **stack** of the
thread that is running the method. Every thread has its own stack, and no thread can see another
thread's local variables. There is nothing left to share, so there is nothing left to protect.

The one field that remains, `loyalty`, is `final` and points at another bean. That is a
**stateless** bean, and it is the normal shape of a Spring service.

```bash
./demo --scenario receipt
```

```
Got their own receipt:             100
Got someone else's receipt:        0

RESULT: OK. Every customer got their own receipt.
```

### Why not just add `synchronized`?

`public synchronized Receipt receiptFor(...)` would also make the behaviour tests pass. It would
also mean that the shop can produce one receipt at a time, and that every waiting customer waits
for somebody else's 20 ms loyalty lookup. You would be locking to protect state that should not
exist. One of the Task 6 checks reads the class with reflection and fails if any field is not
`final`, to point you at the real fix.

### Before: the labels

```bash
./demo --scenario labels
```

```
Scenario: labels | requests: 2,000 | in flight at the same time: 64
Request N asks for a shipping label: GET /api/labels?shipDate=<a date>.
The dates differ from request to request. The label must show the date that was asked for.

Requests sent:                     2,000
Answered 200 OK:                   2,000
Labels with the right date:        1,808
Labels with the WRONG date:        192
    asked for 2026-01-10, label says "Ships on 24 Jul 2028"
    asked for 2026-02-05, label says "Ships on 16 Feb 2026"
    asked for 2026-02-11, label says "Ships on 11 Mar 2026"
Time taken:                        709 ms

RESULT: WRONG DATES. 192 of 2,000 labels were answered 200 OK with a date nobody asked for.
```

Look at the examples. Some are another request's date. Some are a mix: the day from one request
and the month from another.

### Why

Open `ShippingLabels`:

```java
private final SimpleDateFormat format = new SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH);

public String labelFor(LocalDate shipDate) {
    Date date = Date.from(shipDate.atStartOfDay(ZoneOffset.UTC).toInstant());
    return "Ships on " + format.format(date);
}
```

There is no request data in a field here. The field is `final`. And it is still broken, because of
what `SimpleDateFormat` does **inside**: `format(date)` first stores the date in an internal
`Calendar` object, then reads the day, the month and the year back out of it, one after the other.
That `Calendar` is a field of the `SimpleDateFormat`. So it is the receipt bug again, one level
down, inside a JDK class: thread A stores its date, thread B stores another, thread A reads B's
month. Its Javadoc says: "Date formats are not synchronized."

Under heavier load it also throws. This is from `./check 6` on the broken code:

```
  [FAIL] Labels: 8 threads print labels for 8 different dates, 20,000 each. Every label shows its own date
           Of 160,000 labels, 91,118 show a date that was not asked for and 220 threw an exception. For example: asked for 2033-08-17, got "Ships on 17 May 2033"
```

(The exception is `ArrayIndexOutOfBoundsException` from deep inside the calendar code.)

### The change: ShippingLabels

`DateTimeFormatter` from `java.time` is **immutable**: once built, nothing in it ever changes, and
it keeps nothing between calls. An immutable object can be shared by any number of threads.

```java
package com.amigoscode.threadsafety.shipping;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class ShippingLabels {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    public String labelFor(LocalDate shipDate) {
        return "Ships on " + FORMAT.format(shipDate);
    }
}
```

The constructor that set the time zone is gone too: a `LocalDate` has no time zone to get wrong.

### After

```bash
./demo --scenario labels
```

```
Labels with the right date:        2,000
Labels with the WRONG date:        0

RESULT: OK. Every label shows the date that was asked for.
```

```bash
./check 6
```

```
Task 6: Fix the beans that remember a request
  [PASS] Receipt: 20 customers pay at the same moment. Nobody is shown another customer's receipt
  [PASS] Receipt: Alice and Bob pay at the same moment. Each gets a receipt with their own name and their own amount
  [PASS] Receipt: ReceiptService is stateless: every field is final, so no request can leave data behind
  [PASS] Labels: 8 threads print labels for 8 different dates, 20,000 each. Every label shows its own date
  4 of 4 pass
```

### What to take away

- A field of a singleton is shared by every request. Request data in a field is a data leak waiting
  for two requests to overlap.
- The fix is not a lock. The fix is no field: parameters and local variables.
- `final` protects the reference, not the object. A `final` field of a mutable, non-thread-safe
  type (`SimpleDateFormat`, `HashMap`, `ArrayList`) is shared mutable state.
- Prefer immutable helpers: `DateTimeFormatter`, `String`, records, `List.of(...)`.

---

## Task 7: Inspect the Scopes

### Step 1: the report now

```bash
./inspect
```

```
  receiptService        singleton  (1 field)                                 OK       stateless: only final references and immutable values
  ...
  shippingLabels        singleton  (1 field)                                 OK       stateless: only final references and immutable values
```

Both have left the `MUTABLE` list. Before your fix they looked like this:

```
  receiptService        singleton  loyalty          LoyaltyClient            OK       final reference to another bean
                                   customer         String                   MUTABLE  not final: every request thread can write it
                                   amountCents      int                      MUTABLE  not final: every request thread can write it
                                   points           int                      MUTABLE  not final: every request thread can write it
  shippingLabels        singleton  format           SimpleDateFormat         MUTABLE  final, but SimpleDateFormat is not thread-safe
```

This is the report to run on your own project. A singleton with a `MUTABLE` field is either a bug
or something that needs a very good reason.

### Step 2: why it survived

With `--threads 1` the broken `ReceiptService` printed `RESULT: OK`. It needs two requests inside
`receiptFor` at the same moment. On a developer's machine there is one request at a time. In a
unit test there is one thread. In a code review the class reads like perfectly normal Java. It
breaks on the first busy day in production, for a few customers, and cannot be reproduced.

### Step 3 and 4: the request-scoped bean

The THREADS table, last two columns:

```
  request  thread  controller (singleton)     service (singleton)   request-scoped bean  prototype bean
  1        exec-1  WhoAmIController@2ffc11f9  VisitCounter@f8ceee9  RequestInfo #3       PrototypeHelper #1
  2        exec-4  WhoAmIController@2ffc11f9  VisitCounter@f8ceee9  RequestInfo #6       PrototypeHelper #1
  ...
  They used 8 different RequestInfo objects: a request-scoped bean is built once per request.
  They all used 1 PrototypeHelper: a prototype injected into a singleton is built once, with the singleton.
```

Eight requests, eight `RequestInfo` objects. Open `scope/RequestInfo`:

```java
@Component
@RequestScope
public class RequestInfo {
```

`@RequestScope` tells Spring to build a new one for every HTTP request and throw it away when the
request ends. Its fields belong to one request, so they are not shared.

But `WhoAmIController` is a singleton and gets its `RequestInfo` once, in the constructor. How does
one field lead to eight objects? Spring does not inject a `RequestInfo`. It injects a **proxy**: a
generated object that looks like a `RequestInfo` and, on every method call, finds the real
`RequestInfo` of the request that the current thread is serving. The BEANS table shows it:

```
  whoAmIController      singleton  visitCounter     VisitCounter             OK       final reference to another bean
                                   requestInfo      RequestInfo              OK       a proxy: each request reaches its own RequestInfo
                                   prototypeHelper  PrototypeHelper          CAREFUL  a prototype bean, injected once: this singleton keeps the same one for ever
```

So "make `ReceiptService` request-scoped and keep the fields" would work. It is rarely the best
answer: you pay for a new object and a proxy on every request to keep three values that fit in
local variables.

### Step 5: the prototype trap

`PrototypeHelper` has `@Scope("prototype")`: "a new instance every time the container is asked for
one". The table shows `PrototypeHelper #1` in all eight rows.

The container is asked **once**: when it builds the singleton `WhoAmIController` and needs
something to pass to its constructor. After that the controller holds on to that one object for
ever. A prototype injected into a singleton behaves like a singleton. Putting state in it is just
as unsafe.

If you really need a new one per use, ask the container each time: inject
`ObjectProvider<PrototypeHelper>` and call `getObject()`, or use `@Lookup`, or give the prototype a
scoped proxy.

### Step 6: where per-request data can live

| Where | Use it when |
|---|---|
| Parameters and local variables | Always, first. Nothing is shared |
| A request-scoped bean | Many beans need the same per-request value and passing it through every method is impractical (the current tenant, a request id) |
| A `ThreadLocal` | The same, without Spring. Frameworks use it: the security context, the transaction. You must clear it, because Tomcat reuses its threads for the next request |

### Check

```bash
./check 7
```

```
Task 7: Inspect the scopes
  [PASS] Scope: a request-scoped bean is a new object for every request; a prototype injected into a singleton is created once
  1 of 1 pass
```

### What to take away

- Singleton is the default scope: one object for the whole application.
- Request scope: one object per HTTP request, reached through a proxy.
- Prototype: one object per **request to the container**. Injected into a singleton, that is once.

---

## Task 8: Fix It With a Lock

**The idea:** when one update changes two variables that must agree, no atomic class can help,
because each one protects a single variable. You need something that covers both: a lock.

### Before

```bash
./demo --scenario sales
```

```
Scenario: sales | requests: 20,000 | in flight at the same time: 64
Half of the requests record a sale of 5.00: POST /api/sales?amountCents=500.
The other half read the dashboard: GET /api/sales/summary.
Every sale is 5.00, so in every summary revenue must be exactly orders x 5.00.

Requests sent:                     20,000
Sales recorded (200 OK):           10,000
Summaries read:                    10,000
Summaries that do not add up:      14
    orders=6,342 revenueCents=3,170,500   (6,342 x 500 = 3,171,000)
    orders=6,785 revenueCents=3,393,000   (6,785 x 500 = 3,392,500)
    orders=9,188 revenueCents=4,594,500   (9,188 x 500 = 4,594,000)
Final orders:                      10,000
Final revenue (cents):             5,000,000
Time taken:                        2,594 ms

RESULT: TORN READ. 14 of 10,000 summaries showed orders and revenue that do not belong together.
```

The final totals are exactly right: 10,000 orders, 5,000,000 cents. No update was lost. And still
14 dashboards showed numbers that cannot both be true: 6,342 orders with the revenue of 6,341.

(This one is rare over HTTP. If you get 0, run it again or use `--requests 50000`.)

```bash
./check 8
```

```
Task 8: Fix it with a lock
  [FAIL] Sales: while 8 threads record sales of 5.00, every summary a reader takes adds up: revenue = orders x 5.00
           27,865 of 50,000 summaries do not add up. For example SalesSummary[orders=6335, revenueCents=3168000]: 6,335 orders x 500 is 3,167,500.
  [PASS] Sales: 8 threads x 50,000 sales of 5.00. The final totals are exact: 400,000 orders, 2,000,000.00
  1 of 2 pass
```

### Why

Open `SalesStats`:

```java
private final AtomicLong orders = new AtomicLong();
private final AtomicLong revenueCents = new AtomicLong();

public void record(long amountCents) {
    orders.incrementAndGet();
    revenueCents.addAndGet(amountCents);
}

public SalesSummary summary() {
    return new SalesSummary(orders.get(), revenueCents.get());
}
```

Each line is atomic. The **pair** is not.

| Time | Writer | Reader | orders | revenue |
|---|---|---|---|---|
| 1 | `orders.incrementAndGet()` | | 6,342 | 3,170,500 |
| 2 | | `orders.get()`: 6,342 | 6,342 | 3,170,500 |
| 3 | | `revenueCents.get()`: 3,170,500 | 6,342 | 3,170,500 |
| 4 | `revenueCents.addAndGet(500)` | | 6,342 | 3,171,000 |

The reader looked in between the writer's two steps. It is the lesson of Task 4 once more: two
atomic operations in a row are not one atomic operation.

### The change

```java
package com.amigoscode.threadsafety.sales;

import org.springframework.stereotype.Service;

@Service
public class SalesStats {

    private long orders;
    private long revenueCents;

    public synchronized void record(long amountCents) {
        orders++;
        revenueCents += amountCents;
    }

    public synchronized SalesSummary summary() {
        return new SalesSummary(orders, revenueCents);
    }

    public synchronized void reset() {
        orders = 0;
        revenueCents = 0;
    }
}
```

Three things to notice:

- **`synchronized` on a method locks `this`**: the one `SalesStats` object. Only one thread at a
  time can be inside *any* synchronized method of that object. A second thread waits at the door.
- **The reader is synchronized too.** If only `record` took the lock, writers would wait for each
  other, and `summary()` could still walk in between a writer's two steps. A lock only protects
  data if **every** access to that data takes it, reads included.
- **The fields are plain `long` again.** Once every read and write happens while holding the lock,
  the atomic classes add nothing. `synchronized` gives both atomicity and visibility.

### After

```bash
./demo --scenario sales
```

```
Summaries that do not add up:      0
Final orders:                      10,000
Final revenue (cents):             5,000,000

RESULT: OK. Every summary added up, and the final totals match the 10,000 sales.
```

```bash
./check 8
```

```
Task 8: Fix it with a lock
  [PASS] Sales: while 8 threads record sales of 5.00, every summary a reader takes adds up: revenue = orders x 5.00
  [PASS] Sales: 8 threads x 50,000 sales of 5.00. The final totals are exact: 400,000 orders, 2,000,000.00
  2 of 2 pass
```

Run `./check` as well: everything should be green now, 29 of 29.

### Another correct fix

Keep both numbers in one immutable object and swap the whole object atomically:

```java
private final AtomicReference<SalesSummary> summary = new AtomicReference<>(new SalesSummary(0, 0));

public void record(long amountCents) {
    summary.updateAndGet(s -> new SalesSummary(s.orders() + 1, s.revenueCents() + amountCents));
}

public SalesSummary summary() {
    return summary.get();
}
```

A reader always gets a complete `SalesSummary`, old or new, never half of each. It passes the same
tests, with no lock.

---

## Task 9: Inspect the Lock

### Step 1: synchronized, and still broken

```bash
./demo --scenario wrong-lock
```

```
Scenario: wrong-lock | requests: 2,000 | in flight at the same time: 64

Sales recorded (200 OK):           1,000
Summaries read:                    1,000
Summaries that do not add up:      984
    orders=19 revenueCents=10,000   (19 x 500 = 9,500)
    orders=11 revenueCents=6,000   (11 x 500 = 5,500)
    orders=30 revenueCents=16,000   (30 x 500 = 15,000)
Final orders:                      973
Final revenue (cents):             496,000

RESULT: TORN READ. 984 of 1,000 summaries showed orders and revenue that do not belong together.
RESULT: LOST UPDATES. 1,000 sales of 500 should give orders=1,000 and revenue=500,000, but the bean says orders=973 and revenue=496,000.
```

Worse than the version with no lock at all: now sales are lost too. Open `WrongLockSalesStats`:

```java
public void record(long amountCents) {
    Object lock = new Object();               // a brand new lock for every call
    synchronized (lock) {                     // nobody else can ever hold this one
        orders++;
        revenueCents += amountCents;
    }
}

public SalesSummary summary() {
    synchronized (this) {                     // a different object from the one record() locks
        return new SalesSummary(orders, revenueCents);
    }
}
```

Every method uses `synchronized`. But `record()` locks an object it has just created. No other
thread has that object, so no other thread ever waits. And `summary()` locks `this`, which
`record()` never locks.

### Step 2: the rule

**A lock protects data only if every thread that touches the data takes the same lock object.**
`synchronized` does not mean "this code is safe". It means "only one thread at a time may hold the
lock of *this particular object*".

- `synchronized` method: locks `this`.
- `static synchronized` method: locks the `Class` object. A different lock from `this`.
- `synchronized (x) { ... }`: locks whatever `x` points at **right now**.

### Step 3: locks that change under you

```java
private Integer count = 0;

public void hit() {
    synchronized (count) {     // locks the Integer object that count points at now
        count++;               // count now points at a DIFFERENT Integer object
    }
}
```

`count++` on an `Integer` creates a new object. The next thread locks the new one while the first
thread still holds the old one. Same mistake as `new Object()`, harder to see. Strings are bad
locks for the opposite reason: equal string literals are the same object across the whole JVM, so
you may share a lock with code you have never seen.

Lock on `this`, or on a dedicated `private final Object lock = new Object();` **field**.

### Step 4 and 5: correct, and slow

```bash
./demo --scenario locked-cache
```

```
Scenario: locked-cache | requests: 20 | in flight at the same time: 64
The requests ask for 20 different products: GET /api/prices/locked/product-N.
Prices are cached, so the slow supplier (50 ms per call) should be asked once per product.

Requests sent:                     20
Answered 200 OK:                   20
Calls to the supplier:             20
Calls needed:                      20
Time taken:                        1,141 ms

RESULT: OK. 20 requests, 20 calls to the supplier, 1,141 ms.
```

Correct. And it took 1,141 ms. The same work with your `computeIfAbsent` cache:

```bash
./demo --scenario price-cache --requests 20 --products 20
```

```
Time taken:                        126 ms

RESULT: OK. 20 requests, 20 calls to the supplier, 126 ms.
```

Nine times faster. Open `LockedPriceCache`:

```java
public synchronized BigDecimal priceOf(String product) {
    if (!prices.containsKey(product)) {
        prices.put(product, source.fetchPrice(product));      // slow, and the lock is held the whole time
    }
    return prices.get(product);
}
```

One lock for the whole bean, held during the 50 ms supplier call. The request for `product-2`
has nothing to do with `product-1`, and it still waits for it: 20 x 50 ms, one after another. With
200 Tomcat threads and one slow supplier, every thread ends up in this queue and the application
stops answering.

### Step 6: the report says MUTABLE

```bash
./inspect
```

```
  salesStats            singleton  orders           long                     MUTABLE  not final: every request thread can write it
                                   revenueCents     long                     MUTABLE  not final: every request thread can write it
  wrongLockSalesStats   singleton  orders           long                     MUTABLE  not final: every request thread can write it
                                   revenueCents     long                     MUTABLE  not final: every request thread can write it
```

Your correct `SalesStats` and the broken exhibit get the same verdict. The report reads fields, and
a lock is not a field. `MUTABLE` means "this needs a lock, go and check that it has one". Under the
table it says: "Only safe if every read and every write holds the same lock."

That is the weakness of a lock compared with the other fixes: nothing in the type of the field
shows that it is protected. It depends on every method, including the ones somebody adds next
year, remembering to take it.

### Step 7: what goes inside a lock

- As little as possible: the reads and writes of the shared state, nothing else.
- Never a slow call: no HTTP, no database, no file, no `Thread.sleep`.
- If you need a slow result, get it **before** you take the lock, then lock only to store it.

Your `SalesStats` holds its lock for two additions, a few nanoseconds. That is what a lock is for.

### Check

```bash
./check 9
```

```
Task 9: Inspect the lock
  [PASS] Wrong lock: record() is synchronized on a new object per call, and sales are still lost (this documents the bug)
  1 of 1 pass
```

### What to take away

- `synchronized` is needed when one update spans several variables.
- Same lock object, for every reader and every writer, or it does nothing.
- Threads queue for a lock. Keep the locked part tiny, and never do I/O inside it.

---

## Task 10: The Rule of Thumb, and Where It Stops

### Step 1 and 2: the six fixes, in order of preference

```bash
./check
```

29 passes, no failures.

| Preference | Approach | Bean in this project | Why it is safe |
|---|---|---|---|
| 1 | **No state**: parameters and local variables | `ReceiptService` | Nothing is shared |
| 2 | **Immutable state**: `final` fields of immutable types | `ShippingLabels` (`DateTimeFormatter`) | Shared, but it never changes |
| 3 | **A thread-safe type, with atomic operations** | `VisitCounter` (`AtomicInteger`), `PageViewStats` (`merge`), `PriceCache` (`computeIfAbsent`) | Each update is one call that nobody can get into |
| 4 | **A lock** | `SalesStats` (`synchronized`) | One thread at a time, for every reader and writer |

Start at the top and go down only when you have to. Each step down is more to get wrong.

The mistakes that go with each level:

- Level 3: a thread-safe type used in **two** calls (check-then-act). `volatile` used where
  atomicity is needed.
- Level 4: the wrong lock object. A reader without the lock. A slow call inside the lock.

### Step 3: logins per customer

```java
private final Map<String, Integer> loginsToday = new ConcurrentHashMap<>();

public void loggedIn(String customer) {
    loginsToday.merge(customer, 1, Integer::sum);
}
```

A `ConcurrentHashMap` and one atomic call. (`ConcurrentHashMap<String, LongAdder>` with
`computeIfAbsent(customer, c -> new LongAdder()).increment()` is the version for very hot counters.)

### Step 4: the half-built PDF in a field

"This bean is a singleton. Two requests will write into the same half-built document, and each
customer gets pages from the other one's PDF. Keep the document in a local variable and pass it to
the steps as a parameter, or create a new builder object with `new` inside the method." It is the
receipt bug from Task 6.

### Step 5 and 6: two instances

Run two copies of this application behind a load balancer and send 1,000 visits. About 500 go to
each. Each JVM has its own Spring context, so each has its own `VisitCounter` object with its own
`AtomicInteger`. `GET /api/visits` returns about 500, and a different number depending on which
instance answers.

`AtomicInteger` is perfectly correct in both. So is `synchronized`: each instance has its own lock,
and a lock in one JVM cannot make a thread in another JVM wait. Everything in this project protects
**one object in one process**.

State that must be correct across instances has to live outside them: in the database, or in
Redis. And there the same race comes back, between two instances instead of two threads, and you
fix it with the database's tools: an atomic `UPDATE ... SET count = count + 1`, a row lock, a
version column. That is the [Locking](../07-locking/) project.

The in-memory techniques are still the right answer for state that is allowed to be per-instance:
a local cache, metrics, rate counters that only need to be roughly right.

### How to say it in an interview

> "Not by default. A Spring bean is a singleton unless you say otherwise, so one instance is used
> by every request thread at the same time, and Spring does nothing to protect its fields. A bean
> is thread-safe if it is stateless: its fields are final and point at other beans or immutable
> objects, and everything that belongs to a request is in parameters and local variables. That is
> how I write services, and it is why most Spring code needs no locks at all. If a bean really
> needs shared state, like a counter or a cache, I use a thread-safe type such as AtomicInteger or
> ConcurrentHashMap, and I make sure each update is a single atomic call, like incrementAndGet,
> merge or computeIfAbsent, because get-then-put on a ConcurrentHashMap is still a race. Volatile
> only gives visibility, so it does not fix count++. I use synchronized when one update has to
> change several fields together, with the reader taking the same lock, and I keep slow calls out
> of it. And all of that is per JVM: with more than one instance, shared state belongs in the
> database or Redis."

Then be ready for the follow-ups in "Questions to Ask Afterwards" at the end of
[`INTERVIEW_TASKS.md`](INTERVIEW_TASKS.md).

---

## Clean up

Nothing to stop: there are no containers. The demo and inspect tools shut the application down
when they finish.

To start again from scratch: `git checkout -- start`.
