# REST Semantics and Error Handling

> **In `start/`, `./check` FAILS on purpose: 13 of 56 checks pass.** That is the exercise, not a
> broken checkout. Tasks 2, 4, 5, 7, 8 and 10 turn the other 43 green.

A member of staff opens the "pending orders" page. A link checker requests every link on that page.
Three orders are cancelled. **3 GET requests. 3 orders cancelled. Nobody clicked anything.**

The cause is one annotation: cancel is `GET /api/orders/{id}/cancel`. This project lets you watch
that happen, then walk through the rest of the API and fix what else it gets wrong: a missing order
that answers 500 with a stack trace, validation that never runs, a client that marks its own order
as PAID, a PUT that wipes fields, and status codes that tell the client the wrong thing.

It answers the interview questions students get most often about REST:

- "Cancelling an order is a GET. Is that a problem?"
- "Which HTTP methods are safe? Which are idempotent?"
- "PUT vs POST. PUT vs PATCH."
- "How do you handle errors in a Spring REST API?"
- "This endpoint returns a 500. Find out why and fix it."

## Tech Stack

- **Java 25**, **Spring Boot 4**, Spring MVC, Spring Data JPA (Hibernate), Bean Validation
- **PostgreSQL 17** in Docker
- JUnit 5, AssertJ, MockMvc, Testcontainers

## What you need

- JDK 25
- Docker, running

## Getting Started

Work in `start/`. Commands are shown for macOS and Linux; on Windows type `.\demo` where you see
`./demo` (same for `inspect` and `check`).

```bash
cd start
docker compose up -d      # Postgres on port 5446
./demo                    # watch a link checker cancel three orders
./check                   # 13 of 56 pass: that is your starting point
```

The first run downloads Maven and the dependencies, so it takes a minute or two.

When you are finished: `docker compose down`.

## The three tools

### `./demo` — see what happens

Starts the real application, resets the database to the same six orders, sends real HTTP requests
and prints each request, each response (status line, the headers that matter, the body) and what
changed in the database. It ends with a `RESULT:` line.

```
./demo                               a link checker visits every link on the admin page (the default)
./demo --scenario cancel             cancel an order, cancel it again, cancel a shipped one
./demo --scenario errors             five client mistakes: which status code and body come back?
./demo --scenario validation         create an order with a negative quantity and no customer
./demo --scenario mass-assignment    the client sends status, total and internalNote in a PUT
./demo --scenario put                change the address with a PUT that leaves two fields out
./demo --scenario patch              change the address with a PATCH that sends one field
./demo --scenario patch-null         try to clear a field with a PATCH that sends null
./demo --scenario status-codes       create an order, delete an order, delete it again
./demo --help
```

### `./inspect` — see the whole HTTP contract at once

Two reports. It resets the database too.

- **ENDPOINTS**: every endpoint Spring has registered, read from `RequestMappingHandlerMapping`,
  with what is wrong with it. Every GET is really called once and the `orders` table is compared
  before and after, so "this GET changes data" is measured, not guessed. It also reports a request
  body that is a JPA entity, a body with validation rules and no `@Valid`, and a handler that
  returns an entity.
- **STATUS CODES**: 18 probe requests (an id that does not exist, a body that is not JSON, the
  wrong method, a delete sent twice...) with the status code each one got and the one it should get.

```
./inspect                 both reports
./inspect --endpoints     only the endpoints
./inspect --probes        only the status codes
./inspect --bodies        also print the body of every probe response
```

### `./check` — are the acceptance criteria met?

`./check 2` runs the tests for Task 2 and prints one line per criterion. `./check` runs everything.
`./mvnw verify` runs the same tests the usual Maven way: the `*Test` classes are unit tests (no
Spring context, no database), the `*IT` classes start the real application against a real Postgres
in Testcontainers and talk to it over HTTP.

The tests are the acceptance criteria. **Never edit, delete or disable a test.**

## The code

```
start/src/main/java/com/amigoscode/restapi/
├── order/
│   ├── OrderController.java     the API. Most of the problems are visible here
│   ├── OrderService.java        loads, saves, cancels, deletes
│   ├── Order.java               the entity, and the rule for cancelling
│   ├── OrderRequest.java        the body of POST /api/orders, with its validation rules
│   ├── OrderStatus.java         PENDING, PAID, SHIPPED, CANCELLED
│   ├── Product.java             KEYBOARD 50.00, MOUSE 20.00, MONITOR 200.00
│   └── OrderRepository.java     five methods. Leave it as it is
├── admin/
│   └── AdminPageController.java the "pending orders" page, as HTML
├── ApiExceptionHandler.java     "one place for all errors". It has a problem of its own
└── demo/                        the demo and inspect tools. Nothing to change here
```

The places to change are marked `TODO Task N`.

Three things must stay as they are, because the unit tests build the application by hand:
`OrderRepository`, the constructor `OrderService(OrderRepository)` and the constructor
`OrderController(OrderService)`. `ApiExceptionHandler` must keep its name and a constructor without
arguments.

Every run of `./demo`, `./inspect` and every test starts from the same six orders
(`src/main/resources/seed.sql`):

| id | customer | product | total | status | also has |
|---|---|---|---|---|---|
| 1 | Maria Jones | 1 x MONITOR | 200.00 | PENDING | notes, phone, internal note |
| 2 | Ahmed Khan | 2 x KEYBOARD | 100.00 | PENDING | |
| 3 | Sofia Rossi | 3 x MOUSE | 60.00 | PENDING | |
| 4 | Liam Brown | 2 x MONITOR | 400.00 | PAID | notes, phone, internal note |
| 5 | Chen Wei | 1 x KEYBOARD | 50.00 | SHIPPED | |
| 6 | Olga Novak | 1 x MOUSE | 20.00 | CANCELLED | |

## Two ways to work

- **[`INTERVIEW_TASKS.md`](INTERVIEW_TASKS.md)**: challenge mode. A story, acceptance criteria and
  hints for each task. Use this if you want to work it out yourself.
- **[`TASKS_GUIDED.md`](TASKS_GUIDED.md)**: the same eleven tasks, step by step, with the output you
  should see and an explanation of why. Use this to learn the concept, or when you are stuck.

Both files use the same task numbers.

| Task | What you do | Checked by |
|---|---|---|
| 1 | See a GET cancel three orders | you answer questions |
| 2 | Fix: cancel with a POST | `./check 2` |
| 3 | Inspect: safe and idempotent methods | you answer questions |
| 4 | Fix: the right status code for every error, as `ProblemDetail` | `./check 4` |
| 5 | Fix: validation that actually runs | `./check 5` |
| 6 | Inspect: the error responses | you answer questions |
| 7 | Fix: request and response DTOs | `./check 7` |
| 8 | Fix: PUT replaces, PATCH changes | `./check 8` |
| 9 | Inspect: what goes in and what comes out | you answer questions |
| 10 | Fix: 201, `Location` and 204 | `./check 10` |
| 11 | The status-code map of this API | you answer questions |

## Solution

`solution/` is the same project with every task done: `cd solution && ./check` prints 56 passes and
no failures.

To start again from scratch: `git checkout -- start` (this throws away your changes in `start/`).

<details>
<summary>Spoiler: the problems and the fixes</summary>

**A GET that changes data (Task 2).** `@GetMapping("/{id}/cancel")`. GET is a *safe* method: by
definition it only reads, so browsers, crawlers, link checkers, caches and proxies request GET URLs
whenever they like, and repeat them. Cancel becomes `POST /api/orders/{id}/cancel`, and the admin
page uses a form with a button in place of a link.

**Every error is a 500 that shows internals (Task 4).** `ApiExceptionHandler` catches `Exception`
and answers 500 with the class name and stack frames. That swallows the exceptions Spring throws for
a bad request, which Spring would have turned into 400, 404, 405 and 415 on its own. The fix:
extend `ResponseEntityExceptionHandler`, map `OrderNotFoundException` to 404 and
`OrderStateException` to 409, answer with `ProblemDetail` (`application/problem+json`), and keep one
last handler that answers 500 with no details and writes the exception to the log.

**Validation never runs (Task 5).** `OrderRequest` has `@NotBlank` and `@Positive`, the controller
parameter has no `@Valid`, so nobody checks them and an order for -5 keyboards is saved. Add
`@Valid`, and make the 400 list every invalid field under `errors`.

**The entity is the API (Task 7).** `PUT` takes `@RequestBody Order`, so the client can set
`status`, `total` and `internalNote` (mass assignment), and a PUT without them resets them. Every
response is the entity, so `internalNote` goes to the client. The fix: `OrderRequest` in,
`OrderResponse` out.

**PUT used for a small change (Task 8).** PUT replaces the whole order, so a client that leaves
`notes` out clears the notes. That is what PUT means. Add `PATCH /api/orders/{id}` with a body of
optional fields: what is not sent stays as it is.

**Status codes that say the wrong thing (Task 10).** Create answers 200 with no `Location`: it
becomes 201 with `Location: /api/orders/{id}`. Delete answers 200 with a made-up body: it becomes
204 with no body. Deleting twice answers 204, then 404.

</details>
