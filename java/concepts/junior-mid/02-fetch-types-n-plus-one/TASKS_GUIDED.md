# Guided Tasks — Fetch Types and the N+1 Problem

The same ten tasks as [`INTERVIEW_TASKS.md`](INTERVIEW_TASKS.md), step by step: the command to run,
the output you should see, why it happens, and the change to make.

Commands are for macOS and Linux. On Windows type `.\demo`, `.\inspect` and `.\check`.

Response times differ on every machine and on every run. The statement counts, the SQL and the
`RESULT` line should match exactly.

**Before you start**

```bash
cd start
docker compose up -d
```

**The shop.** An `Order` belongs to one `Customer` and has `OrderItem`s. Each item points to a
`Product`. The demo fills the shop with the same data on every run: 200 orders, one customer per
order, 1 to 3 items per order, 20 products.

---

## Task 1: See the N+1

No code in this task. You are collecting evidence.

### Run it

```bash
./demo
```

### What you should see

```
GET /api/orders?fetch=naive
Shop: 200 orders, 200 customers, 401 order items, 20 products. open-in-view: off.

1 request, 201 SQL statements

      1 x  select (4 columns) from orders o1_0 order by o1_0.id
    200 x  select (4 columns) from customer c1_0 where c1_0.id=?

  (--sql prints every statement in full)

HTTP status:            200
Orders in the response: 200
Response size:          22.2 KB
Response time:          87 ms

RESULT: N+1. 1 request ran 201 SQL statements: 1 query for the list, then 200 more, one row at a time.
```

One HTTP request. 201 trips to the database. The status is 200 and the JSON is correct, which is
what makes this problem easy to ship: no test fails, no error is logged.

### See the SQL

```bash
./demo --sql
```

```
     1  select o1_0.id,o1_0.customer_id,o1_0.placed_at,o1_0.status from orders o1_0 order by o1_0.id
     2  select c1_0.id,c1_0.city,c1_0.email,c1_0.name from customer c1_0 where c1_0.id=?
     3  select c1_0.id,c1_0.city,c1_0.email,c1_0.name from customer c1_0 where c1_0.id=?
   ...
   201  select c1_0.id,c1_0.city,c1_0.email,c1_0.name from customer c1_0 where c1_0.id=?
```

Statement 1 reads the orders. It does not mention the `customer` table. Statements 2 to 201 are the
same text 200 times. Only the `?` changes: a different customer id each time.

### Find it in the code

`OrderService.list`:

```java
case NAIVE -> summaries(orders.findAll(BY_ID));        // statement 1: the orders
```

`OrderSummary.from`, called once per order:

```java
order.getCustomer().getName(),                         // statements 2 to 201: one customer each
order.getCustomer().getCity());
```

- **The "1"** is the query for the list.
- **The "N"** is one more query per row of that list, for something the first query did not bring.

Nobody wrote a loop with a query in it. The loop is `stream().map(...)` and the query hides behind a
getter.

### It grows with the data

```bash
./demo --orders 1000
```

```
1 request, 1001 SQL statements

      1 x  select (4 columns) from orders o1_0 order by o1_0.id
   1000 x  select (4 columns) from customer c1_0 where c1_0.id=?
```

In development you have 10 orders and 11 statements, and it feels instant. In production the same
code runs thousands.

### What N really counts

```bash
./demo --orders 200 --customers 20
```

```
1 request, 21 SQL statements

      1 x  select (4 columns) from orders o1_0 order by o1_0.id
     20 x  select (4 columns) from customer c1_0 where c1_0.id=?
```

Still 200 orders, but only 21 statements. Hibernate keeps every entity it has loaded in the session
(the persistence context). When order 21 asks for customer 1 again, Hibernate already has it and
does not go back to the database. So N is the number of **different customers**, not the number of
orders. In the worst case they are the same number.

### One more number, for Task 2

```bash
./demo --endpoint report
```

```
GET /api/orders/report

1 request, 201 SQL statements

      1 x  select (4 columns) from orders o1_0
    200 x  select (4 columns) from customer c1_0 where c1_0.id=?

Response body:          {"NEW":66,"PAID":67,"SHIPPED":67}

RESULT: N+1. 1 request ran 201 SQL statements: 1 query for the list, then 200 more, one row at a time.
```

Open `OrderService.statusReport`. It counts orders per status. It never calls `getCustomer()`. And
it still loads 200 customers. Remember that.

### Check

```bash
./check 1
```

```
Task 1: See the N+1
  [PASS] Naive: 200 orders, 200 customers: 201 statements, 1 for the orders + 1 per customer (this documents the bug)
  [PASS] Naive: 200 orders, 20 customers: 21 statements, because a customer already in the session is not loaded twice
  2 of 2 pass
```

These tests pass on purpose. They pin the bug down. The `naive` fetch plan stays like this for ever
so you can compare the fixes against it.

---

## Task 2: EAGER Is Not the Fix. Make the Associations LAZY

**The idea people have:** "The customers are loaded lazily, one by one. Make the association EAGER
and Hibernate will load them with the orders."

### Look at the mapping first

```bash
./inspect
```

Section 1:

```
1. FETCH TYPES (what Hibernate read from your entity classes)

  association        kind         fetch  loads a
  -----------------  -----------  -----  ---------------
  Customer.orders    one-to-many  LAZY   List<Order>
  Order.customer     many-to-one  EAGER  Customer
  Order.items        one-to-many  LAZY   List<OrderItem>
  OrderItem.order    many-to-one  EAGER  Order
  OrderItem.product  many-to-one  EAGER  Product
```

`Order.customer` is **already EAGER**. Open `Order.java`: the annotation is a bare `@ManyToOne`.
Nobody chose EAGER. It is the default.

| Association | Default |
|---|---|
| `@ManyToOne`, `@OneToOne` (to-one) | **EAGER** |
| `@OneToMany`, `@ManyToMany` (to-many) | **LAZY** |

So the 201 statements you saw in Task 1 were produced by an EAGER association. EAGER does not mean
"with a join". It only means "load it now, not later". For a query like `findAll`, Hibernate runs
the query as written, sees 200 orders whose customer must be loaded now, and loads them one by one.

And EAGER has a second cost. That is the report from Task 1: it never uses a customer, and it pays
for 200 of them, because EAGER applies to every code path that loads an order.

### Before

```bash
./check 2
```

0 of 3 pass.

### The change

In `Order.java`:

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "customer_id")
private Customer customer;
```

In `OrderItem.java`, both associations:

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "order_id")
private Order order;

@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "product_id")
private Product product;
```

Import `jakarta.persistence.FetchType` in both files.

### After

```bash
./demo --endpoint report
```

```
GET /api/orders/report

1 request, 1 SQL statement

      1 x  select (4 columns) from orders o1_0

Response body:          {"NEW":66,"PAID":67,"SHIPPED":67}

RESULT: OK. 1 request, 1 SQL statement.
```

The report went from 201 statements to 1 without touching the report's code.

```bash
./demo
```

```
1 request, 201 SQL statements
```

The list still runs 201. That is expected. The list **does** use the customer, so the customers are
still loaded one by one, now at the moment `getCustomer().getName()` is called instead of right
after the query. LAZY stops you paying for data you do not use. It does not fix the N+1 for data
you do use. That is Task 6.

```bash
./check 2
```

```
Task 2: EAGER is not the fix. Make the associations LAZY
  [PASS] LAZY: loading one order leaves its customer as a proxy that is not loaded yet
  [PASS] LAZY: loading an order's items leaves each item's product as a proxy that is not loaded yet
  [PASS] LAZY: the status report reads 200 orders with 1 statement and loads no customer
  3 of 3 pass
```

---

## Task 3: Inspect Lazy Loading: the Proxy, the Exception, Open-in-View

```bash
./inspect
```

### Step 1: the fetch types

```
  association        kind         fetch  loads a
  -----------------  -----------  -----  ---------------
  Customer.orders    one-to-many  LAZY   List<Order>
  Order.customer     many-to-one  LAZY   Customer
  Order.items        one-to-many  LAZY   List<OrderItem>
  OrderItem.order    many-to-one  LAZY   Order
  OrderItem.product  many-to-one  LAZY   Product
```

All LAZY now. `Order.items` and `Customer.orders` were LAZY before you changed anything: to-many is
lazy by default.

### Step 2: what is in the field

```
2. ORDER 1, JUST LOADED: entityManager.find(Order.class, 1)

     sql: select o1_0.id,o1_0.customer_id,o1_0.placed_at,o1_0.status from orders o1_0 where o1_0.id=?

  What is in the order's fields right now, before anyone used them:

  field           loaded?  class of the object in the field
  --------------  -------  --------------------------------------------------------
  order.customer  NO       com.amigoscode.fetching.customer.Customer$HibernateProxy
  order.items     NO       org.hibernate.collection.spi.PersistentBag
```

One statement, and it reads only the `orders` table.

`order.customer` is not null and it is not a `Customer`. It is a `Customer$HibernateProxy`: a class
Hibernate generated at startup that **extends** `Customer`. Your code cannot tell the difference,
because it is a `Customer` as far as Java is concerned. The proxy holds two things: the customer's
id, and a reference to the session that created it.

Where does the id come from? From `o1_0.customer_id` in the statement above. The foreign key is in
the order row, so Hibernate knows *which* customer it is without loading it.

(In `start/` before Task 2 the same line said `yes` and `com.amigoscode.fetching.customer.Customer`:
the real class, loaded with a join.)

### Step 3: when the proxy runs its query

```
3. USE THEM, ONE AT A TIME (still inside the transaction, the session is open)

  order.getCustomer().getId()           -> 1            0 statements
  order.getCustomer().getName()         -> Customer 1   1 statement
         sql: select c1_0.id,c1_0.city,c1_0.email,c1_0.name from customer c1_0 where c1_0.id=?
  order.getCustomer().getCity()         -> Lagos        0 statements
```

- `getId()`: 0 statements. The proxy already knows the id.
- `getName()`: 1 statement. The first call to anything else makes the proxy ask its session to load
  the whole customer row. This is called **initializing** the proxy.
- `getCity()`: 0 statements. The proxy is initialized. It stays that way.

That one statement is the "+1" from Task 1. In the list it happens 200 times.

### Step 4: the collection

```
  order.getItems().size()               -> 2            1 statement
         sql: select i1_0.order_id,i1_0.id,i1_0.product_id,i1_0.quantity from order_item i1_0 where i1_0.order_id=?
  items.get(0).getProduct().getName()   -> Desk lamp    1 statement
         sql: select p1_0.id,p1_0.name,p1_0.price from product p1_0 where p1_0.id=?
```

`order.items` is a `PersistentBag`. It is Hibernate's own `List` implementation, a wrapper that
loads its rows the first time you read from it (`size()`, `get()`, iterating). Same idea as the
proxy: a placeholder plus a session.

Each item's product is a proxy again, so reading its name is one more statement.

### Step 5: after the transaction

```
4. AFTER THE TRANSACTION (the session is closed)

  Order 2 was loaded in the same transaction, and nobody used its customer or items.

  order.getCustomer().getName()
     -> org.hibernate.LazyInitializationException:
        Could not initialize proxy [com.amigoscode.fetching.customer.Customer#2] - no session
  order.getItems().size()
     -> org.hibernate.LazyInitializationException:
        Cannot lazily initialize collection of role 'com.amigoscode.fetching.order.Order.items' with key '2' (no session)
```

Both messages end the same way: **no session**. A proxy needs its session to run its query. When the
transaction ended, the session was closed. The proxy is still in the field, it still knows it is
customer 2, and it has nothing left to load it with.

### Step 6: the same thing in the application

```bash
./demo --endpoint order
```

```
GET /api/orders/1

1 request, 1 SQL statement

      1 x  select (4 columns) from orders o1_0 where o1_0.id=?

HTTP status:            500

RESULT: FAILED. 500 LazyInitializationException: Could not initialize proxy [com.amigoscode.fetching.customer.Customer#1] - no session
```

`OrderService.details` is not transactional. `orders.findById(id)` is a repository call: it opens a
transaction, loads the order, and closes the transaction (and the session) before it returns. Then
`OrderDetails.from(order)` runs, and its first line is `order.getCustomer().getName()`. No session.

Before Task 2 this request failed too, with the other message
(`Cannot lazily initialize collection of role '...Order.items'`). The customer was EAGER then, so
the first line worked and the code got as far as the items, which were always lazy. Making the
customer lazy did not introduce the bug. It moved it one line up.

### Step 7: open-in-view

```bash
./demo --endpoint order --open-in-view --sql
```

```
GET /api/orders/1
Shop: 200 orders, 200 customers, 401 order items, 20 products. open-in-view: ON.

1 request, 5 SQL statements

     1  select o1_0.id,o1_0.customer_id,o1_0.placed_at,o1_0.status from orders o1_0 where o1_0.id=?
     2  select c1_0.id,c1_0.city,c1_0.email,c1_0.name from customer c1_0 where c1_0.id=?
     3  select i1_0.order_id,i1_0.id,i1_0.product_id,i1_0.quantity from order_item i1_0 where i1_0.order_id=?
     4  select p1_0.id,p1_0.name,p1_0.price from product p1_0 where p1_0.id=?
     5  select p1_0.id,p1_0.name,p1_0.price from product p1_0 where p1_0.id=?

HTTP status:            200

RESULT: OK. 1 request, 5 SQL statements.
```

The same broken code answers 200.

**What open-in-view is.** `spring.jpa.open-in-view=true` makes Spring open the Hibernate session
when the HTTP request arrives and close it only after the response has been written. The session
now outlives every transaction in the request. A proxy touched in the service, in the controller, or
by the JSON serializer still has a session, so it loads.

It is **on by default** in Spring Boot. If you do not set the property, Boot logs this at startup:

```
[WARN] spring.jpa.open-in-view is enabled by default. Therefore, database queries may be performed during view rendering. Explicitly configure spring.jpa.open-in-view to disable this warning
```

This project sets it to `false` in `application.properties`.

**Why it is not a fix.**

- The queries did not go away. Statements 2 to 5 still ran, one by one. They now run outside the
  service method and outside its transaction, in places where nobody expects SQL.
- It hides the next N+1. Run `./demo --open-in-view`: the list still runs 201 statements. With
  open-in-view on you never get the exception that tells you "this code reads data the query did
  not load".
- The fix is one switch for the whole application, so it hides every such place at once.

With open-in-view off, a `LazyInitializationException` is an early warning. Keep the warning and fix
the cause.

### What to take away

- A lazy to-one association is a **proxy**: a generated subclass that knows the id and runs one
  select the first time you call anything but the id getter.
- A lazy collection is a **wrapper** (`PersistentBag` for a `List`) that runs one select the first
  time you read it.
- Both need the session they came from. No session: `LazyInitializationException`.
- Open-in-view keeps the session open for the whole request. It removes the exception, not the
  queries.

---

## Task 4: Fix the LazyInitializationException

**The idea:** read everything the response needs while the session is open. The session is open
for as long as the transaction is. So make the method that does the reading transactional.

### Before

```bash
./check 4
```

```
Task 4: Fix the LazyInitializationException
  [FAIL] Details: orders 1 to 30 all answer 200, and each total is quantity x price added up over its items
           GET /api/orders/1 failed with org.hibernate.LazyInitializationException: Could not initialize proxy [com.amigoscode.fetching.customer.Customer#1] - no session
  [FAIL] Details: GET /api/orders/1 answers 200 with the customer's name and both items, not a LazyInitializationException
           GET /api/orders/1 failed with org.hibernate.LazyInitializationException: Could not initialize proxy [com.amigoscode.fetching.customer.Customer#1] - no session
  [PASS] Details: spring.jpa.open-in-view is still false (already passing: keep it that way)
  1 of 3 pass
```

### The change

In `OrderService`:

```java
@Transactional(readOnly = true)
public OrderDetails details(long id) {
    Order order = orders.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
    return OrderDetails.from(order);
}
```

One annotation. The import is already there: the other methods in the class use it.

Why it works:

- The transaction now starts when `details` is called and ends when it returns. `findById` joins
  that transaction instead of opening its own.
- `OrderDetails.from(order)` runs **inside** the method, so the session is still open when it
  touches the customer, the items and the products.
- The method returns an `OrderDetails` record: plain data. Nothing lazy leaves the service, so
  nothing can fail later in the controller or the serializer.

That last point is the rule to remember: **a service returns DTOs, not entities.** An entity that
leaves the transaction is full of placeholders that can no longer be filled.

### After

```bash
./demo --endpoint order
```

```
1 request, 5 SQL statements

HTTP status:            200

RESULT: OK. 1 request, 5 SQL statements.
```

```bash
./check 4
```

```
Task 4: Fix the LazyInitializationException
  [PASS] Details: orders 1 to 30 all answer 200, and each total is quantity x price added up over its items
  [PASS] Details: GET /api/orders/1 answers 200 with the customer's name and both items, not a LazyInitializationException
  [PASS] Details: spring.jpa.open-in-view is still false (already passing: keep it that way)
  3 of 3 pass
```

---

## Task 5: Inspect the Transaction Boundary

### Step 1: the five statements

```bash
./demo --endpoint order --sql
```

```
     1  select o1_0.id,o1_0.customer_id,o1_0.placed_at,o1_0.status from orders o1_0 where o1_0.id=?
     2  select c1_0.id,c1_0.city,c1_0.email,c1_0.name from customer c1_0 where c1_0.id=?
     3  select i1_0.order_id,i1_0.id,i1_0.product_id,i1_0.quantity from order_item i1_0 where i1_0.order_id=?
     4  select p1_0.id,p1_0.name,p1_0.price from product p1_0 where p1_0.id=?
     5  select p1_0.id,p1_0.name,p1_0.price from product p1_0 where p1_0.id=?
```

| Statement | Line of Java |
|---|---|
| 1 | `orders.findById(id)` |
| 2 | `order.getCustomer().getName()` |
| 3 | `order.getItems().stream()` |
| 4, 5 | `item.getProduct().getName()`, once per item |

These are the same five statements as with open-in-view in Task 3. The difference is where they
run: all inside `details`, inside one transaction.

### Step 2: three items

```bash
./demo --endpoint order --id 2
```

Order 2 has three items, and the request runs 6 statements: one more product select. One query for
the items, then one per item for its product. That is an N+1 again, a small one. It is fine for one
order with a few items. Task 9 removes it.

### Step 3: open-in-view again

```bash
./demo --endpoint order --open-in-view
```

Still 5 statements. With the method transactional, open-in-view changes nothing you can see. All it
ever did was keep the session open after the transaction. Now nothing needs that.

### Step 4: where the session begins and ends

It opens when `details` is entered (Spring's transaction proxy does that before your first line) and
closes when `details` returns. If the controller called `OrderDetails.from(order)`, that would be
after the return: no session, and you are back to the exception.

### Step 5: `readOnly = true`

It tells Spring and Hibernate that this transaction will not change anything. Hibernate then skips
the work it normally does to detect changes to loaded entities when the transaction ends. It does
not change which queries run. Use it on every method that only reads.

### What to take away

- The session lives exactly as long as the transaction (with open-in-view off).
- Do the mapping to a DTO inside the transactional method.
- The exception is fixed, but the endpoint still loads its data in several small statements.

---

## Task 6: Fix the N+1 With JOIN FETCH and With @EntityGraph

**The idea:** the list needs the customers, and Hibernate has to be told that before it runs the
query, so it can bring them back in the same statement with a join.

### Before

```bash
./check 6
```

0 of 4 pass. In `OrderService.list`, the `JOIN_FETCH` and `ENTITY_GRAPH` plans still call
`findAll`.

### Step 1: JOIN FETCH

In `OrderRepository`:

```java
@Query("select o from Order o join fetch o.customer order by o.id")
List<Order> findAllJoinFetchCustomer();
```

Import `java.util.List`.

`join o.customer` alone would only join the table, for filtering or sorting. `join fetch` also
means: use the joined columns to fill `order.customer` with a real, loaded `Customer`.

In `OrderService.list`:

```java
case JOIN_FETCH -> summaries(orders.findAllJoinFetchCustomer());
```

```bash
./demo --fetch join-fetch --sql
```

```
GET /api/orders?fetch=join-fetch

1 request, 1 SQL statement

     1  select o1_0.id,c1_0.id,c1_0.city,c1_0.email,c1_0.name,o1_0.placed_at,o1_0.status from orders o1_0 join customer c1_0 on c1_0.id=o1_0.customer_id order by o1_0.id

RESULT: OK. 1 request, 1 SQL statement.
```

201 statements became 1.

### Step 2: @EntityGraph

Same result, without writing JPQL. In `OrderRepository`:

```java
@EntityGraph(attributePaths = "customer")
List<Order> findAllByOrderById();
```

Import `org.springframework.data.jpa.repository.EntityGraph`.

Spring Data builds the query from the method name: `findAllBy` (no condition) + `OrderById`. The
annotation adds: "and load the `customer` attribute with it".

In `OrderService.list`:

```java
case ENTITY_GRAPH -> summaries(orders.findAllByOrderById());
```

Do not put the annotation on `findAll` itself. The naive plan calls `findAll`, and it has to stay
naive.

```bash
./demo --fetch entity-graph --sql
```

```
1 request, 1 SQL statement

     1  select o1_0.id,c1_0.id,c1_0.city,c1_0.email,c1_0.name,o1_0.placed_at,o1_0.status from orders o1_0 left join customer c1_0 on c1_0.id=o1_0.customer_id order by o1_0.id

RESULT: OK. 1 request, 1 SQL statement.
```

### Check

```bash
./check 6
```

```
Task 6: Fix the N+1 with JOIN FETCH and with @EntityGraph
  [PASS] @EntityGraph: OrderRepository has a method with @EntityGraph(attributePaths = "customer") and no 'join fetch' of its own
  [PASS] JOIN FETCH: OrderRepository has a @Query that returns a List and says 'join fetch o.customer'
  [PASS] JOIN FETCH: the list of 200 orders runs 1 statement and returns the same JSON as the naive list
  [PASS] @EntityGraph: the list of 200 orders runs 1 statement and returns the same JSON as the naive list
  4 of 4 pass
```

`./demo` (naive) must still print 201.

---

## Task 7: Fix the N+1 With a DTO Projection

**The idea:** the list shows five values per order. Ask the database for exactly those five and
build the `OrderSummary` straight from the row. No `Order`, no `Customer`, no proxy.

### The change

In `OrderRepository`:

```java
@Query("""
        select new com.amigoscode.fetching.order.OrderSummary(o.id, o.status, o.placedAt, c.name, c.city)
          from Order o
          join o.customer c
         order by o.id
        """)
List<OrderSummary> findAllSummaries();
```

`select new ...(...)` is a JPQL constructor expression: for every row, Hibernate calls that
constructor with the selected values. The arguments must be in the order of the record's
components.

It is a plain `join`, not `join fetch`. There is no entity to fill.

In `OrderService.list`:

```java
case PROJECTION -> orders.findAllSummaries();
```

No `summaries(...)` call: the repository already returns the DTOs.

### After

```bash
./demo --fetch projection --sql
```

```
GET /api/orders?fetch=projection

1 request, 1 SQL statement

     1  select o1_0.id,o1_0.status,o1_0.placed_at,c1_0.name,c1_0.city from orders o1_0 join customer c1_0 on c1_0.id=o1_0.customer_id order by o1_0.id

RESULT: OK. 1 request, 1 SQL statement.
```

```bash
./check 7
```

```
Task 7: Fix the N+1 with a DTO projection
  [PASS] Projection: the statement selects only the 5 columns the list shows, not whole entities
  [PASS] Projection: the list of 200 orders runs 1 statement and returns the same JSON as the naive list
  2 of 2 pass
```

---

## Task 8: Inspect the Three Fixes

### Step 1 and 2: the three statements side by side

```
join-fetch    select o1_0.id,c1_0.id,c1_0.city,c1_0.email,c1_0.name,o1_0.placed_at,o1_0.status
              from orders o1_0 join customer c1_0 on c1_0.id=o1_0.customer_id order by o1_0.id

entity-graph  select o1_0.id,c1_0.id,c1_0.city,c1_0.email,c1_0.name,o1_0.placed_at,o1_0.status
              from orders o1_0 left join customer c1_0 on c1_0.id=o1_0.customer_id order by o1_0.id

projection    select o1_0.id,o1_0.status,o1_0.placed_at,c1_0.name,c1_0.city
              from orders o1_0 join customer c1_0 on c1_0.id=o1_0.customer_id order by o1_0.id
```

| | Join | Columns |
|---|---|---|
| JOIN FETCH | `join` (inner) | 7: every column of both entities |
| @EntityGraph | `left join` | 7: every column of both entities |
| Projection | `join` (inner) | 5: what the list shows |

The two entity fixes select `c1_0.email` and the customer's id. The list shows neither. An entity
is always loaded whole. With two small tables that is two columns. With a 40-column table it is most
of the row.

### Step 3: an order without a customer

`join fetch` is an inner join, because that is what you wrote. An inner join drops orders that have
no customer. The entity graph uses a left join, so it keeps them, with `customer = null`. If the
association can be null and you want those rows, write `left join fetch`.

### Step 4: the mapping did not change

`./inspect` still says `Order.customer  many-to-one  LAZY`. The fetch type in the mapping is the
**default**: what happens when a query says nothing. `JOIN FETCH` and `@EntityGraph` override it
**for one query**. That is why "everything LAZY in the mapping, fetch per query" works: each use
case loads exactly what it needs, and no other code path pays for it.

### Step 5: the list must also show the email

- JOIN FETCH and @EntityGraph: add the field to `OrderSummary` and to `OrderSummary.from`. The query
  already loads it.
- Projection: add it to `OrderSummary` **and** to the query. More typing, and the query never
  loads more than the screen shows.

### Step 6: can you change what a projection returned?

No. `OrderSummary` is a record that Hibernate created and forgot. It is not an entity and it is not
in the persistence context, so there is nothing for Hibernate to watch or save. Projections are for
reading. When you want to change an order, load the `Order` entity.

### What to take away

- All three run one statement.
- JOIN FETCH: you write the query, inner join unless you say `left`.
- @EntityGraph: declared on the method, works with derived queries, left join.
- Projection: only the columns you need, no entities, read-only.
- The mapping sets the default. The query decides.

---

## Task 9: The Collection Trap: Paging, Two Bags, @BatchSize (mid-level)

So far the fetched association was a to-one: one customer per order, so the join returns one row per
order. A collection is different. An order with 3 items comes back as 3 rows.

### See it

```bash
./demo --endpoint page --sql
```

```
GET /api/orders/page?page=0&size=20
Shop: 200 orders, 200 customers, 401 order items, 20 products. open-in-view: off.

[WARN] HHH90003004: firstResult/maxResults specified with collection fetch; applying in memory

1 request, 2 SQL statements

     1  select o1_0.id,c1_0.id,c1_0.city,c1_0.email,c1_0.name,i1_0.order_id,i1_0.id,p1_0.id,p1_0.name,p1_0.price,i1_0.quantity,o1_0.placed_at,o1_0.status from orders o1_0 join customer c1_0 on c1_0.id=o1_0.customer_id join order_item i1_0 on o1_0.id=i1_0.order_id join product p1_0 on p1_0.id=i1_0.product_id order by o1_0.id
     2  select count(o1_0.id) from orders o1_0

HTTP status:            200
Orders in the response: 20

RESULT: PAGED IN MEMORY. No statement has a limit. The database returned every order, and Hibernate cut out the 20 you asked for in Java.
```

Two statements and a correct response. It looks like the best result so far. Read statement 1 to the
end: there is **no limit**. You asked for 20 orders. The database sent all 200 orders with all 401
items, and Hibernate threw 180 orders away in memory.

Why? `findPage` says `join fetch o.items`. The join returns one row per item. "The first 20 rows"
would be about 10 orders, the last one maybe cut in half. The database cannot say "the first 20
orders" in a query whose rows are items. So Hibernate does not send a limit at all, reads
everything, and counts orders itself. It tells you so in the log:

```
HHH90003004: firstResult/maxResults specified with collection fetch; applying in memory
```

With 200 orders nobody notices. With 2 million, every request for page 0 loads the whole table into
memory.

(You can make Hibernate refuse instead of warn:
`spring.jpa.properties.hibernate.query.fail_on_pagination_over_collection_fetch=true`. The request
then fails with `setFirstResult() or setMaxResults() specified with collection fetch join`. A good
setting to have on.)

### Step 1: take the collection out of the paged query

In `OrderRepository.findPage`:

```java
@Query(value = "select o from Order o join fetch o.customer",
        countQuery = "select count(o) from Order o")
Page<Order> findPage(Pageable pageable);
```

The customer stays: a to-one join does not multiply rows, so it is safe to page.

```bash
./demo --endpoint page
```

```
1 request, 42 SQL statements

      1 x  select (7 columns) from orders o1_0 join customer c1_0 on c1_0.id=o1_0.customer_id order by o1_0.id fetch first ? rows only
      1 x  select count(o1_0.id) from orders o1_0
     20 x  select (4 columns) from order_item i1_0 where i1_0.order_id=?
     20 x  select (3 columns) from product p1_0 where p1_0.id=?

RESULT: N+1. 1 request ran 42 SQL statements: 1 query for the list, then 41 more, one row at a time.
```

The first statement now ends in `fetch first ? rows only`: the database does the paging. (On later
pages it is `offset ? rows fetch first ? rows only`.) But the items and products are lazy again, so
the N+1 is back: 20 selects for the items of 20 orders, 20 for the products.

### Step 2: load the collections in batches

In `Order.java`:

```java
@OneToMany(mappedBy = "order")
@BatchSize(size = 50)
private List<OrderItem> items = new ArrayList<>();
```

In `Product.java`, on the class:

```java
@Entity
@Table(name = "product")
@BatchSize(size = 50)
public class Product {
```

Import `org.hibernate.annotations.BatchSize` in both.

`@BatchSize` does not change *when* things are loaded. They are still lazy. It changes *how many* are
loaded at once: when the items of one order are needed, Hibernate looks at the other orders in the
session whose items are still not loaded, and loads up to 50 of them with the same statement. On
the class, it does the same for `Product` proxies.

### After

```bash
./demo --endpoint page --sql
```

```
1 request, 4 SQL statements

     1  select o1_0.id,c1_0.id,c1_0.city,c1_0.email,c1_0.name,o1_0.placed_at,o1_0.status from orders o1_0 join customer c1_0 on c1_0.id=o1_0.customer_id order by o1_0.id fetch first ? rows only
     2  select count(o1_0.id) from orders o1_0
     3  select i1_0.order_id,i1_0.id,i1_0.product_id,i1_0.quantity from order_item i1_0 where i1_0.order_id = any (?)
     4  select p1_0.id,p1_0.name,p1_0.price from product p1_0 where p1_0.id = any (?)

RESULT: OK. 1 request, 4 SQL statements.
```

Four statements, whatever is on the page: the page of orders, the count, all their items, all their
products. And the database only reads 20 orders.

```bash
./check 9
./check
```

`./check` should now print 24 passes and no failures.

### Two bags

```bash
./inspect --two-bags
```

```
5. TWO BAGS: fetch two List collections in one query

  select c from Customer c join fetch c.orders o join fetch o.items
     -> org.hibernate.loader.MultipleBagFetchException:
        cannot simultaneously fetch multiple bags: [com.amigoscode.fetching.order.Order.items, com.amigoscode.fetching.customer.Customer.orders]
```

A **bag** is Hibernate's name for a `List` with no order column: a collection that may hold
duplicates. Join two collections in one statement and the rows multiply: a customer with 10 orders
of 3 items each comes back as 30 rows. Hibernate cannot tell from those rows which repeats are real
and which come from the join, so it refuses the query.

The answer is the same as for paging: fetch at most one collection in a query, and let the others
load in batches.

---

## Task 10: Compare Everything as the Data Grows

### Step 1: what batch fetching sends

In the page output above, statements 3 and 4 end in `= any (?)`. The single parameter is an
**array**: the ids of all 20 orders on the page in statement 3 (from `@BatchSize` on `Order.items`),
and the ids of all the different products in statement 4 (from `@BatchSize` on `Product`). On other
databases the same thing is written `in (?, ?, ?, ...)`.

### Step 2: two bags

See the end of Task 9. 10 orders x 3 items = 30 rows for one customer, where 1 + 10 + 30 values
would do.

### Step 3: the list at three sizes

```bash
./demo --orders 2000 --fetch naive
./demo --orders 2000 --fetch join-fetch
./demo --orders 2000 --fetch projection
```

Numbers from one run on a busy laptop. Your times will differ, the shape will not.

| Orders | naive | join-fetch | projection |
|---|---|---|---|
| 200 | 201 statements, 676 ms | 1 statement, 47 ms | 1 statement, 17 ms |
| 2,000 | 2,001 statements, 3,224 ms | 1 statement, 43 ms | 1 statement, 39 ms |
| 20,000 | 20,001 statements, 19,540 ms | 1 statement, 252 ms | 1 statement, 200 ms |

The naive time grows in step with the number of statements. Each statement is a round trip: send,
wait, receive. Against a database on another machine every round trip costs more than it does here,
where Postgres is on the same laptop. The single-statement fixes barely move.

### Step 4: batch fetching for the whole application

```bash
./demo --batch-size 50
```

```
GET /api/orders?fetch=naive
Shop: 200 orders, 200 customers, 401 order items, 20 products. open-in-view: off. default_batch_fetch_size: 50.

1 request, 5 SQL statements

      1 x  select (4 columns) from orders o1_0 order by o1_0.id
      4 x  select (4 columns) from customer c1_0 where c1_0.id = any (?)

RESULT: BATCHED. 5 statements instead of one per row. The number still grows with the data, one more for every 50 rows.
```

The naive code, unchanged, with `spring.jpa.properties.hibernate.default_batch_fetch_size=50`: 201
statements became 5. 200 customers in batches of 50 is 4 statements, plus the 1 for the orders.

```bash
./demo --orders 2000 --batch-size 50
```

41 statements: 1 + 2000 / 50.

This is the cheapest improvement there is: one property, no code. Many teams set it as a safety net.
It is not the same as a join: it is still a second (and third, and fourth) statement, and the count
still grows with the data.

| | Statements for N rows | You change |
|---|---|---|
| Naive | 1 + N | nothing |
| Batch fetching | 1 + N / size | one annotation or one property |
| JOIN FETCH / @EntityGraph | 1 | one query |
| Projection | 1 | one query and it returns DTOs |

### Step 5: one million orders

```bash
./demo --orders 2000 --customers 50
```

51 statements: 2000 orders, 50 different customers.

"You load 1M orders and each has a lazy customer. What happens?"

1. The query for the orders is **one statement**. No customer is loaded. Lazy means nothing happens
   yet.
2. But Hibernate now holds a million `Order` objects and a million proxies in the persistence
   context. Memory is the first problem, before any extra query.
3. If the code then touches each order's customer, Hibernate runs one select per **different**
   customer: up to a million round trips. Minutes, at best.
4. So: do not load a million entities. Page it or stream it. Select only the columns you need with a
   projection. Get the customer's columns in the same statement with a join.

### Step 6: which fix where

| Situation | Fix | Why |
|---|---|---|
| The order list | Projection | Read-only, five columns, nothing lazy left to go wrong |
| A screen that edits one order | Entities, with JOIN FETCH or an entity graph for what the screen shows | You need the entity so Hibernate can save the change |
| A page of orders with their items | Page query that fetches only to-one, `@BatchSize` for the collection | A collection fetch cannot be paged in the database |
| The status report | LAZY mapping (and better: `select status, count(*) ... group by status`) | It should never load a customer, or even an order |
| Everything else | `default_batch_fetch_size` as a safety net | Turns the N+1 you have not found yet into a handful of statements |

### How to say it in an interview

> "N+1 is when one query loads a list and then the code touches an association of each row that the
> query did not fetch, so Hibernate runs one more query per row. Making the association EAGER does
> not fix it: to-one associations are EAGER by default, and for a query Hibernate still loads them
> one by one, and now on every code path. So I map everything LAZY and fetch per use case. A lazy
> association is a proxy that knows the id and runs a select the first time it is used, and it needs
> an open session for that. That is why reading it after the transaction gives a
> LazyInitializationException. I fix that by mapping to a DTO inside the transactional service
> method, not by turning on open-in-view, which only keeps the session open and hides the queries.
> For the N+1 itself I fetch in the same statement: JOIN FETCH or an entity graph when I need
> entities, a DTO projection for read-only lists. For collections with pagination I do not join
> fetch, because Hibernate would page in memory. I fetch the to-one side in the paged query and
> batch the collections with @BatchSize. And I prove it by counting statements in a test."

Then be ready for the follow-ups in "Questions to Ask Afterwards" at the end of
[`INTERVIEW_TASKS.md`](INTERVIEW_TASKS.md).

---

## Clean up

```bash
docker compose down
```
