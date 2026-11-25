# Interview Tasks — Thread Safety in Spring Beans

> **In `start/`, `./check` FAILS on purpose: 18 of 29 checks pass.** Tasks 2, 4, 6 and 8 turn the
> other 11 green. Tasks 1, 3, 5, 7, 9 and 10 are inspection tasks: you run things, look, and answer
> questions.

Do the tasks in order. Every fix is followed by an inspection task: a green test tells you the fix
works, the inspection tells you **why**, and the why is what an interviewer asks for.

Stuck, or want the explanation? The same task number in [`TASKS_GUIDED.md`](TASKS_GUIDED.md) walks
you through it with the expected output.

Commands are for macOS and Linux. On Windows type `.\demo`, `.\inspect` and `.\check`.

**Rules:** work in `start/`. Never edit, delete or disable a test. Do not fix the exhibits
(`VolatileVisitCounter`, `WrongLockSalesStats`, `LockedPriceCache`, the `scope` package): they stay
as they are so you can compare against them. Do not change a bean's scope to make a test pass.

---

## Task 1: See the Lost Visits (10 min)

**Story:** *Marketing says the visit counter on the dashboard is lower than the number of requests
in the access log. Nobody has seen an error. Before you change anything, reproduce it and find out
what is shared.*

**Steps:**
1. `./demo`. How many requests were answered 200 OK? What does the counter say?
2. Run it twice more. Is the number of lost visits the same each time?
3. `./demo --threads 1`. This sends the same 1,000 requests, one at a time. What does the counter
   say now?
4. `./demo --requests 20000`. Do more requests lose more visits?
5. Open `VisitCounter`. Which line loses the visit? How many `VisitCounter` objects does the
   application have, and how many threads call that line?
6. `./check`. Look at the "Baseline" block. Why do those 12 checks pass on broken code?

**Acceptance Criteria** (`./check 1`, already passing: these tests document the cause)
- [ ] You can say what `count++` does, as three steps
- [ ] You can explain why one request at a time loses nothing
- [ ] You can explain why nothing failed: no exception, no 500, no log line
- [ ] You can say why a single-threaded test cannot find this bug
- [ ] `SingletonScopeIT.theContainerHandsOutTheSameObject` and
      `SingletonScopeIT.everyRequestIsServedByTheSameObjects` pass

**Hints:**
- The default scope of a Spring bean is singleton
- Tomcat serves every request on its own thread, up to 200 at a time by default

---

## Task 2: Fix the Counter (10 min)

**Story:** *Every visit must be counted, however many requests arrive at once. Fix `VisitCounter`.
Keep it a singleton and keep its three methods.*

**Acceptance Criteria** (`./check 2`)
- [ ] 8 threads x 100,000 visits = 800,000, and every visit is counted
      — `VisitCounterTest.everyVisitIsCounted`
- [ ] The same against the bean Spring built, and `GET /api/visits` reports the total
      — `VisitCounterIT.theSharedBeanCountsEveryVisit`
- [ ] `./demo` prints `RESULT: OK`
- [ ] The baseline checks still pass (`./check`)

**Hints:**
- `java.util.concurrent.atomic` has a class that does read-add-write as one step
- Make the field `final`. The reference never changes, only the number inside it

---

## Task 3: Inspect the Counter (15 min)

**Story:** *Your fix passes. A colleague says: "You did not need a new class. Just make the int
`volatile`." Find out whether that is true, and look at what is really shared in this application.*

**Steps:**
1. `./inspect`. Find the `visitCounter` row in the BEANS table. What did it say before your fix
   (`MUTABLE`), and what does it say now? Why is it not simply `OK`?
2. In the same output, read the THREADS table. How many threads served the 8 requests? How many
   `VisitCounter` objects did they use?
3. Open `VolatileVisitCounter`. It is your colleague's fix. Predict the result, then run
   `./demo --scenario volatile`.
4. What does `volatile` guarantee? What does it not guarantee?
5. Name one thing `volatile` **is** the right tool for.
6. `AtomicInteger`, `AtomicLong`, `LongAdder`: when would you pick `LongAdder`?

**Acceptance Criteria** (`./check 3`, already passing: it documents that volatile is not a fix)
- [ ] You can explain the difference between visibility and atomicity in one sentence each
- [ ] You can say how `AtomicInteger.incrementAndGet()` manages without a lock
- [ ] You can give a correct use of `volatile`
- [ ] `VolatileVisitCounterTest.volatileStillLosesVisits` passes

---

## Task 4: Fix the Maps (25 min)

**Story:** *Two more beans keep state in a map. `PageViewStats` counts views per page in a
`HashMap`: pages go missing and the counts are too low. `PriceCache` already uses a
`ConcurrentHashMap`, and the supplier still sends an invoice for 50 price lookups of the same
product. Fix both.*

**Acceptance Criteria** (`./check 4`)
- [ ] 4 threads view 1,000 different pages each: all 4,000 pages are in the map, with 1 view each
      — `PageViewStatsTest.noPageIsLost`
- [ ] 8 threads view the same page 50,000 times each: the page has exactly 400,000 views
      — `PageViewStatsTest.noViewOfAHotPageIsLost`
- [ ] 4 requests for the same product arrive together: the supplier is called once, and all 4 get
      the price — `PriceCacheIT.theSupplierIsAskedOncePerProduct`
- [ ] 12 requests for 3 products arrive together: the supplier is called 3 times
      — `PriceCacheIT.differentProductsAreFetchedOnceEach`
- [ ] `./demo --scenario pages`, `--scenario hot-page` and `--scenario price-cache` all print
      `RESULT: OK`

**Hints:**
- Do `PageViewStats` in two steps. First only change the type of the map, then run
  `./check 4` and `./demo --scenario hot-page` before you go on. Look at what is fixed and what is
  not
- A thread-safe map makes each **call** safe. `get` followed by `put` is two calls
- `ConcurrentHashMap` has methods that read and write in one call: `merge`, `compute`,
  `computeIfAbsent`, `putIfAbsent`
- For the cache: which of those calls the slow supplier only when the key is missing?

---

## Task 5: Inspect the Maps (15 min)

**Story:** *The tests are green. Now explain why swapping `HashMap` for `ConcurrentHashMap` was
necessary and still was not enough.*

**Steps:**
1. In Task 4 you ran `./demo --scenario pages` on the `HashMap`. It printed two different numbers
   for the pages in the map. Why can `size()` and the number of entries you can look up disagree?
2. Name three different things that can happen to a `HashMap` that several threads write to.
3. `./inspect`. `pageViewStats` and `priceCache` are both `CAREFUL` now. In `start/`, `priceCache`
   was `CAREFUL` too, and it was broken. What can the report not see?
4. Write down the three steps of `map.put(k, map.get(k) + 1)`. Which other bug in this project has
   the same three steps?
5. `./demo --scenario price-cache`. 50 requests, 1 call. What were the other 49 requests doing
   while the first one was fetching?
6. `./demo --scenario price-cache --requests 20 --products 20`. 20 different products, 50 ms each.
   Is the time taken closer to 50 ms or to 1,000 ms? What does that tell you about what
   `computeIfAbsent` locks?
7. The Javadoc of `computeIfAbsent` says the function should be "short and simple". Ours calls a
   slow supplier. What is the risk, and what would you use in a real project?

**Acceptance Criteria** (no tests: you answer)
- [ ] You can say what `ConcurrentHashMap` guarantees, and what it does not
- [ ] You can explain "check-then-act" and name the atomic method that replaces each pattern:
      get-then-put, containsKey-then-put
- [ ] You can explain why `computeIfAbsent` runs the function once per key
- [ ] You can say why `Collections.synchronizedMap` would not have fixed the counts either

---

## Task 6: Fix the Beans That Remember a Request (20 min)

**Story:** *Two bug reports. One: a customer was shown a receipt with another customer's name and
amount on it. Two: a few parcels a day go out with a label for the wrong date. Both beans work
perfectly in every test anyone wrote. Fix `ReceiptService` and `ShippingLabels`.*

**Acceptance Criteria** (`./check 6`)
- [ ] Alice and Bob pay at the same moment: each gets a receipt with their own name and amount
      — `ReceiptIT.aliceAndBobGetTheirOwnReceipt`
- [ ] 20 customers pay at the same moment: nobody is shown another customer's receipt
      — `ReceiptIT.nobodyGetsAnotherCustomersReceipt`
- [ ] `ReceiptService` is stateless: every field is `final`
      — `ReceiptServiceTest.keepsNoRequestDataInFields`
- [ ] 8 threads print labels for 8 different dates, 20,000 each: every label shows its own date
      — `ShippingLabelsTest.everyLabelShowsItsOwnDate`
- [ ] `./demo --scenario receipt` and `./demo --scenario labels` print `RESULT: OK`

**Hints:**
- Before you change anything, run `./demo --scenario receipt`, and then
  `./demo --scenario receipt --threads 1 --requests 10`. Remember the second result for Task 7
- `ReceiptService`: which of its fields belong to the bean, and which belong to one request?
- A local variable or a parameter lives on the stack of the thread that runs the method. No other
  thread can reach it
- `ShippingLabels`: the field is `final`. Why is that not enough?
- `java.time.format.DateTimeFormatter` is immutable. Keep the same output: `05 Mar 2026`, in English

---

## Task 7: Inspect the Scopes (15 min)

**Story:** *Somebody suggests a different fix for `ReceiptService`: "leave the fields, just make the
bean request-scoped". Find out what that would mean, and what a prototype bean would not fix.*

**Steps:**
1. `./inspect`. Compare the BEANS table with the one from Task 3. Which beans left the `MUTABLE`
   list? What does the report say about `receiptService` and `shippingLabels` now?
2. In Task 6, the broken `ReceiptService` printed `RESULT: OK` when you ran it with `--threads 1`.
   Why did this bug survive every test and every code review until production?
3. In the THREADS table, look at the `request-scoped bean` column. How many `RequestInfo` objects
   served the 8 requests? Open `RequestInfo`: which annotation does that?
4. `WhoAmIController` is a singleton, and `RequestInfo` is injected into it once, in the
   constructor. How can 8 requests reach 8 different objects through one field?
5. Look at the `prototype bean` column. How many `PrototypeHelper` objects were there? Why, if
   prototype means "a new one every time"?
6. You have three ways to keep per-request data: a local variable, a request-scoped bean, a
   `ThreadLocal`. Which is the default choice, and when would you reach for the other two?

**Acceptance Criteria** (`./check 7`, already passing: it documents the scopes)
- [ ] You can name the default scope and say what it means for instance fields
- [ ] You can explain how a request-scoped bean is injected into a singleton
- [ ] You can answer "what happens with a prototype bean injected into a singleton?"
- [ ] `SingletonScopeIT.requestScopeIsPerRequestAndAnInjectedPrototypeIsNot` passes

---

## Task 8: Fix It With a Lock (15 min)

**Story:** *`SalesStats` holds two numbers: orders and revenue. Whoever wrote it knew about
`AtomicLong` and used one for each. No sale is ever lost. But the dashboard sometimes shows an
average order value that is impossible, and finance wants to know why. Fix `SalesStats`.*

**Acceptance Criteria** (`./check 8`)
- [ ] While 8 threads record sales of 5.00, every summary a reader takes adds up:
      revenue = orders x 5.00 — `SalesStatsTest.everySummaryAddsUp`
- [ ] 8 threads x 50,000 sales: the final totals are exact
      — `SalesStatsTest.finalTotalsAreExact` (already passing: keep it that way)
- [ ] `./demo --scenario sales` prints `RESULT: OK`

**Hints:**
- Two atomic operations in a row are not one atomic operation. You have seen this in Task 4
- `synchronized` on a method locks `this`. Which methods touch the two fields?
- If only the writer takes the lock, what can the reader still see?
- Once every access is under the lock, the fields do not need to be atomic any more

---

## Task 9: Inspect the Lock (15 min)

**Story:** *`synchronized` fixed it. Before you start putting it everywhere, look at two beans that
also use `synchronized`: one that is still broken, and one that is correct and slow.*

**Steps:**
1. `./demo --scenario wrong-lock`. Then open `WrongLockSalesStats`. It uses `synchronized` in every
   method. Which object does `record()` lock? Which object does `summary()` lock?
2. State the rule: when does a lock protect a piece of data?
3. Why is `synchronized (someInteger)` or `synchronized (someString)` a mistake, even when every
   method uses the same field?
4. `./demo --scenario locked-cache`. 20 requests for 20 different products. Is the result correct?
   How long did it take?
5. `./demo --scenario price-cache --requests 20 --products 20`. Same work, your fix from Task 4.
   How long did it take? Open `LockedPriceCache` and explain the difference.
6. `./inspect`. `salesStats` is on the `MUTABLE` list again, and it is correct. Why does the report
   say that?
7. Your `SalesStats` lock is held for two additions. `LockedPriceCache` holds its lock for a
   network call. What is the rule for what goes inside a `synchronized` block?

**Acceptance Criteria** (`./check 9`, already passing: it documents the wrong lock)
- [ ] You can say which object a `synchronized` method locks, and a `static synchronized` one
- [ ] You can explain why a lock on a new object, or on a different object per thread, does nothing
- [ ] You can explain the cost of a lock in terms of who waits, and for how long
- [ ] `WrongLockSalesStatsTest.aLockNobodySharesProtectsNothing` passes

---

## Task 10: The Rule of Thumb, and Where It Stops (15 min)

**Story:** *"Is a Spring bean thread-safe?" is the interview question. Put what you have seen into
an answer, and then find its limit.*

**Steps:**
1. `./check`. 29 of 29. `./inspect`: read the summary under the BEANS table one more time.
2. For each of the six beans you fixed, name the fix in three words or fewer. Sort them from "no
   shared state at all" to "shared state behind a lock".
3. A new requirement: "count how many times each customer logged in today". Which type do you
   use, and which method? Write the one line.
4. A new requirement: "a bean builds a PDF in several steps and keeps the half-built document in a
   field between the steps". What do you say in the code review?
5. The shop now runs **two** instances of this application behind a load balancer. Each visit goes
   to one of them. What does `GET /api/visits` return? Does `AtomicInteger` help? Does
   `synchronized`?
6. Where does state have to live so that it is right across instances? Which project in this folder
   shows how to update it safely?

**Acceptance Criteria** (no tests: you answer)
- [ ] You can answer "is a Spring bean thread-safe?" in three sentences
- [ ] You can list the options in order of preference: stateless, immutable, thread-safe type with
      atomic operations, lock
- [ ] You can say why none of them works across two instances of the application

---

## Questions to Ask Afterwards

Answer out loud, in full sentences, before reading the answer in brackets.

- Is a Spring bean thread-safe? (Spring does nothing to make it so. By default a bean is a
  singleton: one object, called by every request thread at the same time. It is thread-safe only if
  it has no state that changes, or if the state it has is protected. A stateless bean with `final`
  fields that point at other beans is safe. A bean with a field that requests write to is not.)
- What is the default bean scope, and what does that mean for instance fields? (Singleton: one
  instance per application context. Every thread that uses the bean sees the same fields, so a
  field is shared between all requests. Data that belongs to one request must not be in a field.)
- Why does `count++` lose updates? (It is three steps: read the value, add one, write the result.
  Two threads can both read 41, both add one and both write 42. One increment is gone, and nothing
  fails.)
- `volatile` vs `synchronized` vs `AtomicInteger`? (`volatile` gives visibility: a read sees the
  latest write. It does not make read-add-write one step, so it does not fix `count++`. It is right
  for a flag that one thread sets and others read. `synchronized` gives mutual exclusion and
  visibility: one thread at a time in the block, so any group of steps becomes atomic, at the cost
  of threads waiting. `AtomicInteger` makes single operations on one variable atomic without a
  lock, using compare-and-set.)
- How does `AtomicInteger.incrementAndGet()` work without a lock? (Compare-and-set, a single CPU
  instruction: "write 42 only if the value is still 41". If another thread got there first, it
  reads again and retries. It is optimistic locking inside the CPU.)
- When would you use `LongAdder` instead of `AtomicLong`? (For a counter that many threads
  increment very often and that is read rarely, like a metrics counter. It spreads the count over
  several cells so threads do not fight over one, and adds them up on read. The sum is not an
  atomic snapshot.)
- `HashMap` vs `ConcurrentHashMap`? (`HashMap` is not thread-safe: concurrent writes lose entries,
  break `size()`, and can leave a thread looping for ever. `ConcurrentHashMap` is: each single
  operation is safe and atomic, readers do not block, and writers lock only one bucket. It does not
  allow null keys or values.)
- Is a `ConcurrentHashMap` enough? (No. It makes each call atomic, not a sequence of calls.
  `if (!map.containsKey(k)) map.put(k, v)` and `map.put(k, map.get(k) + 1)` are check-then-act:
  another thread can get in between the two calls. Use the atomic methods: `putIfAbsent`,
  `computeIfAbsent`, `merge`, `compute`.)
- Why not `Collections.synchronizedMap` or `Hashtable`? (They lock the whole map for every call, so
  threads queue even to read. And they have the same check-then-act problem: two calls are still
  two calls.)
- A singleton keeps the current user in a field. What happens? (Under load, request A sets the
  field, request B overwrites it, and request A reads B's user. A customer sees another customer's
  data, with a 200 OK. Fix: pass the data as parameters and keep it in local variables. If it must
  be reachable without passing it around, a request-scoped bean or the security context.)
- Is `SimpleDateFormat` thread-safe? What do you use instead? (No. It keeps the date it is working
  on in an internal `Calendar`, so two threads formatting at once get each other's dates or an
  exception. `DateTimeFormatter` from `java.time` is immutable and safe to share. The same applies
  to any helper with internal state.)
- When do you need `synchronized`, if you have atomic classes? (When one update touches more than
  one variable and they must stay consistent, or when a check and an action must happen together
  and no single atomic method does it. Atomic classes protect one variable.)
- What does a `synchronized` method lock? (The object it is called on, `this`. A
  `static synchronized` method locks the `Class` object. Two threads only exclude each other if
  they lock the same object.)
- What goes wrong with locks? (Locking the wrong object, so nobody waits. Locking only the writers,
  so readers see half an update. Holding a lock during a slow call, so every other thread queues.
  Taking two locks in different orders in two places, which is a deadlock.)
- What are `wait` and `notify`? (Methods on every object, used inside `synchronized`. `wait`
  releases the lock and sleeps until another thread calls `notify` or `notifyAll` on the same
  object. Always call `wait` in a loop that re-checks the condition. In practice you use the
  `java.util.concurrent` classes built on the same idea: `BlockingQueue`, `CountDownLatch`,
  `Semaphore`.)
- What happens with a prototype bean injected into a singleton? (It is created once, when the
  singleton is built, and the singleton keeps that one instance for ever. So it behaves like a
  singleton. To get a new one each time, ask the container each time: `ObjectProvider`, `@Lookup`,
  or a scoped proxy.)
- How does a request-scoped bean work inside a singleton? (Spring injects a proxy. On every call
  the proxy looks up the real object for the current request, so each request reaches its own
  instance through the same field.)
- How would you find a bug like this in a code review? (Look at the fields of every `@Service`,
  `@Component` and `@Controller`. A field that is not `final` is a question. A `final` field that is
  a collection or a helper with state is a question. Then look for two calls on a shared object
  where the second depends on the first.)
- Why did the tests not catch it? (They ran on one thread. Every one of these beans is correct with
  one thread. You need a test that starts several threads at the same moment, and for
  check-then-act you need to make them overlap at the right place.)
- Does any of this work with two instances of the application? (No. Each JVM has its own beans, so
  each has its own counter, its own cache and its own lock. State that must be correct across
  instances lives in the database or Redis, updated with an atomic statement, a row lock or a
  version column.)

## Tips for Interviewers

- The most common wrong answer is "Spring beans are thread-safe because Spring manages them". The
  second most common is "make it `volatile`". Run `./demo --scenario volatile` and ask why.
- Ask the candidate to predict `./demo --threads 1` before running it. A strong candidate says
  immediately that nothing will be lost, and why.
- Task 4 separates levels. A junior changes `HashMap` to `ConcurrentHashMap` and stops. Ask them to
  run `./demo --scenario hot-page` again. A stronger candidate names check-then-act without being
  asked.
- In Task 6, watch for `synchronized` on `receiptFor`. It passes the behaviour tests and makes the
  whole service handle one customer at a time, while holding a lock during a network call. Ask what
  it costs. The field test is there to steer towards the stateless fix.
- In Task 8, a candidate who synchronizes only `record()` has fixed half of it. Ask what the reader
  sees.
- For code review rounds: show `ReceiptService` from `start/` with no context and ask "anything
  wrong with this?". It compiles, it reads naturally, and every unit test passes.
- For senior candidates, Task 10 step 5 is the real question: everything in this project stops
  working the moment there are two instances.
