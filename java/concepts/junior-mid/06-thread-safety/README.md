# Thread Safety — Shared State in Spring Beans

> **In `start/`, `./check` FAILS on purpose: 18 of 29 checks pass.** That is the exercise, not a
> broken checkout. Tasks 2, 4, 6 and 8 turn the other 11 green.

A shop counts its visits with `count++`. 1,000 requests come in, every one is answered 200 OK, and
the counter says 934. No exception, no error in the log, and 66 visits are gone.

The reason is one sentence: **a Spring bean is a singleton, so one object serves every request
thread at the same time, and any field it can change is shared by all of them.** This project lets
you watch that go wrong in six different ways, fix each one, and look at what your fix changed.

It answers the interview questions students most often get wrong:

- "Is a Spring bean thread-safe?"
- "What is the default bean scope, and what does that mean for instance fields?"
- "`volatile` vs `synchronized` vs `AtomicInteger`: what is the difference?"
- "`HashMap` vs `ConcurrentHashMap`. And is a `ConcurrentHashMap` enough?"

## Tech Stack

- **Java 25**, **Spring Boot 4** (Spring MVC on Tomcat)
- JUnit 5, AssertJ
- No database. All the state lives in the fields of the beans, which is the point

## What you need

- JDK 25

That is all. **Docker is not needed** for this project: there is no database and no
`compose.yaml`. A machine with at least 2 CPU cores is assumed (every laptop has that).

## Getting Started

Work in `start/`. Commands are shown for macOS and Linux; on Windows type `.\demo` where you see
`./demo` (same for `inspect` and `check`).

```bash
cd start
./demo                    # 1,000 requests, and the counter says less
./check                   # 18 of 29 pass: that is your starting point
```

The first run downloads Maven and the dependencies, so it takes a minute or two.

## The three tools

### `./demo` — see what happens

Starts the real application, sends HTTP requests at it at the same moment, and prints what the
beans ended up with. It ends with a `RESULT:` line. Every run starts a fresh application, and all
the state is in memory, so every run starts from a clean state.

```
./demo                                  1,000 requests at POST /api/visits (scenario: counter)
./demo --scenario receipt               one scenario per problem, see the list below
./demo --requests 20000                 how many requests to send
./demo --threads 1                      how many are in flight at the same time (default 64)
./demo --help
```

| Scenario | What goes wrong | Tasks |
|---|---|---|
| `counter` | `count++` on an `int` field loses updates | 1, 2, 3 |
| `volatile` | the same with a `volatile int`: still loses updates (exhibit) | 3 |
| `pages` | a `HashMap` field loses entries and corrupts itself | 4, 5 |
| `hot-page` | get-then-put on a map loses counts, also on a `ConcurrentHashMap` | 4, 5 |
| `price-cache` | check-then-act: the slow supplier is called 50 times instead of once | 4, 5 |
| `receipt` | request data in a field: customers get each other's receipt | 6, 7 |
| `labels` | one shared `SimpleDateFormat`: labels with the wrong date | 6, 7 |
| `sales` | two fields that must agree: a reader sees one without the other | 8, 9 |
| `wrong-lock` | `synchronized` on the wrong object protects nothing (exhibit) | 9 |
| `locked-cache` | a lock held during a slow call: correct, and one at a time (exhibit) | 9 |

### `./inspect` — see which objects are shared

You cannot see in the code which objects are shared between threads. `./inspect` starts the
application and prints two things:

1. **BEANS**: every bean of the application, its scope, and a verdict for each field, found with
   reflection. `MUTABLE` means a request thread can change it while another thread is using it
   (a field that is not `final`, or a `final` field of a type that is not thread-safe, such as
   `HashMap`). `CAREFUL` means a thread-safe type, which is only safe if every update is one call.
2. **THREADS**: it sends 8 requests at the same moment and prints, for each one, the thread that
   served it and the identity of the objects that thread used. Eight threads, one controller, one
   service.

`./inspect --requests 20` sends more requests.

### `./check` — are the acceptance criteria met?

`./check 2` runs the tests for Task 2 and prints one line per criterion. `./check` runs everything.
`./mvnw verify` runs the same tests the usual Maven way (`*Test` are unit tests in plain Java,
`*IT` are integration tests that start the application).

The tests are the acceptance criteria. **Never edit, delete or disable a test.**

## The code

```
start/src/main/java/com/amigoscode/threadsafety/
├── visits/      VisitCounter: count++ on an int field. THE FIRST BUG IS HERE (Task 2)
│                VolatileVisitCounter: exhibit for Task 3. Do not fix
├── pages/       PageViewStats: a HashMap of page -> views (Task 4)
├── pricing/     PriceCache: check-then-act on a ConcurrentHashMap (Task 4)
│                LockedPriceCache: exhibit for Task 9. Do not fix
├── receipt/     ReceiptService: keeps the current request in fields (Task 6)
├── shipping/    ShippingLabels: one shared SimpleDateFormat (Task 6)
├── sales/       SalesStats: two fields that must agree (Task 8)
│                WrongLockSalesStats: exhibit for Task 9. Do not fix
├── scope/       exhibit for ./inspect: a request-scoped bean and a prototype bean. Do not fix
└── demo/        the demo and inspect tools. Nothing to change here
```

Every broken bean has a `TODO Task N` comment at the line that matters. The four exhibits are the
same in `start/` and `solution/`: they stay as they are, so you can always run them and compare.

Every bean in this project is correct when one thread uses it. The 12 baseline checks prove that:
they use one thread and they pass from the start. A bug that needs two threads is invisible to a
test that uses one.

## Two ways to work

- **[`INTERVIEW_TASKS.md`](INTERVIEW_TASKS.md)**: challenge mode. A story, acceptance criteria and
  hints for each task. Use this if you want to work it out yourself.
- **[`TASKS_GUIDED.md`](TASKS_GUIDED.md)**: the same ten tasks, step by step, with the output you
  should see and an explanation of why. Use this to learn the concept, or when you are stuck.

Both files use the same task numbers.

| Task | What you do | Checked by |
|---|---|---|
| 1 | See the lost visits | you answer questions (`./check 1` passes: it documents the cause) |
| 2 | Fix the counter: `AtomicInteger` | `./check 2` |
| 3 | Inspect the counter, and why `volatile` is not a fix | you answer questions (`./check 3` passes) |
| 4 | Fix the maps: `ConcurrentHashMap`, then `merge` and `computeIfAbsent` | `./check 4` |
| 5 | Inspect the maps: why a thread-safe map was not enough | you answer questions |
| 6 | Fix the beans that remember a request: a field for request data, a shared `SimpleDateFormat` | `./check 6` |
| 7 | Inspect the scopes: singleton, request, prototype | you answer questions (`./check 7` passes) |
| 8 | Fix it with a lock: `synchronized` | `./check 8` |
| 9 | Inspect the lock: the wrong lock, and what a lock costs | you answer questions (`./check 9` passes) |
| 10 | The rule of thumb, and where it stops | you answer questions |

## Solution

`solution/` is the same project with every task done: `cd solution && ./check` prints 29 of 29.

To start again from scratch: `git checkout -- start` (this throws away your changes in `start/`).

<details>
<summary>Spoiler: the bugs and the fixes</summary>

**The cause of all of them.** The default bean scope is singleton. Tomcat runs every request on
its own thread, and all those threads call the same `VisitCounter`, the same `ReceiptService`, the
same everything. A field that can change is shared mutable state.

| Bean | Bug | Fix | Task |
|---|---|---|---|
| `VisitCounter` | `count++` is read, add, write: three steps. Two threads read the same value and one update is lost | `AtomicInteger.incrementAndGet()` | 2 |
| `PageViewStats` | a `HashMap` is not thread-safe: entries vanish, `size()` is wrong, a thread can loop for ever | `ConcurrentHashMap` | 4 |
| `PageViewStats` | `get` then `put` is two calls: counts are lost even on a `ConcurrentHashMap` | `merge(page, 1, Integer::sum)` | 4 |
| `PriceCache` | `containsKey` then `put`: every thread sees "not there" and fetches | `computeIfAbsent(product, source::fetchPrice)` | 4 |
| `ReceiptService` | the customer of the current request is stored in a field | parameters and local variables: a stateless bean | 6 |
| `ShippingLabels` | `SimpleDateFormat` keeps the date it is working on in its own fields | the immutable `DateTimeFormatter` | 6 |
| `SalesStats` | two `AtomicLong`s are each safe, but nothing keeps them in step | `synchronized` on the writer **and** the reader | 8 |

**`volatile` is not a fix for `count++`.** It makes a write visible to other threads. It does not
make read-add-write one step.

**The rule of thumb.**

1. Keep beans stateless: request data lives in parameters and local variables.
2. Fields are `final` and point at immutable things or at other beans.
3. If state really is shared: a thread-safe type, **and** every update is one atomic call.
4. If an update spans several fields: one lock, held by every reader and every writer, and never
   held during a slow call.

**Where it stops.** All of this protects one object in one JVM. Run two instances of the
application and each has its own counter. State that must be right across instances lives in the
database or in Redis: see the [Locking](../07-locking/) project.

</details>
