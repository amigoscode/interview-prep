# Interview Tasks — Pagination

> **In `start/`, `./check` FAILS on purpose: 15 of 35 checks pass.** Tasks 2, 3, 5, 6 and 8 turn the
> other 20 green. Tasks 1, 4, 7 and 9 are inspection tasks: you run things, look, and answer
> questions.

Do the tasks in order. Every fix is followed by an inspection: a green test tells you the fix works,
the inspection tells you **why**, and the why is what an interviewer asks for.

Stuck, or want the explanation? The same task number in [`TASKS_GUIDED.md`](TASKS_GUIDED.md) walks
you through it with the expected output.

Commands are for macOS and Linux. On Windows type `.\demo`, `.\inspect` and `.\check`.

**Rules:** work in `start/`. Never edit, delete or disable a test. Indexes go in
`src/main/resources/indexes.sql` and nowhere else. Keep `GET /api/orders?page=&size=` an OFFSET
endpoint: it stays slow on deep pages for ever, so you can always compare against it.

---

## Task 1: See It Break (15 min)

**Story:** *The back office has an "All orders" screen. It worked fine for two years. Now there are
one million orders, the screen is slow, the nightly export that walks every page takes hours, and
last week the service ran out of memory. Before you change anything, find out what one request
really does.*

**Steps:**
1. `docker compose up -d`, then `./demo`. It asks for page 1 and page 5,000. How many rows did
   Postgres read for each? How many came back?
2. In the same output, count the SQL statements per request. You asked for 20 orders. What is the
   second statement for, and how many rows did it read?
3. `./inspect`. Find `OFFSET` in the SQL, and the `rows=` of the `Index Scan` line in the plan. What
   did Postgres do with the rows it read and did not return?
4. `./demo --depth 50000`. That is the last page. How many rows now?
5. `./demo --size 100000`. What did the server do? What would `?size=1000000` do? Open `Paging.java`:
   what does a request with no `size` at all get?
6. Open `OrderService.page`. That is all the code there is. List everything you have seen go wrong
   with this one line.

**Acceptance Criteria** (`./check 1`, already passing: these tests document the problem)
- [ ] You can say what `OFFSET n` makes the database do
- [ ] You can explain why page 1 is fast and page 5,000 is not, although both return 20 rows
- [ ] You can say where the `SELECT COUNT` comes from
- [ ] `DeepOffsetIT.theFirstPageIsCheap` and `DeepOffsetIT.aDeepPageReadsEveryRowBeforeIt` pass

**Hints:**
- Compare "Rows read" with "Rows returned to the client"
- Time is printed, but do not trust it: it changes on every run. Rows and pages do not

---

## Task 2: Fix the Unbounded Page Size (10 min)

**Story:** *The client decides how many rows one request loads, and that is how the service ran out
of memory. Give the API a default page size and a maximum, in `Paging`.*

**Acceptance Criteria** (`./check 2`)
- [ ] A request without `size` gets 20 orders, and without `page` the first page
      — `PageSizeTest.noSizeMeansTwenty`
- [ ] `size=100` is the largest size that is accepted — `PageSizeTest.oneHundredIsAllowed`
      (already passing: keep it that way)
- [ ] `size=101` and `size=1000000` return 400 `INVALID_PAGE_REQUEST`, and nothing is loaded
      — `PageSizeTest.tooLargeIsRejected`
- [ ] `size=0` and `size=-5` return 400 — `PageSizeTest.zeroAndNegativeAreRejected`
- [ ] `page=-1` returns 400 — `PageSizeTest.negativePageIsRejected`
- [ ] The feed and a customer's history follow the same rules
      — `PageSizeTest.theOtherEndpointsFollowTheSameRules`
- [ ] `./demo --size 100000` prints `RESULT: OK`

**Hints:**
- `InvalidPageRequestException` already exists, and `ApiExceptionHandler` already turns it into a
  400 with the code `INVALID_PAGE_REQUEST`
- All three endpoints call `Paging.size` and `Paging.page`. You do not touch the controller
- Reject or clamp? The tests want a 400. Be ready to argue for it, and for the other choice

---

## Task 3: Fix the Unstable Order (15 min)

**Story:** *A business customer complains that their order history shows some orders twice and that
others are missing. Support checked: nothing was added or deleted while they were scrolling. Find
out why, and fix the sort order in `OrderService`.*

**Acceptance Criteria** (`./check 3`)
- [ ] The SQL sorts by `created_at` and then by `id` — `StableOrderIT.theSqlHasATieBreaker`
- [ ] Orders with the same `created_at` come back with the highest id first, however they are stored
      — `StableOrderIT.tiesComeBackInOneFixedOrder`
- [ ] Reading a customer's 120 orders page by page returns each order exactly once
      — `StableOrderIT.walkingThePagesReturnsEveryOrderOnce`
- [ ] `./demo --mode history` prints `RESULT: OK`

**Hints:**
- Run `./demo --mode history` first and read the example it prints. What do the repeated orders
  have in common?
- What does `ORDER BY created_at` promise about two rows with the same `created_at`?
- One constant. Both offset endpoints use it

---

## Task 4: Inspect What OFFSET Cannot Fix (15 min)

**Story:** *The order is stable now. Two complaints are still open: the screen is slow on deep pages,
and during the day, when orders keep coming in, people still see an order twice. Show why your last
fix could not help with either.*

**Steps:**
1. `./demo --mode history` and `./inspect --mode history`. Find the `Sort Key` in the plan. What
   changed compared with before Task 3?
2. `./demo --insert`. One order is placed between the request for page 1 and the request for page 2.
   Which order comes back twice? Why that one?
3. Now the other direction, on paper: page 1 is read, then an order on page 1 is cancelled and
   deleted, then page 2 is read. Which order does the client never see?
   (`OffsetShiftIT.aDeleteSkipsARow` is this scenario.)
4. `./demo`. Page 5,000 is still slow. Would an index help? There already is one: which index does
   the plan use, and for what?
5. The nightly export reads every page from first to last. With 50,000 pages, roughly how many rows
   does Postgres read in total? (Page n reads n x 20 rows.)

**Acceptance Criteria** (`./check 4`, already passing: these tests document the problem)
- [ ] You can explain why an insert at the top of the list repeats a row, and a delete skips one
- [ ] You can explain why this happens with a perfectly stable `ORDER BY`
- [ ] You can explain why no index makes `OFFSET 99980` cheap
- [ ] `OffsetShiftIT.anInsertRepeatsARow` and `OffsetShiftIT.aDeleteSkipsARow` pass

---

## Task 5: Fix It With Keyset Pagination (30 min)

**Story:** *Stop counting rows. A page should start where the last one ended: "the 20 orders that
come after this one". Somebody started `GET /api/orders/feed` that way and left. Finish it:
`OrderRepository`, `OrderService.feed` and `indexes.sql`.*

**Acceptance Criteria** (`./check 5`)
- [ ] `indexes.sql` creates an index on `orders (created_at, id)`
      — `KeysetIT.theIndexMatchesTheSortOrder`
- [ ] The feed query finds its starting point in that index with a row comparison, and sorts nothing
      — `KeysetIT.theQueryIsARowComparisonOnTheIndex`
- [ ] A page 10,000 orders deep reads 21 rows, exactly as many as the first page
      — `KeysetIT.aDeepPageCostsTheSameAsTheFirst`
- [ ] Following `nextCursor` from the first page to the last returns all 20,000 orders exactly once,
      newest first — `KeysetIT.followingTheCursorReturnsEveryOrderOnce`
- [ ] `hasNext` is true while orders remain; the last page has `hasNext = false` and no
      `nextCursor`, even when it is exactly full — `KeysetIT.theLastPageKnowsItIsTheLast`
- [ ] An order placed between two requests changes nothing
      — `KeysetIT.anInsertDoesNotShiftThePages`
- [ ] `./demo --mode keyset` prints `RESULT: OK`

**Hints:**
- Run `./demo --mode keyset` first. Which orders does page 2 start with, and which did it jump over?
  Look at the `WHERE` clause and remember Task 3
- The position of a row in the list is two values, not one. `Cursor` already carries both
- Postgres can compare two values at once: `(a, b) < (?, ?)`. Write the query as native SQL
- How do you know there is a next page without counting? Ask for one row more than you return
- After the query is right, `./check 5` still complains about a Sort. The index has to have the
  same columns as the `ORDER BY`. Replace the old index, do not add a second one

---

## Task 6: Fix the Cursor (15 min)

**Story:** *The cursor travels to the client and back as `2025-11-25T10:00:00Z|999981`. Clients have
started building it themselves, and when someone sends rubbish the API answers 500. Make the cursor
opaque, and make a cursor the API cannot read a 400. Fix `Cursor`.*

**Acceptance Criteria** (`./check 6`)
- [ ] `decode(encode(cursor))` gives the same cursor back — `CursorTest.roundTrip` (already passing:
      keep it that way)
- [ ] The encoded form is URL-safe Base64, with no timestamp or id readable in it
      — `CursorTest.theEncodedFormIsOpaque`
- [ ] Text that is not a cursor is an `InvalidCursorException`, never another exception
      — `CursorTest.garbageIsRejected`
- [ ] Over HTTP a malformed cursor returns 400 `INVALID_CURSOR`, not a 500
      — `CursorTest.aMalformedCursorIsABadRequest`
- [ ] A cursor the API handed out is accepted — `CursorTest.aValidCursorReachesTheService` (already
      passing)
- [ ] Task 5 is still green (`./check 5`)

**Hints:**
- `java.util.Base64.getUrlEncoder().withoutPadding()` and `getUrlDecoder()`
- `InvalidCursorException` already exists and is already mapped to a 400
- Which exceptions can decoding throw? Bad Base64, a bad timestamp, a bad number, a missing
  separator. All four must end as the same exception
- Is the cursor a secret now?

---

## Task 7: Inspect Keyset Pagination (15 min)

**Story:** *The feed passes its tests. Now prove you know why it is fast, and what you gave up.*

**Steps:**
1. `./demo --mode keyset`. Compare "Rows read" for page 1 and page 5,000. Then
   `./demo --mode keyset --depth 50000`.
2. `./inspect --mode keyset`. Find the `Index Cond` line. Compare it with the plan of
   `./inspect` (offset): where did Postgres start reading the index in each case?
3. The request returns 20 orders and the SQL says `limit 21`. What happens to the 21st row?
4. `./demo --mode keyset --insert`. Compare with Task 4 step 2. Why does the new order not shift
   anything? Will the client ever see the new order?
5. Copy a `nextCursor` from the demo output and decode it (any Base64 tool). What is in it? What
   happens if the order it points to is deleted before the client asks for the next page?
6. The demo "looked up the last order of page 4,999 in the database" to get to page 5,000. Why did
   it have to? What can a real client not do with this endpoint?
7. The product owner wants to sort the feed by `total_cents`. What would have to change: in the
   query, the cursor and the index?

**Acceptance Criteria** (no tests: you answer)
- [ ] You can explain in one sentence why a deep keyset page costs the same as the first
- [ ] You can explain why `(created_at, id) < (?, ?)` is not the same as
      `created_at < ? AND id < ?`
- [ ] You can explain how `hasNext` works without a count
- [ ] You can name two things keyset pagination cannot do

---

## Task 8: Fix the Hidden COUNT(*) (15 min)

**Story:** *Every request to the two offset endpoints still runs two SQL statements. The second one
counts every matching row, to fill a `totalElements` that no screen shows: the UI only has a "Next"
button. Remove it, in `OrderRepository` and `OrderService`.*

**Acceptance Criteria** (`./check 8`)
- [ ] One page of `GET /api/orders` is exactly one SQL statement — `CountQueryIT.onePageIsOneStatement`
- [ ] No endpoint sends `SELECT COUNT` — `CountQueryIT.nothingCountsTheTable`
- [ ] The page query asks for one row more than the page size
      — `CountQueryIT.thePageQueryFetchesOneRowMore`
- [ ] `hasNext` is still right: true in the middle, false on the last page
      — `CountQueryIT.hasNextIsStillRight` (already passing: keep it that way)
- [ ] `./demo` no longer prints `RESULT: HIDDEN COUNT`
- [ ] `./check` prints 35 passes and no failures

**Hints:**
- The return type of a Spring Data repository method decides what it does. What else can a method
  that takes a `Pageable` return?
- `findAll(Pageable)` is inherited and always returns a `Page`. Declare your own method
- You already did the same trick by hand in Task 5

---

## Task 9: Compare Offset and Keyset (15 min)

**Story:** *"So keyset is always better?" is the interview question. Collect the numbers, then give
the honest answer.*

**Steps:**
1. Run these and note statements per request, rows read for page 1 and rows read for the deep page:
   - `./demo --depth 5000`
   - `./demo --mode keyset --depth 5000`
   - `./demo --depth 50000`
   - `./demo --mode keyset --depth 50000`
2. `./demo --insert` and `./demo --mode keyset --insert`. Which one repeats a row?
3. `./demo --depth 3`. How much does page 3 cost with OFFSET? Most users never go past it.
4. A support agent wants to jump straight to page 400 of 2,500. Which endpoint can do that?
5. An admin screen lists 300 products, sortable by any column. Would you build keyset pagination
   for it? What would it cost you?
6. `./inspect`: look at the `rows` column of the table. Where could a screen get "about 1,000,000
   orders" from without counting them?

**Acceptance Criteria** (no tests: you answer)
- [ ] You can name three things that go wrong with offset pagination on a big table
- [ ] You can name two things keyset pagination cannot do, and one thing it needs
- [ ] You can give one situation where offset is the right choice and one where it is not
- [ ] You can say what to do when the screen really needs a total

---

## Questions to Ask Afterwards

Answer out loud, in full sentences, before reading the answer in brackets.

- How do you paginate a large table? (For anything a user or a job reads far into, keyset
  pagination: order by a unique key, remember the key of the last row of a page, and ask for the
  rows after it with `WHERE (created_at, id) < (?, ?) ORDER BY created_at DESC, id DESC LIMIT ?`,
  backed by an index on the same columns. Every page then costs the same. Offset pagination is fine
  for small tables and shallow pages. Either way the API has a default and a maximum page size.)
- What is wrong with `LIMIT` / `OFFSET`? (Three things. `OFFSET n` does not skip rows for free: the
  database produces the first n rows and throws them away, so page 5,000 reads 100,000 rows to
  return 20. It counts positions from the top at the moment of each request, so an insert or delete
  between two requests shifts every row: one is shown twice or one is never shown. And it is usually
  paired with a `COUNT(*)` for the total, which reads every matching row on every request.)
- Users see the same row on two pages. Why? (Two different causes. The `ORDER BY` is not unique:
  rows with the same `created_at` have no defined order, so the database may arrange them
  differently for page 1 and for page 2. Fix: add a unique tie-breaker, usually the primary key.
  Or rows were inserted or deleted between the two requests and the offsets shifted. Fix: keyset
  pagination. A tie-breaker does not fix the second cause.)
- Why did the same query return ties in a different order for different pages? (Without an index
  that gives the order, Postgres sorts. With a `LIMIT` it uses a top-N heapsort that only keeps the
  first `offset + limit` rows, and a heapsort does not keep equal rows in their original order. The
  rows it keeps among equals depend on how many it keeps, so page 1 and page 2 disagree. Nothing
  random is involved: the SQL simply never said which of two equal rows comes first.)
- What is a row-value comparison? (`(a, b) < (x, y)` compares like a dictionary: `a < x`, or
  `a = x AND b < y`. It is exactly the order `ORDER BY a, b` produces, so "the rows after this one"
  is one condition. Postgres turns it into a single index condition on an index on `(a, b)`. It is
  not the same as `a < x AND b < y`, which would lose every row with a smaller `a` and a larger
  `b`.)
- Why does the keyset query need a matching index? (Without it the query is still correct, but the
  database has to find and sort the rows. With an index on exactly the `ORDER BY` columns it walks
  down the tree to the cursor position, 3 or 4 pages, and reads the next 21 entries in order. That
  is why depth does not matter.)
- How do you know there is a next page without `COUNT(*)`? (Fetch `size + 1` rows. If the extra row
  comes back there is a next page: return `size` rows and drop the extra one. "The page was full"
  is not enough: when the last page is exactly full it would promise a page that is empty.)
- What is the difference between `Page` and `Slice` in Spring Data? (Both hold one page of rows. A
  `Page` also knows the total number of rows and pages, and to know that it runs a second query,
  `SELECT COUNT`, on every request. Spring Data only skips the count when the page came back
  shorter than requested, because then the total is already known. A `Slice` knows only whether
  there is a next page, which it finds out by fetching `size + 1` rows. No count.)
- The screen really needs a total. What then? (Ask how exact it must be. "About 1,000,000" can come
  from the planner's estimate, `pg_class.reltuples`, for free. Or count up to a limit and show
  "10,000+". Or cache the count for a minute. Or count once on the first page and not on every
  page. An exact count of a large filtered result on every request is the expensive option, and it
  is the default.)
- Why an opaque cursor? (The cursor is the server's private bookmark. If clients can read it they
  start to build it, and then you can never change the sort key or the format. Base64 makes it
  clearly "not yours to parse". It is not security: anyone can decode it, so it must not hold
  anything secret, and it must be validated like any other input. If a client must not be able to
  forge a position, sign it.)
- What should happen when a client sends a cursor you cannot read? (A 400 with a clear code. It is
  the client's mistake, so it must not become a 500, and it must not be silently treated as "no
  cursor", which would restart the client at page 1 and loop for ever.)
- Reject a page size that is too large, or clamp it? (Both are used. Clamping is forgiving, and it
  is what Spring Data's `Pageable` resolver does by default, at 2,000. Rejecting with a 400 is
  louder: a client that asks for 500 and silently gets 100 may conclude "fewer than I asked for, so
  that was the last page" and stop early. Whichever you choose, document it and return the size
  that was used.)
- What can keyset pagination not do? (Jump to an arbitrary page or show "page 37 of 500": there is
  no page number, only "after this row". Sort by anything the user clicks: each sort order needs
  its own cursor and its own index, and the sort key must be unique, or made unique with a
  tie-breaker, and not null. And if a row's sort key changes while a client is paging, that row can
  be seen twice or missed.)
- When is offset pagination perfectly fine? (Small tables. Admin screens with a few hundred rows
  and any-column sorting. Search results where nobody goes past page 3. A UI that needs page
  numbers. Deep pages and constant inserts are what make it fail. Without those, it is the simpler
  tool.)
- A new order arrives while a client is paging with a cursor. Does the client see it? (Not in this
  pass: it sorts before the client's position. Nothing is repeated and nothing is skipped. To see
  new rows the client starts again from the top, or the API offers a second cursor that goes in the
  other direction.)
- How would you export all one million orders? (Not with page numbers. Follow the cursor in a loop
  with the largest allowed page size, or stream the result of one query, in key order. Each step
  costs the same, and the export does not shift when orders arrive.)

## Tips for Interviewers

- The most common weak answer is "I would use `Pageable`". Ask what SQL that produces, and how many
  statements. Many candidates have never seen the `COUNT`.
- Ask the candidate to predict "rows read" for page 5,000 before running `./demo`. A strong
  candidate says 100,000 without running anything, and adds that the index does not help.
- In Task 3, see whether they separate the two causes of duplicates. A candidate who says "add the
  id to the ORDER BY" and believes the insert problem is solved too has learned a recipe. Task 4
  step 2 shows it.
- In Task 5, watch the `WHERE` clause. `created_at < ? AND id < ?` is the classic wrong answer, and
  `created_at <= ?` returns rows twice. Ask them to walk through three orders with the same
  `created_at`.
- A candidate who uses "the page was full" for `hasNext` should be asked about a table with exactly
  40 rows and a page size of 20.
- In Task 6, ask whether Base64 makes the cursor secure. The right answer is no, and that it does
  not need to be.
- For senior candidates, go past the tasks: a previous-page cursor, a sort key that is nullable or
  that changes (`updated_at`), mixed sort directions (`ORDER BY a ASC, b DESC` cannot be one row
  comparison), keyset over a filtered list and the index that needs, and what Spring Data's
  `Window` / `ScrollPosition.keyset()` generates (see the guided file, Task 7).
