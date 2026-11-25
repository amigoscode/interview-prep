# Guided Tasks — Pagination

The same nine tasks as [`INTERVIEW_TASKS.md`](INTERVIEW_TASKS.md), step by step: the command to run,
the output you should see, why it happens, and the change to make.

Commands are for macOS and Linux. On Windows type `.\demo`, `.\inspect` and `.\check`.

All output below was copied from real runs. **Timings differ on every machine and on every run.**
The row counts, the number of SQL statements and the `RESULT` lines should match. Page counts can
be off by a few.

**Before you start**

```bash
cd start
docker compose up -d
```

**Three things to know about the tools**

- `./demo` and `./inspect` start the real application and send it real HTTP requests. For every
  request they print the SQL the application sent, then run each statement again with
  `EXPLAIN (ANALYZE, BUFFERS)` to see what Postgres did. "Rows read" and "pages read" come from there.
- Page numbers in the URL start at 0, the Spring Data way. The demo says "page 1" for `page=0` and
  "page 5,000" for `page=4999`.
- You change an index by editing `src/main/resources/indexes.sql`. The next command prints
  `indexes.sql changed: rebuilding 2 indexes...` and applies it.

---

## Task 1: See It Break

No code in this task. You are collecting evidence.

### Run it

```bash
./demo
```

The very first run prints `Seeding 1,000,000 orders (first run only)...`. It takes a few seconds.

### What you should see

```
Mode: offset | page size: 20 | depth: page 5,000 of 50,000
Table orders: 1,000,000 rows, newest first.

--- Page 1 ---
GET /api/orders?page=0&size=20
    200 OK, 20 orders, 32 ms
    SQL statements sent: 2
      1. select ... from orders o1_0 order by o1_0.created_at desc offset ? rows fetch first ? rows only     -- ? = 0, 20
         Index Scan Backward using idx_orders_created_at on orders
         rows read: 20 | rows returned: 20 | pages read: 4
      2. select count(o1_0.id) from orders o1_0
         Seq Scan on orders
         rows read: 1,000,000 | rows returned: 1 | pages read: 8,334

--- Page 5,000 ---
GET /api/orders?page=4999&size=20
    200 OK, 20 orders, 35 ms
    SQL statements sent: 2
      1. select ... from orders o1_0 order by o1_0.created_at desc offset ? rows fetch first ? rows only     -- ? = 99980, 20
         Index Scan Backward using idx_orders_created_at on orders
         rows read: 100,000 | rows returned: 20 | pages read: 1,011
      2. select count(o1_0.id) from orders o1_0
         Seq Scan on orders
         rows read: 1,000,000 | rows returned: 1 | pages read: 8,334

                              page 1        page 5,000
Rows returned to the client   20            20
Rows read                     20            100,000
Rows read and thrown away     0             99,980
Pages read (8 kB each)        4             1,011
Time in the database          0.04 ms       13.17 ms   (differs on every run)

RESULT: DEEP OFFSET. Page 5,000 read 100,000 rows to return 20. Page 1 read 20.
RESULT: HIDDEN COUNT. The request also sent a SELECT COUNT that nobody asked for. It read 1,000,000 rows to return one number.
```

Both pages return 20 orders. Page 1 read 20 rows to do it. Page 5,000 read 100,000.

### What OFFSET does

`offset ? rows fetch first ? rows only` is the standard SQL spelling of `LIMIT 20 OFFSET 99980`.
Hibernate writes it that way. Look at the plan:

```bash
./inspect
```

```
  EXPLAIN (ANALYZE, BUFFERS)
    Limit  (cost=2681.19..2681.72 rows=20 width=37) (actual time=14.246..14.250 rows=20 loops=1)
      Buffers: shared hit=1011
      ->  Index Scan Backward using idx_orders_created_at on orders o1_0  (cost=0.42..26813.42 rows=1000000 width=37) (actual time=0.011..11.155 rows=100000 loops=1)
            Buffers: shared hit=1011
```

Read it from the inside out. The `Index Scan` produced **100,000 rows** (`rows=100000`). The `Limit`
above it let 20 through (`rows=20`). The other 99,980 were fetched from the table, handed up, and
dropped.

That is all `OFFSET` is: "produce the rows from the top, and do not give me the first n". The
database has no way to know where row 99,981 is without walking past the 99,980 before it. The
index gives the order, so nothing has to be sorted, but it cannot be asked "where is position
99,981?". An index finds **values**, not positions.

So the cost of a page grows with its number:

```bash
./demo --depth 50000
```

```
Rows read                     20            1,000,000
Pages read (8 kB each)        4             10,061
RESULT: DEEP OFFSET. Page 50,000 read 1,000,000 rows to return 20. Page 1 read 20.
```

The last page reads the whole table. (If you need a refresher on reading `EXPLAIN ANALYZE`, on
pages and on why pages are a better measure than milliseconds: [`../03-indexes`](../03-indexes/),
Task 1.)

### The second statement

You asked for 20 orders and the application sent two statements. The second one,
`select count(o1_0.id) from orders o1_0`, read all 1,000,000 rows. It is sent on **every** request,
page 1 included. Nobody wrote it: remember it for Task 8.

### No limit on the size

```bash
./demo --size 100000
```

```
GET /api/orders?page=0&size=100000
    200 OK, 100,000 orders, 10.7 MB of JSON, 505 ms

RESULT: UNBOUNDED. One request made the server load 100,000 orders and send 10.7 MB.
        Nothing stops a client from asking for size=1000000, or from leaving size out.
```

The server loaded 100,000 entities into memory and wrote 10.7 MB of JSON because one client asked
for it. Open `Paging.java`: a request with no `size` at all gets `Integer.MAX_VALUE`, which means
every order there is.

### Find it in the code

`OrderService.page`:

```java
Page<Order> result = orders.findAll(PageRequest.of(page, size, NEWEST_FIRST));
```

One line, the line every tutorial shows. So far it has three problems: the size is whatever the
client says, deep pages read everything before them, and there is a `COUNT` on every request. Two
more are coming.

### Check

```bash
./check 1
```

```
Task 1: See the deep OFFSET
  [PASS] Offset: page 500 reads more than 10,000 rows to return 20: every row of the 500 pages before it (this documents the problem)
  [PASS] Offset: the first page reads fewer than 100 rows to return 20
  2 of 2 pass
```

These pass on purpose, now and at the end: `OFFSET` cannot be made cheap. (The tests use a database
of 20,000 orders, started by Testcontainers. The demo uses one million.)

---

## Task 2: Fix the Unbounded Page Size

**The idea:** the server decides how much one request may cost, not the client.

### Before

```bash
./check 2
```

```
  [FAIL] Page size: a request without size gets 20 orders, not every order
           [size passed to the service for GET /api/orders]
           expected: 20
           but was: 2147483647
  1 of 6 pass
```

### The change

Replace the two methods in `Paging.java`:

```java
public static final int DEFAULT_SIZE = 20;
public static final int MAX_SIZE = 100;

public static int size(Integer requested) {
    if (requested == null) {
        return DEFAULT_SIZE;
    }
    if (requested < 1 || requested > MAX_SIZE) {
        throw new InvalidPageRequestException("size must be between 1 and " + MAX_SIZE + ", got " + requested);
    }
    return requested;
}

public static int page(Integer requested) {
    if (requested == null) {
        return 0;
    }
    if (requested < 0) {
        throw new InvalidPageRequestException("page must be 0 or more, got " + requested);
    }
    return requested;
}
```

`InvalidPageRequestException` already exists and `ApiExceptionHandler` already maps it to a 400.
All three endpoints call `Paging`, so this one class fixes all of them.

### Reject or clamp?

There are two honest answers to `size=1000000`:

- **Clamp:** quietly use 100. Forgiving, and it is what Spring Data's own `Pageable` argument
  resolver does: we ran it, and `size=1000000` came back as 2,000, its default maximum, while
  `size=0` and `size=-5` came back as 20.
- **Reject:** answer 400. That is what this project does.

The argument for rejecting: a client that asks for 500 rows and silently receives 100 often reads
that as "fewer than I asked for, so this was the last page", and stops. Its data is incomplete and
nothing failed. A 400 is noticed on the first call. Either is acceptable in an interview if you can
say why. No limit at all is not.

### After

```bash
./demo --size 100000
```

```
GET /api/orders?page=0&size=100000
    400 INVALID_PAGE_REQUEST: size must be between 1 and 100, got 100000
    SQL statements sent: 0

RESULT: OK. size=100000 was rejected with a 400 before anything was read from the database.
```

`./check 2` prints 6 of 6.

---

## Task 3: Fix the Unstable Order

### Run it

```bash
./demo --mode history
```

Customer 42 is a business customer with 300 orders. The demo reads all of them through
`GET /api/customers/42/orders`, 20 at a time, the way a browser or an export would.

```
Customer 42 has 300 orders. The demo reads all of them, page by page.
Nothing is inserted, updated or deleted while it reads.

GET /api/customers/42/orders?size=20&page=0
    200 OK, 20 orders, 12 ms
    SQL statements sent: 2
      1. select ... from orders o1_0 where o1_0.customer_id=? order by o1_0.created_at desc fetch first ? rows only     -- ? = 42, 20
         Bitmap Heap Scan on orders + Bitmap Index Scan using idx_orders_customer
         rows read: 300 | rows returned: 20 | pages read: 31
      ...

Pages requested               15
Orders received               300
Different orders received     286
Orders received twice         14
Orders never received         14

For example: order #921,649 was on page=0 and again on page=1.
11 other orders of this customer have exactly the same created_at.

RESULT: UNSTABLE ORDER. Nothing changed in the database, but 14 orders came back twice and 14 never came back.
```

300 orders went out, but only 286 different ones. 14 were sent twice, 14 were never sent. And
nothing was written to the database in between.

### What just happened

The last line of the example is the clue: **12 orders with exactly the same `created_at`**. This
customer's orders arrive a dozen at a time in one transaction.

`ORDER BY created_at DESC` tells Postgres to put newer orders first. It says nothing about two
orders that are equally new. For those, any order is a correct answer, and Postgres is free to give
a different correct answer each time. Look at how it sorts:

```bash
./inspect --mode history
```

```
      ->  Sort  (cost=97.60..97.66 rows=24 width=37) (actual time=1.027..1.072 rows=20 loops=1)
            Sort Key: created_at DESC
            Sort Method: top-N heapsort  Memory: 27kB
```

No index gives this order, so Postgres sorts the customer's 300 rows. Because of the limit it uses a
**top-N heapsort**: it keeps only the best `offset + limit` rows and drops the rest as it goes. For
page 0 it keeps 20, for page 1 it keeps 40, for page 2 it keeps 60. Which of twelve equal rows
survive depends on how many are kept, so the pages do not agree with each other. Twelve orders tie
across the boundary between page 0 and page 1; page 0 shows eight of them, page 1 shows a different
arrangement, and some appear on both while others appear on neither.

Nothing is random and nothing is broken in Postgres. The query asked a question with more than one
right answer.

> You will not always see this. `GET /api/orders` has the same incomplete `ORDER BY`, but there an
> index hands Postgres the rows already in `created_at` order, nothing is sorted, and the ties came
> back the same way in every run. Nothing in the SQL promises that: it is luck that depends on the
> plan. That is why this bug survives testing and appears in production.

### The change

Make the order complete: add a column that is never equal for two rows. In `OrderService`:

```java
static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
```

`id` is the primary key, so `(created_at, id)` is unique. There is now exactly one right answer for
every page. Both offset endpoints use this constant.

### After

```bash
./demo --mode history
```

```
      1. select ... from orders o1_0 where o1_0.customer_id=? order by o1_0.created_at desc,o1_0.id desc fetch first ? rows only     -- ? = 42, 20
...
RESULT: OK. 300 orders, each returned exactly once.
```

`./check 3` prints 3 of 3.

**The rule:** an `ORDER BY` used for pagination must end in a unique column. If the sort column is
not unique, add the primary key as a tie-breaker.

---

## Task 4: Inspect What OFFSET Cannot Fix

### Step 1: the sort key

`./inspect --mode history` now shows `Sort Key: created_at DESC, id DESC`. Still a top-N heapsort,
but there are no equal rows left for it to shuffle.

### Step 2: an order arrives between two requests

```bash
./demo --insert
```

```
GET /api/orders?page=0&size=20
    200 OK, 20 orders, 32 ms
    last 3 orders:   #999,983  #999,982  #999,981

A customer places order #1,000,001. It is now the newest order: first in the list.

GET /api/orders?page=1&size=20
    200 OK, 20 orders, 34 ms
    first 3 orders:  #999,981  #999,980  #999,979

RESULT: SHIFTED. Order #999,981 was returned twice: at the end of page 1 and again at the start of page 2.
        The new order moved every other order down by one, and OFFSET 20 counts from the new top.
```

Order #999,981 was position 20 when page 1 was read. Then a new order took position 1 and pushed
everything down: #999,981 is now position 21. "Skip 20, take 20" starts at position 21. The client
gets it again.

| Position | When page 1 was read | When page 2 was read |
|---|---|---|
| 1 | #1,000,000 | **#1,000,001** (new) |
| ... | ... | ... |
| 20 | #999,981 (last of page 1) | #999,982 |
| 21 | #999,980 | **#999,981** (first of page 2: again) |

The order is perfectly stable here. The tie-breaker from Task 3 is in place and it did not help,
because this is a different problem: `OFFSET` is a **position**, and positions move when rows are
added or removed.

### Step 3: the other direction

Read page 1, then delete one order from page 1, then read page 2. Everything moves **up** by one:
the order that was at position 21 is now at position 20. Page 2 starts at 21, so that order is on
neither page the client saw. It is skipped, silently. For a screen that is an annoyance. For an
export or a sync job it is lost data.

### Step 4 and 5: depth, again

```bash
./demo
```

```
RESULT: DEEP OFFSET. Page 5,000 read 100,001 rows to return 20. Page 1 read 41.
```

Unchanged, as expected. (Page 1 now reads 41 rows, not 20: the index is on `created_at` alone, so
Postgres reads the whole batch of 40 equal rows and sorts it by id. `./inspect` shows it as
`Incremental Sort`. Task 5 fixes that.)

The export that reads every page: page n reads n x 20 rows, so 50,000 pages read
20 x (1 + 2 + ... + 50,000), about **25 billion rows**, to export one million.

`./check 4` prints 2 of 2: one test for the insert, one for the delete. Both pass now and at the
end, because they describe what `OFFSET` is.

**What to take away:** `OFFSET n` reads n rows and throws them away, and no index changes that.
`OFFSET` is a position in a list that keeps changing: an insert repeats a row, a delete skips one.
A unique `ORDER BY` fixes ties. It fixes neither of these.

---

## Task 5: Fix It With Keyset Pagination

**The idea:** do not say "skip 99,980 rows". Say "give me the 20 orders that come after **this
one**". The database can find "this one" in an index in 3 or 4 page reads, wherever it is. The last
row of each page becomes the bookmark for the next: the **cursor**. The technique is called keyset
pagination, cursor pagination or the seek method.

### Before

`GET /api/orders/feed` was started this way and never finished.

```bash
./demo --mode keyset
```

```
--- Page 2: the client sends back the nextCursor of page 1 ---
GET /api/orders/feed?size=20&after=2025-11-25T10%3A00%3A00Z%7C999980
    200 OK, 20 orders, 14 ms
    SQL statements sent: 1
      1. select * from orders where created_at < ? order by created_at desc limit ?     -- ? = '2025-11-25T10:00:00Z', 20
         Index Scan Backward using idx_orders_created_at on orders
         rows read: 20 | rows returned: 20 | pages read: 4
...
RESULT: SKIPPED ROWS. After two pages a client should have seen the 40 newest orders. 20 of them were never returned, for example #1,000,000.
RESULT: SKIPPED ROWS. Page 5,000 comes after 99,980 orders, so it should hold the next 20. 20 of them were not returned, for example #900,020.
```

It is fast: 20 rows read, at any depth. And it is wrong. The newest 40 orders all share one
`created_at`. Page 1 returned 20 of them. Page 2 asked for `created_at < ?`, strictly older, and so
jumped over the other 20. It is Task 3 again: `created_at` alone does not identify a position.

### Step 1: the query

The position of a row is the pair `(created_at, id)`. "After this row" in the order
`created_at DESC, id DESC` means: an older `created_at`, **or** the same `created_at` and a smaller
`id`. SQL can say that in one comparison. In `OrderRepository`, replace the two feed queries:

```java
@Query(value = """
        select * from orders
         order by created_at desc, id desc
         limit :limit
        """, nativeQuery = true)
List<Order> feedFirstPage(@Param("limit") int limit);

@Query(value = """
        select * from orders
         where (created_at, id) < (:createdAt, :id)
         order by created_at desc, id desc
         limit :limit
        """, nativeQuery = true)
List<Order> feedAfter(@Param("createdAt") Instant createdAt, @Param("id") long id, @Param("limit") int limit);
```

`(created_at, id) < (?, ?)` is a **row-value comparison**. It compares like a dictionary: first by
`created_at`, and only if those are equal, by `id`. That is exactly how the `ORDER BY` sorts.

Two wrong versions you will meet:

| Condition | What goes wrong |
|---|---|
| `created_at < ?` | Skips the rest of the rows with the same `created_at` (what the code did) |
| `created_at <= ?` | Returns the rows with the same `created_at` again |
| `created_at < ? AND id < ?` | Loses every older order with a larger id. `(1, 9) < (2, 1)` is true, `1 < 2 AND 9 < 1` is false |

### Step 2: hasNext without counting

In `OrderService`, replace the body of `feed`:

```java
public FeedPage feed(Cursor after, int size) {
    // One row more than the page: if it comes back, there is a next page.
    List<Order> rows = after == null
            ? orders.feedFirstPage(size + 1)
            : orders.feedAfter(after.createdAt(), after.id(), size + 1);
    boolean hasNext = rows.size() > size;
    List<Order> items = hasNext ? rows.subList(0, size) : rows;
    String nextCursor = hasNext ? Cursor.after(items.getLast()).encode() : null;
    return new FeedPage(responses(items), nextCursor, hasNext);
}
```

Ask for 21, return 20. If the 21st row exists, there is a next page. The old code used "the page is
full" instead, which is wrong when the last page happens to be exactly full: it promises one more
page, and that page is empty.

The cursor is built from the last row the client **received** (row 20), not from the extra row.

### Look before you go on

`./demo --mode keyset`:

```
RESULT: NO MATCHING INDEX. The pages are right, but page 5,000 read 81 rows and sorted them to return 20.
        The feed is sorted by (created_at, id). No index on orders has exactly those columns.
```

The rows are right now, and `./check 5` is at 3 of 6: it reports
`plan has a Sort step: [Limit, Incremental Sort, Index Scan Backward using idx_orders_created_at on orders]`. But the only index is on `created_at`, so Postgres can use it for the first
half of the comparison only: it reads whole batches of equal `created_at`, filters them by id and
sorts them.

### Step 3: the index

In `src/main/resources/indexes.sql`, replace the `idx_orders_created_at` line:

```sql
CREATE INDEX idx_orders_created_at_id ON orders (created_at, id);
```

The index now has the same columns, in the same order, as the `ORDER BY`. Replace, do not add: the
new index can do everything the old one did, and every extra index slows down writes
([`../03-indexes`](../03-indexes/), Task 10).

### After

```bash
./demo --mode keyset
```

```
indexes.sql changed: rebuilding 2 indexes...
Indexes ready in 0.5 s.

--- Page 5,000 ---
A client gets here by following nextCursor 4,999 times. To save you the wait, the
demo looked up the last order of page 4,999 in the database and built the cursor from it.
GET /api/orders/feed?size=20&after=2025-09-17T00%3A00%3A00Z%7C900021
    200 OK, 20 orders, 6 ms
    SQL statements sent: 1
      1. select * from orders where (created_at, id) < (?, ?) order by created_at desc, id desc limit ?     -- ? = '2025-09-17T00:00:00Z', 900021, 21
         Index Scan Backward using idx_orders_created_at_id on orders
         rows read: 21 | rows returned: 21 | pages read: 5

                              page 1        page 5,000
Rows returned to the client   20            20
Rows read                     21            21
Rows read and thrown away     1             1
Pages read (8 kB each)        4             5
Time in the database          0.06 ms       0.07 ms   (differs on every run)

RESULT: OK. Page 5,000 read 21 rows to return 20. Page 1 read 21.
```

21 rows on page 1, 21 rows on page 5,000. `./check 5` prints 6 of 6.

---

## Task 6: Fix the Cursor

Look at the URL in the output above: `after=2025-09-17T00%3A00%3A00Z%7C900021`. The cursor is a
readable timestamp and an id. Two problems.

**Clients will build it.** Once a client knows the format it will construct cursors itself, and from
then on you cannot change the sort key or the format without breaking that client.

**Rubbish becomes a 500.** `./check 6`:

```
  [FAIL] HTTP: a malformed cursor returns 400 INVALID_CURSOR, not a 500
           Request processing failed: java.time.format.DateTimeParseException: Text 'not-a-cursor' could not be parsed at index 0
```

A 500 means "the server has a bug". A bad cursor is the client's mistake: a 400.

### The change

In `Cursor`, replace `encode` and `decode`:

```java
public String encode() {
    String text = createdAt + "|" + id;
    return Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes(StandardCharsets.UTF_8));
}

public static Cursor decode(String encoded) {
    try {
        String text = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
        int separator = text.indexOf('|');
        if (separator < 0) {
            throw new InvalidCursorException();
        }
        Instant createdAt = Instant.parse(text.substring(0, separator));
        long id = Long.parseLong(text.substring(separator + 1));
        return new Cursor(createdAt, id);
    } catch (IllegalArgumentException | DateTimeException e) {
        throw new InvalidCursorException();
    }
}
```

Imports: `java.nio.charset.StandardCharsets`, `java.time.DateTimeException`, `java.util.Base64`.

- `getUrlEncoder()` uses `-` and `_` instead of `+` and `/`, so the cursor can go in a URL as it is.
- Four things can be wrong with the input: not Base64 (`IllegalArgumentException`), no separator,
  not a timestamp (`DateTimeParseException`, a `DateTimeException`), not a number
  (`NumberFormatException`, an `IllegalArgumentException`). All four end as
  `InvalidCursorException`, which `ApiExceptionHandler` already maps to 400 `INVALID_CURSOR`.
- The error message does not repeat what the client sent. Nothing here renders HTML, but echoing
  input back is a habit to avoid.

### Opaque is not secret

Base64 is an encoding. Anyone can decode `MjAyNS0wOS0xN1QwMDowMDowMFp8OTAwMDIx` back to
`2025-09-17T00:00:00Z|900021`. That is fine: it holds nothing the client did not already receive.
"Opaque" is a contract, not a lock: *this string is ours, send it back unchanged*. Never put
anything in a cursor that the client may not see, and validate it like any other input. If a client
must not be able to invent a position, sign the cursor.

`./check 6` prints 5 of 5. Run `./check 5` too: it must still be green.

---

## Task 7: Inspect Keyset Pagination

### Step 1 and 2: why depth does not matter

```bash
./inspect --mode keyset
```

```
  EXPLAIN (ANALYZE, BUFFERS)
    Limit  (cost=0.42..1.55 rows=21 width=37) (actual time=0.018..0.021 rows=21 loops=1)
      Buffers: shared hit=5
      ->  Index Scan Backward using idx_orders_created_at_id on orders  (cost=0.42..48455.38 rows=900690 width=37) (actual time=0.017..0.019 rows=21 loops=1)
            Index Cond: (ROW(created_at, id) < ROW('2025-09-17 01:00:00+01'::timestamp with time zone, '900021'::bigint))
            Buffers: shared hit=5
```

Compare with Task 1. There the index scan had no condition, started at the newest order and
produced 100,000 rows. Here it has an `Index Cond`: Postgres walks down the B-tree to
`(2025-09-17, 900021)` and starts reading **there**. `rows=21`, 5 pages. The offset plan started at
the top and counted. This one starts at the value.

(The timestamp shows in your machine's time zone. `rows=900690` in the first bracket is the
planner's estimate of how many rows match the condition; the `Limit` stops it after 21.)

`./demo --mode keyset --depth 50000` reads 20 rows for the very last page. Offset read 1,000,000.

`limit 21`, 20 returned (step 3): the extra row is only looked at to set `hasNext`, then dropped.

### Step 4: an order arrives between two requests

```bash
./demo --mode keyset --insert
```

```
GET /api/orders/feed?size=20
    200 OK, 20 orders, 7 ms
    last 3 orders:   #999,983  #999,982  #999,981

A customer places order #1,000,001. It is now the newest order: first in the list.

GET /api/orders/feed?size=20&after=MjAyNS0xMS0yNVQxMDowMDowMFp8OTk5OTgx
    200 OK, 20 orders, 12 ms
    first 3 orders:  #999,980  #999,979  #999,978

RESULT: OK. Page 2 continues exactly where page 1 ended. No order was returned twice or skipped.
```

Page 2 is "everything after #999,981". A new order at the top is not after #999,981, so it changes
nothing. The client does not see the new order in this pass: to see it, it starts again from the
top.

### Step 5: what if the cursor's order is deleted?

Nothing. The cursor holds **values**, `(created_at, id)`, not a pointer to a row. "Everything that
sorts after these values" is a valid question whether or not a row with those values still exists.

### Step 6 and 7: what you gave up

- **No jumping.** The demo had to look up the last order of page 4,999 to build a cursor for page
  5,000. A client cannot. There is no "page 400", only "next".
- **One sort order per endpoint.** Sorting by `total_cents` needs a new query
  (`(total_cents, id) < (?, ?)`), a new cursor (it must carry `total_cents`) and a new index on
  `(total_cents, id)`. With offset, changing the sort is one parameter.
- **The key must be unique and stable.** Unique: that is what the `id` is for. Stable: if you sort
  by `updated_at` and a row is updated while a client is paging, it moves, and the client can see it
  twice or not at all.

### A note on Spring Data's own keyset API

Spring Data can do keyset pagination for you: a repository method that returns `Window<Order>` and
takes a `ScrollPosition`, started with `ScrollPosition.keyset()`. We ran it on Spring Boot 4 against
this table. It works, and it returns the right rows. But look at the SQL it sends:

```
where o1_0.created_at<? or o1_0.created_at=? and o1_0.id<? order by o1_0.created_at desc,o1_0.id desc fetch first ? rows only
```

The same meaning, spelled with `OR` instead of a row comparison. Postgres 17 did not turn that into
an `Index Cond`: 10,000 orders deep in the test database it read **10,021 rows**, where the row
comparison read 21. Whatever writes your keyset query, look at the plan.

---

## Task 8: Fix the Hidden COUNT(*)

The two offset endpoints still send two statements per request. `./check 8`:

```
  [FAIL] Count: one page of GET /api/orders is exactly one SQL statement
           [SQL statements sent for GET /api/orders?page=3&size=20]
           Expected size: 1 but was: 2 in:
           [select ... from orders o1_0 order by o1_0.created_at desc,o1_0.id desc offset ? rows fetch first ? rows only [60, 20],
           select count(o1_0.id) from orders o1_0]
```

### Where it comes from

`findAll(Pageable)` returns a `Page<Order>`. A `Page` knows `getTotalElements()` and
`getTotalPages()`, and the only way to know them is to count. So Spring Data runs the count for
you, on every request. (It skips it only when the page comes back shorter than the requested size:
then the total is already known.)

In Postgres a count is not a number stored somewhere. It reads every matching row:

```
    Aggregate  (cost=20834.00..20834.01 rows=1 width=8) (actual time=108.002..108.003 rows=1 loops=1)
      ->  Seq Scan on orders o1_0  (cost=0.00..18334.00 rows=1000000 width=8) (actual time=0.011..62.942 rows=1000000 loops=1)
```

One million rows and 8,334 pages, to fill a field that `OffsetPage` does not even have. The screen
has a "Next" button. It needs `hasNext`, nothing more.

### The change

A `Slice` is a `Page` without the total. In `OrderRepository`, replace the comment about `findAll`
and the `findByCustomerId` method:

```java
Slice<Order> findAllBy(Pageable pageable);

Slice<Order> findByCustomerId(long customerId, Pageable pageable);
```

Import `org.springframework.data.domain.Slice` and remove the `Page` import. `findAll(Pageable)` is
inherited and always returns a `Page`, so you declare `findAllBy`: the same query, and the return
type tells Spring Data not to count.

In `OrderService`, in `page` and in `history`:

```java
Slice<Order> result = orders.findAllBy(PageRequest.of(page, size, NEWEST_FIRST));
```

```java
Slice<Order> result = orders.findByCustomerId(customerId, PageRequest.of(page, size, NEWEST_FIRST));
```

Same import change. `result.getContent()` and `result.hasNext()` work as before.

### After

```bash
./demo
```

```
GET /api/orders?page=0&size=20
    200 OK, 20 orders, 6 ms
    SQL statements sent: 1
      1. select ... from orders o1_0 order by o1_0.created_at desc,o1_0.id desc fetch first ? rows only     -- ? = 21
...
RESULT: DEEP OFFSET. Page 5,000 read 100,001 rows to return 20. Page 1 read 21.
```

One statement. `? = 21`: a `Slice` uses the same trick you wrote by hand in Task 5. It asks for
`size + 1` rows and uses the extra one for `hasNext`. And the request went from about 32 ms to
about 6 ms, on page 1, for every user.

`RESULT: DEEP OFFSET` is still there, and it stays: this endpoint uses `OFFSET`.

`./check` now prints 35 passes and no failures.

### If the screen really needs a total

Ask how exact it has to be. `./inspect` shows `rows 1,000,000` for the table: that is the planner's
estimate (`pg_class.reltuples`), free and good enough for "about a million orders". Other options:
count up to a limit and show "10,000+", cache the count for a minute, or count only on the first
page. An exact count on every request is the most expensive choice, and with `Page` it is the
default.

---

## Task 9: Compare Offset and Keyset

### Step 1: the numbers

Run `./demo` and `./demo --mode keyset`, each with `--depth 5000` and `--depth 50000`. Rows read,
from runs of the finished project. They are the same on every machine.

| | Page 1 | Page 5,000 | Page 50,000 | Statements per request |
|---|---|---|---|---|
| Offset, as it started | 20 | 100,000 | 1,000,000 | 2 (plus 1,000,000 rows for the count) |
| Offset, after Task 8 | 21 | 100,001 | 1,000,000 | 1 |
| Keyset | 21 | 21 | 20 | 1 |

### Step 2 and 3

`./demo --insert` prints `RESULT: SHIFTED`. `./demo --mode keyset --insert` prints `RESULT: OK`.

```bash
./demo --depth 3
```

```
RESULT: OK. Page 3 read 61 rows to return 20. Page 1 read 21.
```

Page 3 costs 61 rows. That is nothing. Offset is not slow. **Deep** offset is slow.

### Step 4, 5 and 6: the honest answer

- Jump to page 400: only offset can. Keyset has no page numbers.
- 300 products, sortable by any column: use offset. The deepest page reads 300 rows. Keyset would
  need a cursor format and an index per sort column, for no gain.
- A total without counting: the estimate in `pg_class.reltuples`.

### When to use which

| | Offset | Keyset |
|---|---|---|
| Cost of page N | Reads N x size rows | Reads size + 1 rows, at any depth |
| Rows inserted or deleted while paging | A row repeats or is skipped | Nothing shifts |
| Jump to page N, "page 37 of 500" | Yes | No: next only (previous with a second query) |
| Sort order | Any, chosen per request | One per endpoint: unique, stable, indexed |
| What the client keeps | A page number | An opaque cursor |
| Use it when | Small tables, admin screens, shallow paging, page numbers required | Feeds, infinite scroll, exports, sync jobs, anything deep or busy |

Whichever you use: a default and a maximum page size, an `ORDER BY` that ends in a unique column,
and no `COUNT` unless somebody reads the total.

### How to say it in an interview

> "With OFFSET the database has to produce all the rows before the page and throw them away, so page
> 5,000 of 20 reads 100,000 rows. It is also a position in a list that changes: if a row is inserted
> between two requests, the user sees a row twice. For a large or busy table I would use keyset
> pagination instead: order by a unique key, for example created_at and then id, and ask for the
> rows after the last one I returned, with a row comparison, `WHERE (created_at, id) < (?, ?)`, and
> an index on the same columns. Every page then reads page size plus one rows, and I use that extra
> row to know if there is a next page, so I do not need a COUNT. The client gets an opaque cursor.
> The trade-off is that you cannot jump to page N and you need a fixed sort key, so for a small admin
> table with sortable columns I would keep offset, with a maximum page size, and a Slice instead of
> a Page so Spring Data does not count on every request."

Then be ready for the follow-ups in "Questions to Ask Afterwards" at the end of
[`INTERVIEW_TASKS.md`](INTERVIEW_TASKS.md).

---

## Clean up

```bash
docker compose down
```
