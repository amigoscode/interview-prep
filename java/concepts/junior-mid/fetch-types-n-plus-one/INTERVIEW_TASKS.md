# Interview Tasks — Fetch Types and the N+1 Problem

> **In `start/`, `./check` FAILS on purpose: 12 of 24 checks pass.** Tasks 2, 4, 6, 7 and 9 turn the
> other 12 green. Tasks 1, 3, 5, 8 and 10 are inspection tasks: you run things, look, and answer
> questions.

Do the tasks in order. Fixes are followed by inspection tasks: a green test tells you the fix
works, the inspection tells you **why**, and the why is what an interviewer asks for.

Stuck, or want the explanation? The same task number in [`TASKS_GUIDED.md`](TASKS_GUIDED.md) walks
you through it with the expected output.

Commands are for macOS and Linux. On Windows type `.\demo`, `.\inspect` and `.\check`.

**Rules:** work in `start/`. Never edit, delete or disable a test. Do not fix the `NAIVE` fetch plan
in `OrderService.list`: it stays broken so you can compare against it. Leave
`spring.jpa.open-in-view=false` in `application.properties`.

Tasks 1 to 8 are junior level. **Task 9 is mid-level.**

---

## Task 1: See the N+1 (10 min)

**Story:** *The order list was fast in the demo and is slow in production. Nothing fails, the JSON
is correct. Before you change anything, count what one request does to the database.*

**Steps:**
1. `docker compose up -d`, then `./demo`. How many SQL statements did one request run? How many
   different shapes of statement are there?
2. `./demo --sql`. Compare statement 2 with statement 201. What is different between them?
3. Open `OrderService.list` and `OrderSummary.from`. Which line of Java produced statement 1? Which
   line produced the other 200?
4. `./demo --orders 1000`. How many statements now? Write the formula.
5. `./demo --orders 200 --customers 20`. Still 200 orders. How many statements, and why not 201?
6. `./demo --endpoint report`. This endpoint counts orders per status and never reads a customer.
   How many statements does it run? Keep the number for Task 2.

**Acceptance Criteria** (`./check 1`, already passing: these tests document the bug)
- [ ] You can say where the "1" comes from and where the "N" comes from
- [ ] You can say what N really counts: orders, or something else
- [ ] You can explain why nobody noticed in development
- [ ] `NaiveListIT.oneStatementPerCustomer` and `NaiveListIT.theNIsTheNumberOfDifferentCustomers`
      pass

**Hints:**
- The query for the orders does not mention the customer table at all
- Every extra statement has the same text. Only the parameter changes

---

## Task 2: EAGER Is Not the Fix. Make the Associations LAZY (15 min)

**Story:** *A colleague says: "That is lazy loading. Make the customer EAGER and it goes away."
Check what the mapping is today before you believe it. Then make every to-one association in
`Order` and `OrderItem` lazy.*

**Acceptance Criteria** (`./check 2`)
- [ ] The status report reads 200 orders with 1 statement and loads no customer
      — `LazyFetchIT.reportDoesNotLoadCustomers`
- [ ] Loading one order leaves its customer as a proxy that is not loaded yet
      — `LazyFetchIT.customerIsAProxy`
- [ ] Loading an order's items leaves each item's product as a proxy that is not loaded yet
      — `LazyFetchIT.productIsAProxy`
- [ ] `./demo --endpoint report` prints `RESULT: OK`
- [ ] `./demo` still prints 201 statements. That is expected: this task does not fix the list

**Hints:**
- Run `./inspect` first and read section 1. Which associations are EAGER, and did anyone write that?
- The default depends on the kind of association: to-one and to-many have different defaults
- `fetch = FetchType.LAZY` goes inside the `@ManyToOne` annotation. There are three of them

---

## Task 3: Inspect Lazy Loading: the Proxy, the Exception, Open-in-View (20 min)

**Story:** *The associations are lazy now. Find out what Hibernate put in those fields, when it
talks to the database, and what happens when it cannot.*

**Steps:**
1. `./inspect`, section 1. Which associations are LAZY now? Which one was LAZY before you touched
   anything?
2. Section 2. What is the class of the object in `order.customer`? Is it a `Customer`? How many
   statements did loading the order run, and does that statement mention the customer table?
3. Section 3. `getCustomer().getId()` ran 0 statements, `getName()` ran 1, `getCity()` ran 0. Explain
   all three.
4. Still section 3. What is the class of the object in `order.items`? Which call made it run SQL?
5. Section 4. Two calls fail, with two different messages. What do both messages say is missing?
6. `./demo --endpoint order`. The request answers 500. Which of the two messages is it, and which
   line of `OrderDetails.from` threw it? Before Task 2 the same request failed on `Order.items`
   instead. Why did the message change?
7. `./demo --endpoint order --open-in-view --sql`. The same code now answers 200. How many
   statements ran? Then `./demo --open-in-view`: how many statements does the order list run?

**Acceptance Criteria** (no tests: you answer)
- [ ] You can say what a lazy to-one association and a lazy collection really are at runtime
- [ ] You can say exactly when a proxy runs its query, and what it needs to be able to run it
- [ ] You can quote the exception and explain "no session"
- [ ] You can say what open-in-view does, and why switching it on is not a fix

---

## Task 4: Fix the LazyInitializationException (15 min)

**Story:** *`GET /api/orders/1` answers 500. Make it answer 200 with the customer and the items,
without switching open-in-view on. The fix is in `OrderService.details`.*

**Acceptance Criteria** (`./check 4`)
- [ ] `GET /api/orders/1` answers 200 with the customer's name and both items
      — `OrderDetailsIT.orderOneCanBeRead`
- [ ] Orders 1 to 30 all answer 200, and each total is quantity x price added up over its items
      — `OrderDetailsIT.everyOrderHasTheRightTotal`
- [ ] `spring.jpa.open-in-view` is still `false`
      — `OrderDetailsIT.openInViewStaysOff` (already passing: keep it that way)
- [ ] `./demo --endpoint order` prints `RESULT: OK`

**Hints:**
- The session lives as long as the transaction. Where does the transaction start and end in
  `details` today? (`findById` is a repository method: it opens and closes its own.)
- Everything lazy that the DTO needs must be read before the transaction ends
- One annotation on one method is enough. Look at the other methods in the class

---

## Task 5: Inspect the Transaction Boundary (10 min)

**Story:** *The 500 is gone. Look at what the request does now.*

**Steps:**
1. `./demo --endpoint order --sql`. Five statements. Match each one to a line of
   `OrderDetails.from`.
2. `./demo --endpoint order --id 2`. Order 2 has three items. How many statements now? What is the
   name of this pattern?
3. `./demo --endpoint order --open-in-view`. Does the number of statements change now that the
   method is transactional? So what exactly did open-in-view change in Task 3?
4. In `OrderService.details`, point at the line where the session opens and the line where it
   closes. If the controller did the mapping to `OrderDetails`, on which side of that line would it
   run?
5. The method says `readOnly = true`. What would you expect that to change?

**Acceptance Criteria** (no tests: you answer)
- [ ] You can say where the session begins and ends for this request
- [ ] You can explain why "return DTOs from the service, not entities" is the usual rule
- [ ] You can say what is still not ideal about this endpoint

---

## Task 6: Fix the N+1 With JOIN FETCH and With @EntityGraph (20 min)

**Story:** *Now the list. The customers should come back in the same statement as the orders. Do
it twice, with the two tools JPA gives you: once by writing the query (`?fetch=join-fetch`), once by
declaring what to fetch on a repository method (`?fetch=entity-graph`). The code is in
`OrderRepository` and `OrderService.list`.*

**Acceptance Criteria** (`./check 6`)
- [ ] `?fetch=join-fetch` runs 1 statement for 200 orders and returns the same JSON as the naive
      list — `FetchInOneQueryIT.joinFetchRunsOneStatement`
- [ ] `OrderRepository` has a `@Query` that returns a `List` and says `join fetch o.customer`
      — `OrderRepositoryDeclarationsTest.hasAJoinFetchQuery`
- [ ] `?fetch=entity-graph` runs 1 statement for 200 orders and returns the same JSON as the naive
      list — `FetchInOneQueryIT.entityGraphRunsOneStatement`
- [ ] `OrderRepository` has a method with `@EntityGraph(attributePaths = "customer")` and no
      `join fetch` of its own — `OrderRepositoryDeclarationsTest.hasAnEntityGraphMethod`
- [ ] `./demo --fetch join-fetch` and `./demo --fetch entity-graph` print `RESULT: OK`
- [ ] `./demo` (naive) still prints 201 statements

**Hints:**
- JPQL: `join fetch` is a join that also fills the association from the joined rows
- The list is ordered by id. Keep it that way, or the JSON will not match
- `@EntityGraph` works on a derived query method. Spring Data can build "all orders, ordered by id"
  from a method name
- Do not put `@EntityGraph` on `findAll`: the naive plan uses it

---

## Task 7: Fix the N+1 With a DTO Projection (15 min)

**Story:** *The list shows five values per order. The entities carry more than that, and nobody
edits orders on this screen. Let the query return `OrderSummary` records directly
(`?fetch=projection`): no `Order`, no `Customer`, nothing lazy.*

**Acceptance Criteria** (`./check 7`)
- [ ] `?fetch=projection` runs 1 statement for 200 orders and returns the same JSON as the naive
      list — `ProjectionIT.projectionRunsOneStatement`
- [ ] That statement selects only the 5 columns the list shows
      — `ProjectionIT.projectionSelectsOnlyWhatTheListShows`
- [ ] `./demo --fetch projection` prints `RESULT: OK`

**Hints:**
- JPQL constructor expression: `select new com.amigoscode.fetching.order.OrderSummary(...) from ...`
- The arguments must match the record's constructor: id, status, placedAt, customer name, customer
  city
- A plain `join` is enough here. There is no entity to fill, so there is nothing to `fetch`

---

## Task 8: Inspect the Three Fixes (15 min)

**Story:** *Three fixes, one statement each. They are not the same statement.*

**Steps:**
1. Run `./demo --fetch join-fetch --sql`, `./demo --fetch entity-graph --sql` and
   `./demo --fetch projection --sql`. Which kind of join does each statement use?
2. Count the columns each statement selects. Which columns does the list never show?
3. An order whose `customer_id` is null: which of the three statements would still return it?
   (The schema here does not allow that. Answer for a schema that does.)
4. `./inspect`, section 1. Is `Order.customer` EAGER now that two queries load it up front? What is
   the difference between the fetch type in the mapping and the fetch in a query?
5. The list must also show the customer's email. For each of the three fixes: what do you have to
   change?
6. After `?fetch=projection`, could the service change an order's status and have Hibernate save it?
   Why, or why not?

**Acceptance Criteria** (no tests: you answer)
- [ ] You can quote the SQL of each fix and name the difference
- [ ] You can explain "the mapping sets the default, the query decides"
- [ ] You can say when you would pick a projection over entities, and when you could not

---

## Task 9: The Collection Trap: Paging, Two Bags, @BatchSize (25 min, mid-level)

**Story:** *`GET /api/orders/page` returns one page of orders with their items. Somebody already
"fixed the N+1" there by fetching everything in one query. It runs 2 statements and the JSON is
correct. Run `./demo --endpoint page`, read the `[WARN]` line and the `RESULT` line, then fix
`OrderRepository.findPage` and the mapping so the database does the paging and the page still needs
only a handful of statements.*

**Acceptance Criteria** (`./check 9`)
- [ ] The statement that selects the orders has a limit (`fetch first ... rows only`)
      — `PagingIT.theDatabaseDoesThePaging`
- [ ] A page of 20 orders with their items runs at most 5 statements
      — `PagingIT.aPageRunsAHandfulOfStatements` (already passing: keep it that way)
- [ ] Page 2 of size 20 holds orders 41 to 60 out of 200, each with its own items and total
      — `PagingIT.pageHoldsTheRightOrders` (already passing: keep it that way)
- [ ] Fetching two `List` collections in one query is refused
      — `PagingIT.twoBagsCannotBeFetchedTogether` (already passing: it documents the limit)
- [ ] `./demo --endpoint page` prints `RESULT: OK` and no `[WARN]` line

**Hints:**
- A join on a collection returns one row per item, not per order. What would "the first 20 rows"
  mean then?
- Fetching a to-one association does not have that problem. Keep `join fetch o.customer`
- Take the collection out of the paged query and run the demo again before you do anything else.
  Count the statements
- `@BatchSize(size = 50)` on a collection, and on an entity class, tells Hibernate to load up to 50
  of them with one statement
- Another valid fix: two queries. First the page of orders, then their items with
  `where o.id in :ids`

---

## Task 10: Compare Everything as the Data Grows (20 min)

**Story:** *"How would you fix an N+1?" has more than one right answer. Collect the numbers, then
say which fix you would use where.*

**Steps:**
1. `./demo --endpoint page --sql`. Find the two statements with `= any (?)`. What is in that one
   parameter? Which annotation produced each statement?
2. `./inspect --two-bags`. Read the exception. Why does Hibernate refuse the query? How many rows
   would the database return for a customer with 10 orders of 3 items each?
3. Run the list with 200, 2000 and 20000 orders for `naive`, `join-fetch` and `projection`
   (`./demo --orders 2000 --fetch join-fetch`, and so on). Note statements and response time.
4. `./demo --batch-size 50`, then `./demo --orders 2000 --batch-size 50`. No code changed. How many
   statements? Write the formula.
5. `./demo --orders 2000 --customers 50`. Then answer: you load 1,000,000 orders and each has a lazy
   customer. What happens?
6. Pick a fix for each of these and say why: the order list; a screen that edits one order; a page
   of orders with their items; the status report.

**Acceptance Criteria** (no tests: you answer)
- [ ] You can rank naive, batch fetching, JOIN FETCH and projection by statements and explain it
- [ ] You can say what batch fetching still costs compared with a join
- [ ] You can explain why a collection cannot be fetched and paged in the same query
- [ ] You can give one situation where each fix is the right choice

---

## Questions to Ask Afterwards

Answer out loud, in full sentences, before reading the answer in brackets.

- What is the N+1 problem? (One query loads a list of N rows. Then the code touches an association
  of each row that the query did not fetch, and Hibernate loads it with one extra query per row. 1 +
  N statements where 1 would do. Nothing fails, so it is found by counting statements, not by
  testing.)
- EAGER or LAZY by default, and for which associations? (To-one associations, `@ManyToOne` and
  `@OneToOne`, are EAGER by default. To-many associations, `@OneToMany` and `@ManyToMany`, are LAZY
  by default. The usual advice is to make every association LAZY and fetch what a use case needs in
  its query.)
- Why does EAGER not fix the N+1? (EAGER says when to load, not how. For a JPQL query or `findAll`,
  Hibernate runs the query as written and then loads each eager customer with a separate select: the
  same 1 + N. Only loading by id, `find` or `findById`, uses a join. And EAGER loads the customer on
  every code path, including the ones that never use it.)
- What fetch strategies are there? (When: EAGER or LAZY. How: a join in the same statement, a
  separate select per row, or batch fetching, which loads many proxies or collections with one
  `IN`-style select. Hibernate also has subselect fetching for collections. In a query you override
  the mapping with `JOIN FETCH` or an entity graph, or skip entities with a projection.)
- How does Hibernate implement lazy loading? (For a to-one association it puts a proxy in the field:
  a generated subclass of the entity that knows only the id. The first call to any method other than
  the id getter makes it run a select through the session it came from. For a collection it puts a
  wrapper in the field, `PersistentBag` for a `List`, which loads its rows the first time you read
  it.)
- What is a `LazyInitializationException`? (A proxy or lazy collection was used after its session
  was closed, so it has nothing to run its query with. The message says "no session". It means the
  code read a lazy association outside the transaction that loaded the entity.)
- What is open-in-view? (A Spring Boot setting, on by default, that keeps the Hibernate session open
  for the whole web request, including the controller and the JSON serializer. Lazy associations
  then load wherever they are touched, so there is no `LazyInitializationException`. The price: the
  queries still run, outside any transaction and outside the service, where nobody looks for them.
  It hides N+1 problems. Spring Boot logs a warning at startup when you have not set it yourself.)
- How do you fix a `LazyInitializationException` properly? (Read what you need while the transaction
  is open: map to a DTO inside the `@Transactional` service method, and fetch the associations the
  DTO needs in the query. Not by switching to EAGER and not by switching open-in-view on.)
- JOIN FETCH or `@EntityGraph`? (Same result: the association is loaded in the same statement. JOIN
  FETCH is written in the JPQL and is an inner join unless you write `left join fetch`. An entity
  graph is declared on the repository method, works with derived queries, and uses a left join.)
- When do you use a DTO projection? (For read-only output: lists, reports, anything serialized and
  thrown away. The query selects only the columns needed, there are no entities in the persistence
  context, no dirty checking, and nothing lazy that could fire later. You cannot use it when you want
  to change the data and let Hibernate save it.)
- Why can you not `JOIN FETCH` a collection and paginate? (The join returns one row per item, so a
  limit on rows would cut an order in half. Hibernate leaves the limit out of the SQL, reads every
  row, and cuts the page in memory. It logs `HHH90003004: firstResult/maxResults specified with
  collection fetch; applying in memory`. On a big table that is an out-of-memory error waiting to
  happen. Fetch only to-one associations in the paged query and load the collections in a batch.)
- What is a `MultipleBagFetchException`? (Hibernate refuses to fetch two `List` collections in one
  query. Two collections joined in one statement multiply each other, and a `List` without an order
  column, a bag, cannot tell the duplicates apart. Fetch one collection per query, or use batch
  fetching.)
- What does `@BatchSize` do? (When one lazy proxy or collection has to be loaded, Hibernate loads up
  to that many of the ones waiting in the same session with a single statement. N+1 becomes 1 +
  N/size. `hibernate.default_batch_fetch_size` sets it for the whole application. It is a safety
  net: fewer statements, but still more than a join, and still growing with the data.)
- You load 1M orders and each has a lazy customer. What happens? (The query itself is one statement,
  and no customer is loaded. But a million `Order` entities now sit in the persistence context, with
  a million customer proxies, so memory is the first problem. Then, if the code touches each
  customer, Hibernate runs one more select for every different customer: up to a million round
  trips. The request takes minutes or runs out of memory. I would never load a million entities:
  page it or stream it, select only the columns needed with a projection, and get the customer data
  in the same query with a join.)
- How do you find an N+1 in a real project? (Count statements. Log the SQL for one request in
  development, use Hibernate's statistics, or assert the statement count in an integration test, as
  this project does. Response time alone will not show it on a small development database.)

## Tips for Interviewers

- The most common wrong answer is "N+1 is caused by lazy loading, so make it EAGER". Ask the
  candidate what SQL an EAGER `@ManyToOne` produces for `findAll`. Task 2 is the proof.
- Ask the candidate to predict the number for `./demo --orders 200 --customers 20` before running
  it. A strong candidate says 21 and mentions the persistence context.
- "I would turn on open-in-view" and "I would add `@Transactional` to the controller" both make the
  exception go away. Ask where the queries run now, and how they would notice the next N+1.
- For "how does lazy loading work", listen for the word proxy and for the session it needs. Ask what
  `getCustomer().getId()` costs. Many candidates expect a query.
- In Task 6, ask why the two statements use different joins and which one can lose rows.
- Task 9 separates levels. A junior answer stops at "use JOIN FETCH". A mid-level candidate knows it
  does not combine with pagination or with a second collection, and reaches for batch fetching or
  two queries. Ask what the warning in the log means before they change any code.
- For the 1M question, the weak answer is only "a million queries". The better answer starts with
  memory and ends with "I would not load a million entities at all".
