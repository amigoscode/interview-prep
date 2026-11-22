# Fetch Types and the N+1 Problem — EAGER vs LAZY

> **In `start/`, `./check` FAILS on purpose: 12 of 24 checks pass.** That is the exercise, not a
> broken checkout. Tasks 2, 4, 6, 7 and 9 turn the other 12 green.

A shop has an order list. One request for 200 orders runs **201 SQL statements**: one for the
orders, then one more for every customer. Nothing crashes and the JSON is correct, so nobody notices
until the table grows. This project lets you count the statements, then fix it four different ways
and see what Hibernate does differently each time.

It answers the interview questions students most often get wrong:

- "What is the N+1 problem, and how do you fix it?"
- "EAGER or LAZY by default, and for which associations?"
- "What fetch strategies are there, and how does Hibernate implement lazy loading?"
- "What is open-in-view?"
- "You load 1M orders and each has a lazy customer. What happens?"

## Tech Stack

- **Java 25**, **Spring Boot 4**, Spring Data JPA (Hibernate 7)
- **PostgreSQL 17** in Docker
- JUnit 5, AssertJ, Testcontainers

## What you need

- JDK 25
- Docker, running

## Getting Started

Work in `start/`. Commands are shown for macOS and Linux; on Windows type `.\demo` where you see
`./demo` (same for `inspect` and `check`).

```bash
cd start
docker compose up -d      # Postgres on port 5443
./demo                    # 1 request, 201 SQL statements
./check                   # 12 of 24 pass: that is your starting point
```

The first run downloads Maven and the dependencies, so it takes a minute or two.

When you are finished: `docker compose down`.

## The three tools

### `./demo` — count the SQL of one request

Starts the real application, fills the shop with the same data every time, sends **one** request
over HTTP and prints how many SQL statements it ran, grouped by shape.

```
./demo                                  GET /api/orders, the naive code, 200 orders
./demo --fetch join-fetch               naive | join-fetch | entity-graph | projection
./demo --endpoint report                orders | report | order | page
./demo --orders 2000 --customers 50     more orders, fewer customers
./demo --sql                            print every SQL statement in full
./demo --open-in-view                   start the application with open-in-view switched on
./demo --batch-size 50                  start it with hibernate.default_batch_fetch_size=50
./demo --help
```

### `./inspect` — see what "lazy" really is

Prints the fetch type of every association as Hibernate sees it. Then it loads one order and shows
what is inside its fields (a proxy, a collection wrapper, or the real object), touches them one at a
time, and prints the SQL each touch causes. Last, it touches a lazy field after the transaction has
ended. `./inspect --two-bags` also tries to fetch two collections in one query.

### `./check` — are the acceptance criteria met?

`./check 2` runs the tests for Task 2 and prints one line per criterion. `./check` runs everything.
`./mvnw verify` runs the same tests the usual Maven way.

The tests are the acceptance criteria. They never measure time: they count the SQL statements a
request runs. **Never edit, delete or disable a test.**

## The code

```
start/src/main/java/com/amigoscode/fetching/
├── order/
│   ├── Order.java, OrderItem.java   the entities. THE FETCH TYPES ARE HERE
│   ├── OrderRepository.java         the queries. Most of your fixes go here
│   ├── OrderService.java            one method per endpoint; picks the fetch plan
│   ├── OrderController.java         GET /api/orders, /report, /page, /{id}
│   └── OrderSummary, OrderDetails   the DTOs the endpoints return
├── customer/, product/              two more entities
├── sql/SqlRecorder.java             counts every statement Hibernate sends. Nothing to change
├── seed/Seed.java                   fills the shop with SQL. Nothing to change
└── demo/                            the demo and inspect tools. Nothing to change
```

| Endpoint | Returns |
|---|---|
| `GET /api/orders?fetch=naive` | Every order with its customer's name and city. `fetch` picks how it is loaded |
| `GET /api/orders/report` | Orders per status. Never needs a customer |
| `GET /api/orders/{id}` | One order with its customer and items |
| `GET /api/orders/page?page=0&size=20` | One page of orders, each with its items |

`?fetch=naive` stays broken for ever, so you can always compare against it. You fix the others.

## Two ways to work

- **[`INTERVIEW_TASKS.md`](INTERVIEW_TASKS.md)**: challenge mode. A story, acceptance criteria and
  hints for each task. Use this if you want to work it out yourself.
- **[`TASKS_GUIDED.md`](TASKS_GUIDED.md)**: the same ten tasks, step by step, with the output you
  should see and an explanation of why. Use this to learn the concept, or when you are stuck.

Both files use the same task numbers.

| Task | What you do | Checked by |
|---|---|---|
| 1 | See the N+1 | `./check 1` (passes: it documents the bug) |
| 2 | EAGER is not the fix: make the associations LAZY | `./check 2` |
| 3 | Inspect lazy loading: the proxy, the exception, open-in-view | you answer questions |
| 4 | Fix the `LazyInitializationException` | `./check 4` |
| 5 | Inspect the transaction boundary | you answer questions |
| 6 | Fix the N+1 with `JOIN FETCH` and with `@EntityGraph` | `./check 6` |
| 7 | Fix the N+1 with a DTO projection | `./check 7` |
| 8 | Inspect the three fixes | you answer questions |
| 9 | The collection trap: paging, two bags, `@BatchSize` (mid-level) | `./check 9` |
| 10 | Compare everything as the data grows | you answer questions |

Tasks 1 to 8 are junior level. Task 9 is mid-level: skip it on a first pass if you are short of time.

## Solution

`solution/` is the same project with every task done: `cd solution && ./check` prints 24 of 24.

To start again from scratch: `git checkout -- start` (this throws away your changes in `start/`).

<details>
<summary>Spoiler: the bug and the fixes</summary>

**The bug.** `OrderService.list` loads the orders with one query, then `OrderSummary.from(order)`
calls `order.getCustomer().getName()` for each one. Each customer is loaded with its own
`select ... from customer where id = ?`. 1 query + N customers = N+1.

**EAGER does not fix it (Task 2).** `@ManyToOne` is EAGER by default, and the list still runs 201
statements: for a query, Hibernate runs your SQL as written and then loads each eager customer with
a separate select. Worse, the status report, which never looks at a customer, also runs 201. Make
every to-one association `fetch = FetchType.LAZY`.

**LAZY needs an open session (Task 4).** A lazy association is a proxy that runs a query the first
time it is used. If that happens after the transaction ended, there is no session left and Hibernate
throws `LazyInitializationException`. Do the mapping to a DTO inside a `@Transactional` method. Do
not switch `open-in-view` on: it keeps the session open until the response is written, which hides
the exception and every N+1 behind it.

**Fetch it in the same query (Tasks 6 and 7).**

| | JOIN FETCH | @EntityGraph | DTO projection |
|---|---|---|---|
| How | `select o from Order o join fetch o.customer` | `@EntityGraph(attributePaths = "customer")` on a repository method | `select new OrderSummary(o.id, ..., c.name, c.city)` |
| SQL | 1 statement, inner join, 7 columns | 1 statement, left join, 7 columns | 1 statement, inner join, 5 columns |
| You get | Entities | Entities | Records. No entities, no proxies, nothing lazy |
| Use it when | You need the entities, and you write the query anyway | Same, but the query comes from a method name | Read-only screens and lists |

**Collections are different (Task 9).** `join fetch o.items` together with a `Pageable` makes
Hibernate read every row and cut the page in memory (warning `HHH90003004`). Fetching two `List`
collections in one query is refused with `MultipleBagFetchException`. Fetch only to-one associations
in the paged query and let the collections load in batches: `@BatchSize(size = 50)`, or
`hibernate.default_batch_fetch_size` for the whole application.

</details>
