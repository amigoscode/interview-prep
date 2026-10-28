# Karat — Technical Screen Practice Map

Every question from a Karat question bank for the **Java + React** skillset (compiled by candidates,
September 2026), mapped to the exercise in this repo that practises it. Karat runs the first technical
screen for many companies (Indeed, Wayfair, Roblox and Citrix among the ones that say so publicly), so
the same question can come up for very different employers.

**The round, as the bank shows it:** 60 minutes, live, in a shared editor with a run button. Two halves.
First a run of short **theory questions**, answered out loud and scored fast (equals/hashCode, immutable
keys, generics wildcards, dependency injection, GC generations, checked exceptions, a code snippet with
a design smell). Then one **coding question** built as a small back-office system: read a page of code,
fix the bug that makes the provided test fail, then add two functions to a spec with tests provided, and
sometimes a third part. The code is plain Java with `main`-style or JUnit 4 tests; no framework.

**Labels**
- **Reported**: the question is in the bank, written down by a candidate who got it.
- **Predicted**: our extension where the bank stops short (a drill whose Part 2 the candidate did not record).

Every coding question is one drill in [karat-drills](karat-drills/). Pick one drill per sitting and time it.

---

## Coding questions

| Question in the bank | Label | Drill |
|---|---|---|
| Gym Membership: fix the statistics bug, add `addWorkout` and `getAverageWorkoutDurations`; a second report adds total membership revenue | Reported (3 reports) | [karat-drills](karat-drills/) `gym` |
| Fix the error, Supermarket: fix the code, then 20% off in the first five days of the month, then 10% off by amount | Reported (text only, no code recorded) | Discussion; the fee logic is close to `fooddelivery` Part 3 |
| StockPrice: fix the empty-collection bug, add `getBiggestChange` | Reported | `stocks` |
| "Is `if (!value.equals(Boolean.TRUE)) return false; return true;` correct?" | Reported | `snippets` |
| Transaction Account: fix `getBalance`, add average transaction amount per account | Reported | `payments` |
| Clinic Appointment Management: fix the statistics, add patient visits and average duration by type | Reported | `clinic` |
| Food Delivery (given in C#): fix order statistics, add deliveries and average time per restaurant, then delivery fees | Reported | `fooddelivery` |
| Game Manager: fix the win count, add match results and average score by outcome | Reported | `games` |
| Reservation / Sports Equipment: cancelled reservations must free the slot, then active ids and minutes by status | Reported (2 reports) | `equipment` |
| Music Library: fix listener stats, then most played song, average listen time, most active listener | Reported | `music` |
| Access Control: `isAuthorized` must honour the level ranking | Reported (Part 1 only) | `access` (Part 2 predicted) |
| Stock Ledger: ignore stale inventory updates, then `previewUpdate` and available stock per SKU | Reported | `stockledger` |
| IoT Device Fleet: enforce the status transition table | Reported (Part 1 only) | `devices` (Part 2 predicted) |
| Retail Order Notification + Delivery Updates: fix `getOrderNotifications`, then de-duplicated update batches | Reported (2 reports) | `notifications` |
| Incident Alerting: fix `hasOpenAlert`, then delivery attempts and attempt counts | Reported | `alerts` |
| License Pooling: fix `isLicenseAvailable`, then `checkoutLicense` and `returnLicense` | Reported | `licenses` |
| Compute pool reservations: fix available capacity, then `addReservation` and reserved capacity by customer | Reported | `compute` |
| Customer Sync Queue: fix `countPendingUpdates`, then `queueUpdate` and `cancelPendingUpdates` | Reported (no tests recorded, ours) | `syncqueue` |
| CRM Data Quality: fix `findByEmail`, then `validateRecord` and `getCitySummary` | Reported | `crm` |
| Bank Imported Transactions: fix the account summary, then running balances and a summary by type | Reported | `bank` |
| Code snippets from the theory half: Boolean null check, mutable `Date` field, sort by length with a lambda, mutable `HashSet` key, `Optional.get()` in a service | Reported | `snippets` |

What the bank shows about how the coding half is marked: the Part 1 bug is always small (integer
division, a wrong enum constant, a missing status check, a returned early) and the point is whether you
run the test and read the failure before touching code. Part 2 is where candidates ran out of time, and
the recorded attempts fail in the same places: dividing by zero for a member with no data, a
`NullPointerException` on an unknown id, forgetting that a map result must omit empty groups, treating
a tie without the rule given. Read the rules twice, say the edge cases out loud, then type.

---

## Theory questions

The bank lists 72 Java questions; most are the same twelve asked in different words. Grouped here with
the answer the interviewer is listening for. Say the headline first, then one example, then stop.

### equals, hashCode and keys
- **Why both `equals()` and `hashCode()`, and what breaks if they disagree?** `HashMap`/`HashSet` find
  the bucket with `hashCode()` and then compare with `equals()`. Equal objects must have equal hashes;
  if not, a key you `put` cannot be found with an equal key, and a set holds "duplicates". The reverse
  is not required: unequal objects may share a hash (a collision), it only costs speed.
- **Does `hashCode()` change? Should map keys be mutable? Why immutable keys?** The hash must not
  change while the object is in a hash-based collection: the entry stays in the old bucket and is
  lost (see the `snippets` drill, `UserDirectory`). So keys should be immutable, or at least the
  fields used by `equals`/`hashCode` must be. `String`, boxed numbers, records and enums are safe.
- **The `User` snippet (setter on a field used by `equals`): what is wrong?** Mutable identity. Fix:
  make `username` final, drop the setter, add `withUsername` returning a new object, or stop basing
  equality on a mutable field.
- **What is an immutable class and why?** Final class, final fields, no setters, defensive copies of
  mutable inputs and outputs (the `Employee`/`Date` snippet), no leaking `this` from the constructor.
  Thread-safe for free, safe as a key, easy to reason about. Records give you most of it.

### Collections
- **`HashMap` vs `ConcurrentHashMap`.** `HashMap` is not thread-safe: concurrent writes can lose
  entries or corrupt the table. `ConcurrentHashMap` locks per bin (CAS plus a synchronized bin for
  collisions), allows concurrent reads, never throws `ConcurrentModificationException` on iteration
  (weakly consistent), and forbids null keys and values. `Collections.synchronizedMap` locks the whole map.
- **How does `HashMap` work internally?** An array of bins indexed by `hash & (n - 1)`; each bin is a
  linked list that turns into a red-black tree past 8 entries (Java 8+); resize at load factor 0.75,
  doubling the table and rehashing. `get` is O(1) average, O(log n) worst in a treeified bin.
- **`ArrayList` vs `LinkedList`.** `ArrayList`: contiguous array, O(1) index, amortised O(1) append,
  O(n) insert in the middle, cache friendly. `LinkedList`: O(1) insert/remove at a known node, O(n)
  index, every node is an allocation. In practice `ArrayList` wins almost always; use `ArrayDeque` for
  a queue or stack.
- **What is a `Queue` and when?** FIFO interface (`offer`, `poll`, `peek`). `ArrayDeque` for a plain
  queue, `PriorityQueue` for "next smallest", `LinkedBlockingQueue` between producer and consumer
  threads. Model work to do in order, buffering, BFS.
- **`Collectors.toList()` vs `Stream.toList()`.** `Stream.toList()` (Java 16) returns an unmodifiable
  list and allows nulls; `Collectors.toList()` returns an `ArrayList` you may mutate, with no
  guarantee in the spec. Prefer `toList()` unless you need to add to the result.
- **`Comparable` vs `Comparator`.** `Comparable` is the type's one natural order (`compareTo` in the
  class, used by `TreeMap`, `Collections.sort` with no argument). `Comparator` is an external order,
  as many as you like, passed in: `Comparator.comparingInt(String::length).thenComparing(naturalOrder())`.
  Use `Comparable` when there is one obvious order (dates, money), `Comparator` for everything else.
- **Write the comparator as a lambda / sort by length with a lambda.** `words.sort((a, b) ->
  Integer.compare(a.length(), b.length()))`, better `words.sort(Comparator.comparingInt(String::length))`.
  Not `a.length() - b.length()`: subtraction overflows for large values.
- **Print the fruits with a lambda.** `fruits.forEach(System.out::println)` or
  `fruits.forEach(fruit -> System.out.println(fruit))`.
- **`users.stream().filter(User::isActive).forEach(...)`: what is the design missing?** Side effects
  in `forEach` instead of collecting a result; the business rule "active" lives in the pipeline, not in
  the domain; and the method reference is wrong (`User::Active` is not a method). Return
  `List<User>` from a named method and let the caller decide what to do with it.

### Generics
- **Upper bounded (`? extends T`) and lower bounded (`? super T`) wildcards: why not a plain type
  parameter?** PECS: producer extends, consumer super. `List<? extends Number>` accepts
  `List<Integer>` and `List<Double>` for reading (you cannot add). `List<? super Integer>` accepts
  `List<Number>` and `List<Object>` for writing. A plain `List<Number>` would accept neither, because
  generics are invariant. Wildcards make APIs accept more callers without casts.

### Language
- **Checked vs unchecked exceptions, pros and cons.** Checked: compiler forces a `catch` or `throws`;
  good for recoverable conditions the caller can act on (file missing), bad when they leak through
  every signature and get swallowed in empty catches, and they do not work with lambdas/streams.
  Unchecked (`RuntimeException`): programming errors and conditions nobody up the stack can fix;
  most modern APIs (Spring, JPA) use them. Rule: checked when the caller can recover, unchecked otherwise.
- **"Make recovery cases explicit": what does it mean?** Decide in the signature or the return type what
  happens when the operation cannot do its job: `Optional`, a result type, a specific exception, never
  `null` or a silent default. The `ProductService` snippet is the example.
- **`try` / `catch` / `finally`.** `finally` runs whether or not an exception is thrown, even after
  `return`; use try-with-resources for anything `AutoCloseable`. The `readFirstLine` snippet leaks the
  `BufferedReader`; `try (var br = new BufferedReader(new FileReader(file))) { return br.readLine(); }`.
- **Effectively final.** A local variable that is never reassigned after initialisation. Lambdas and
  anonymous classes may capture only final or effectively final locals, because the lambda may run
  after the method returned; a reassignment makes the compile fail at the capture.
- **`final` on a variable, a method, a class.** Variable: one assignment. Method: cannot be overridden.
  Class: cannot be extended (`String`, records). ("Explicit" is not a Java keyword; the question wants
  explicit constructors or explicit types versus `var`.)
- **`instanceof` and pattern matching for it.** `if (o instanceof User u)` tests and binds in one step,
  no cast, scope-limited, and the compiler stops you from testing an impossible type. Benefits:
  shorter, no `ClassCastException`, pairs with `switch` patterns and sealed types.
- **Records.** `record Point(int x, int y)`: final class, private final fields, canonical constructor,
  accessors, `equals`/`hashCode`/`toString`, all generated. Immutable by default, compact constructors
  for validation, no boilerplate to keep in sync. Not for entities with identity that changes.
- **Enums and their uses.** Fixed set of constants with identity, type-safe (`switch` is exhaustive),
  can carry fields and methods (`Severity.CRITICAL.getRank()`), implement interfaces, and work in
  `EnumMap`/`EnumSet`. Use them for statuses, kinds, strategies.
- **Abstract class vs interface, and when.** Interface: a contract, many per class, default methods
  but no state. Abstract class: shared state and partial implementation, single inheritance. Prefer
  interfaces for roles (`Comparable`, `Repository`), abstract classes for a template with common
  fields. A sealed interface plus records models a closed set of kinds.
- **Multithreading and synchronization.** Threads share the heap; a data race is two threads touching
  the same variable with at least one write and no happens-before edge. `synchronized` gives mutual
  exclusion and visibility; `volatile` gives visibility only; prefer `java.util.concurrent`
  (`AtomicLong`, `ConcurrentHashMap`, executors). Virtual threads (Java 21) make blocking cheap.

### Memory and GC
- **Heap generations; when does an object move?** The heap is split into young (eden plus two
  survivor spaces) and old. Objects are born in eden. A minor GC copies live objects to a survivor
  space and bumps their age; after surviving a threshold of minor GCs (tenuring threshold, 15 by
  default in HotSpot, adapted at runtime) they are promoted to old. Big objects go straight to old.
  Premature promotion happens when survivor space is full. The old generation is collected rarely
  and more expensively (G1 mixed collections, full GC as a last resort). Short-lived objects are
  nearly free; long-lived caches and leaks are what fill the old generation.

### Spring and design
- **What is dependency injection and how does it work internally?** A class declares what it needs
  (constructor parameters) and something outside provides them, instead of `new` inside the class.
  Spring scans for components, builds a `BeanDefinition` per class, resolves the dependency graph,
  instantiates beans in order, and injects by type (then by qualifier/name on ambiguity). Why: one
  place to wire, swap implementations, test with fakes, no hidden globals.
- **How does `@Autowired` work internally?** A `BeanPostProcessor` (`AutowiredAnnotationBeanPostProcessor`)
  inspects each bean class with reflection, finds annotated constructors, fields and setters, asks the
  `BeanFactory` for a matching bean by type (narrowing with `@Qualifier`, `@Primary`, or the parameter
  name), and sets it, through the constructor or by reflective field access. A single constructor
  needs no annotation. Prefer constructor injection: final fields, no partially built objects, easy
  to test without Spring.
- **How does DI reduce complexity in a large system?** Dependencies are explicit in the signature,
  so the graph is visible; modules depend on interfaces, so teams can change implementations
  independently; configuration (profiles, properties) replaces code edits; tests build the object with
  fakes in one line.
- **How do you make a class easy to test?** Constructor injection of collaborators behind interfaces;
  no statics or singletons; pass in `Clock` and `Random`; pure functions for the rules; small methods
  with one reason to change; no `new` of heavy objects inside; keep I/O at the edges.
- **Open/closed principle on a snippet.** A `switch` over a type or status that grows with every new
  case is the smell. Replace it with polymorphism (strategy per kind, a map from kind to handler, a
  sealed interface) so new behaviour is a new class, not an edit to a working one.
- **Factory pattern for flexible applications.** The caller asks a factory for "a payment processor
  for CARD" and gets an interface; the decision which class to build is in one place, driven by
  config or data; adding a kind means adding a class and one registration.
- **Strategy pattern.** An interface for one algorithm (`PricingStrategy.price(order)`), several
  implementations, the context holds one and can swap it. Lambdas make small strategies one-liners.
- **Design patterns that make code testable.** Dependency injection, strategy (swap the rule),
  factory (control creation), adapter (wrap the third party behind your interface), repository
  (hide persistence), template method sparingly. Avoid singleton and service locator.
- **"Find the design issue in this code" (asked five ways).** Look for: a field used in `equals` with
  a setter; `new` of a dependency inside the class; a `Date`/`List` returned without a copy; a
  `switch` on type; statics holding state; a method doing validation, calculation and I/O at once;
  `Optional.get()` or `null` returns; a swallowed exception; `double` for money.

### NullPointerException scenarios
- Calling a method on a parameter that may be null (`value.equals(...)`: put the literal first or use
  `Objects.equals`). Unboxing a null `Integer` into an `int`. `map.get(key)` on a missing key then
  `.intValue()`. A field never initialised before use (the gym bank's `workouts` list). `Optional.get()`
  on empty. Fixes: validate at the boundary (`Objects.requireNonNull`), `getOrDefault`/`computeIfAbsent`,
  `Optional.orElseThrow` with a message, never return null collections.

---

## React questions (same bank, for the full-stack variant)

- **`React.StrictMode`:** development-only wrapper that double-invokes render and effects to surface
  impure renders and missing cleanups, and warns on deprecated APIs. Use it at the app root, and
  around a new feature while you migrate it.
- **`getBy`, `queryBy`, `findBy` (Testing Library):** `getBy` throws if absent (assert presence),
  `queryBy` returns null (assert absence), `findBy` returns a promise and waits (async rendering).
- **List virtualization:** render only the rows in the viewport (`react-window`, `react-virtual`) so a
  10,000-row list costs a screenful of DOM nodes.
- **JSX versus `React.createElement`:** JSX compiles to the same calls; it is readable, lets tooling
  (lint, types, highlighting) see the tree, and the compiler can optimise (automatic runtime, hoisting).
- **Refs and `useRef`:** a mutable box that survives renders without causing one. For DOM access
  (focus, measure, scroll), storing timers or previous values, integrating non-React libraries.
- **Higher-order component:** a function that takes a component and returns a wrapped one, to share
  cross-cutting behaviour (auth, data loading) before hooks; today a custom hook is usually better.
- **`useCallback` vs `useMemo`:** both memoise by dependencies; `useCallback(fn, deps)` returns the same
  function reference, `useMemo(() => value, deps)` returns a computed value. Use `useCallback` to keep
  a stable prop for a memoised child.
- **Three things that re-render a component:** its state changes, its parent re-renders (new props or
  not), or a context it consumes changes. (`forceUpdate` and key changes also count.)
- **Lifting state up:** move state to the closest common ancestor and pass it down with callbacks,
  when siblings must agree; reach for context or a store when the ancestor is far away.
- **`useNavigate` and `useParams` (React Router):** `useNavigate` returns a function to change the route
  programmatically; `useParams` reads the dynamic segments of the current route (`/users/:id`).
