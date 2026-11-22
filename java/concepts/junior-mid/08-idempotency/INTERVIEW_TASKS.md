# Interview Tasks — Idempotency

> **In `start/`, `./check` FAILS on purpose: 15 of 32 checks pass.** Tasks 2, 4, 6 and 7 turn the
> other 17 green. Tasks 1, 3, 5, 8 and 9 are inspection tasks: you run things, look, and answer
> questions.

Do the tasks in order. A green test tells you the fix works. The inspection tasks tell you **why**,
and the why is what an interviewer asks for.

Stuck, or want the explanation? The same task number in [`TASKS_GUIDED.md`](TASKS_GUIDED.md) walks
you through it with the expected output.

Commands are for macOS and Linux. On Windows type `.\demo`, `.\inspect` and `.\check`.

**Rules:** work in `start/`. Never edit, delete or disable a test. A request without an
`Idempotency-Key` header stays unprotected on purpose (`./demo --no-key`), so you can always compare
against it.

---

## Task 1: See the Double Charge (10 min)

**Story:** *Support forwards a complaint: "I paid 25.00 once and you took 50.00." The logs show two
successful payments, one second apart, and no errors. Before you change anything, reproduce it and
find out what the client and the server each knew.*

**Steps:**
1. `docker compose up -d`, then `./demo`. How many requests did the client send? How many payments
   did Maria want to make? How many rows are in `payment`?
2. Read the timeline. At +1000 ms the client gives up. What does it know at that moment about the
   first request? What had the server done by then, and what did it do afterwards?
3. `./demo --sql`. Is there any statement that could tell the second request from a brand new
   payment?
4. The client did send an `Idempotency-Key` header. Open `PaymentController` and `PaymentService`.
   Where does the key go?
5. `./inspect`. What does the server remember about the requests it has seen?
6. Without running anything: the client retries a `GET /api/payments/1`, a `PUT` that sets a
   customer's email, a `DELETE /api/payments/1`. Which of those retries can do harm? Why is `POST`
   different?

**Acceptance Criteria** (`./check 1`, already passing: this test documents the bug)
- [ ] You can explain why a timeout means "unknown" and not "failed"
- [ ] You can say why the client is right to retry, and why the server is wrong to charge again
- [ ] You can list the HTTP methods that are idempotent by definition, and say why POST is not
- [ ] `NoKeyIT.samePaymentSentTwiceWithoutAKeyChargesTwice` passes

**Hints:**
- A request can fail in three places: on the way in, on the server, on the way back. The client sees
  the same thing in all three cases
- Idempotent means: doing it twice leaves the server in the same state as doing it once

---

## Task 2: Fix It With an Idempotency Key (20 min)

**Story:** *The client already makes up a UUID per payment and sends it as `Idempotency-Key` on
every attempt. Use it. If this customer has already used this key, do not charge again: answer with
the payment that exists. Otherwise charge and remember the key. Fix `PaymentService.pay`.*

**Acceptance Criteria** (`./check 2`)
- [ ] A retry with the same key charges the customer once, and the card once
      — `IdempotencyKeyIT.retryWithTheSameKeyChargesOnce`
- [ ] The retry is answered with the payment that already exists (same payment id)
      — `IdempotencyKeyIT.retryIsAnsweredWithTheSamePayment`
- [ ] The key is stored in `idempotency_key` with its payment id
      — `IdempotencyKeyIT.keyIsStoredWithItsPayment`
- [ ] Two different keys are two payments
      — `IdempotencyKeyIT.differentKeysAreDifferentPayments` (already passing: keep it that way)
- [ ] The same key from two different customers is two payments
      — `IdempotencyKeyIT.keysAreScopedPerCustomer` (already passing: keep it that way)
- [ ] `./demo` prints `RESULT: OK`
- [ ] `./demo --no-key` still prints `RESULT: DOUBLE CHARGE`, and the baseline tests still pass

**Hints:**
- The table, the entity (`IdempotencyRecord`) and a finder
  (`findByCustomerIdAndIdempotencyKey`) already exist
- `idempotencyKey` is `null` when the client sent no header. Leave that path as it is
- Save the key in the same transaction as the payment. What would happen if the payment committed
  and the key did not?
- For the answer to a retry, load the payment by the stored `payment_id`. Answer it with status 200
  for now. Task 6 comes back to this

---

## Task 3: Inspect the Double Click (10 min)

**Story:** *The retry is safe. Then a customer double-clicks the Pay button.*

**Steps:**
1. `./demo --scenario double-click`. Both requests carry the same key. How many times was Maria
   charged?
2. `./inspect`. Look at the `idempotency_key` table. What is wrong with it?
3. `./demo --scenario double-click --sql`. Write down the order of the statements across the two
   threads. Which statement did both threads run before either of them ran its `insert into
   idempotency_key`?
4. Open your `pay` method. Name the three steps. Between which two is the gap?
5. `./demo --scenario double-click --requests 10`. How many charges? Why did some requests get a
   402?
6. `./demo --scenario double-click --processor-ms 0`, a few times. Does a fast card processor make
   the problem go away?

**Acceptance Criteria** (no tests: you answer)
- [ ] You can name the pattern that makes the fix from Task 2 unsafe
- [ ] You can explain why the retry after a timeout was safe and the double click is not
- [ ] You can explain why a `SELECT` cannot protect you here, whatever you do in Java

---

## Task 4: Fix the Race With a UNIQUE Constraint (25 min)

**Story:** *Two requests cannot both be allowed to decide "this key is new". Let the database make
that decision: it is the only place both requests meet. Claim the key first, charge second.*

**Acceptance Criteria** (`./check 4`)
- [ ] The database refuses a second row for the same customer and key
      — `ConcurrentDuplicatesIT.databaseRefusesASecondRowForTheSameKey`
- [ ] Two requests with the same key at the same moment: one payment row, and the card processor is
      called once — `ConcurrentDuplicatesIT.doubleClickChargesOnce`
- [ ] The request that loses is answered with the winner's payment, not with an error
      — `ConcurrentDuplicatesIT.theLoserIsAnsweredWithTheWinnersPayment`
- [ ] 8 requests with the same key at the same moment: one payment, one charge, 8 answers
      — `ConcurrentDuplicatesIT.manyDuplicatesChargeOnce`
- [ ] A payment that fails leaves no key behind, so the same key can be tried again
      — `ConcurrentDuplicatesIT.failedPaymentLeavesNoKeyBehind` (already passing: keep it that way)
- [ ] `./demo --scenario double-click` prints `RESULT: OK`, and nobody gets a 500

**Hints:**
- Step 1 is in `schema.sql`. Add the constraint, change nothing else, and run
  `./demo --scenario double-click`. Look at what the losing request gets, and at "Card processor was
  called". The constraint alone is half a fix
- The key row has to be written **before** the charge, not after it
- Postgres: `insert ... on conflict (...) do nothing`. With `@Modifying` and
  `@Query(nativeQuery = true)` the repository method can return `int`: the number of rows inserted
- Keep the insert in the same transaction as the charge. What does the second request's insert do
  while the first transaction is still open?

---

## Task 5: Inspect the Insert-First Fix (15 min)

**Story:** *The tests are green. Now show what the second request does while the first one is still
talking to the card processor.*

**Steps:**
1. `./demo --scenario double-click --sql`. Which statement is now the first one both threads run?
   How many statements does the losing thread run in total?
2. In the same output, look at the timestamps. When does the losing thread run its second
   statement, compared with the winner's last statement?
3. Open a second terminal. In the first, run `./demo --scenario double-click --pause`. While it
   waits, run `./inspect` in the second. Which session is waiting, for which session, on which
   statement? What is in the `idempotency_key` table?
4. When the demo finishes, look at the two answers and their timestamps. What did the loser get, and
   when?
5. `./demo --scenario double-click --requests 30`. How many times was the card processor called? The
   connection pool holds 10 connections. What were the other 29 requests holding while they waited?
6. Without running anything: the winner's payment fails (insufficient funds) and its transaction
   rolls back. What happens to the request that was waiting?

**Acceptance Criteria** (no tests: you answer)
- [ ] You can quote the SQL that claims the key, and say what "0 rows inserted" means
- [ ] You can say what the loser was doing while the winner was charging, and what it answers
- [ ] You can explain why the key row and the payment must commit together
- [ ] You can name the other design (answer the loser at once with a 409) and when you would pick it

---

## Task 6: Replay the Same Response (20 min)

**Story:** *A mobile client retries a payment and gets `200` where the first attempt would have got
`201`, with a different `balanceAfterCents`. Its code treats that as a different outcome. A retry
must get the answer the original got: same status code, same body. Store the answer with the key and
send it again. Add a header so a replay can still be recognised.*

**Acceptance Criteria** (`./check 6`)
- [ ] The retry gets the same status code and the same body as the original, even after the balance
      has changed — `ReplayIT.retryGetsTheOriginalAnswer`
- [ ] The original is not marked as a replay, the retry is — `ReplayIT.onlyTheRetryIsMarkedAsAReplay`
- [ ] The status code and the body are stored with the key
      — `ReplayIT.answerIsStoredWithTheKey`
- [ ] A replayed answer carries the header `Idempotent-Replay: true`
      — `ReplayHeaderTest.replayCarriesTheHeader`
- [ ] The original answer does not carry it
      — `ReplayHeaderTest.originalDoesNotCarryTheHeader` (already passing: keep it that way)
- [ ] `./demo --scenario replay` prints `RESULT: OK`

**Hints:**
- Run `./demo --scenario replay` first and compare the first and the last answer
- The columns `response_status` and `response_body` already exist, and `IdempotencyRecord` has
  setters for them
- `PaymentResponse.toJson()` and `PaymentResponse.fromJson(...)` are given.
  `PaymentResult.replay(status, body)` is given
- The header is the controller's job: `result.replayed()`

---

## Task 7: Same Key, Different Body (20 min)

**Story:** *A client has a bug: it reuses one key for every payment of a session. The second
payment, for a different amount, gets the stored answer of the first one. The client believes 40.00
was paid. Nothing was. A key belongs to one request. If it comes back with a different body, refuse
it.*

**Acceptance Criteria** (`./check 7`)
- [ ] The same request always gives the same hash — `RequestHashTest.sameRequestSameHash`
- [ ] A different amount or customer gives a different hash
      — `RequestHashTest.differentRequestDifferentHash`
- [ ] The hash is a SHA-256 in hex, 64 characters — `RequestHashTest.hashIsSha256Hex`
- [ ] The hash is stored with the key — `DifferentBodyIT.requestHashIsStoredWithTheKey`
- [ ] The same key with a different amount is rejected with `IdempotencyKeyReusedException`
      (the API answers 422) — `DifferentBodyIT.sameKeyDifferentAmountIsRejected`
- [ ] The rejected request charges nothing — `DifferentBodyIT.rejectedRequestChargesNothing`
- [ ] `./demo --scenario different-body` prints `RESULT: OK`
- [ ] `./check` prints no `[FAIL]` at all

**Hints:**
- Run `./demo --scenario different-body` first. Read the RESULT line
- `RequestHash.of` is a stub. `MessageDigest.getInstance("SHA-256")` and `HexFormat.of().formatHex`
- Hash the fields with their names or a separator between them. Customer 1 paying 2500 and customer
  12 paying 500 must not look the same
- The `request_hash` column already exists. Write it when you claim the key, compare it when the
  claim fails
- `ApiExceptionHandler` already turns the exception into a 422

---

## Task 8: Inspect the Key Table: Scope and Expiry (10 min)

**Story:** *The key table is now part of your payment system. Look at what is in it, who a key
belongs to, and how long it lives.*

**Steps:**
1. `./demo`, then `./inspect`. Read the `idempotency_key` row column by column. Which task added
   which column's value?
2. The table stores `request_hash`, not the request. The request here is tiny. Why store a hash
   anyway?
3. Open `schema.sql`. The UNIQUE constraint is on two columns. What would go wrong if it were on
   `idempotency_key` alone? (`IdempotencyKeyIT.keysAreScopedPerCustomer` is the test for this.)
4. Look at `expires_in`. Open `IdempotencyCleanup`. What does it delete, and who calls it?
5. `./demo --scenario expired`. What happens to a retry that arrives after the key is gone? Is that
   a bug?
6. Without running anything: how long should keys live? What decides it?

**Acceptance Criteria** (`./check 8`, already passing: these tests document the behaviour)
- [ ] You can say what each column of `idempotency_key` is for
- [ ] You can explain why a key is scoped to a customer (or an API client), not global
- [ ] You can say what happens after expiry, and why keys cannot be kept for ever
- [ ] `ExpiryIT.cleanupDeletesOnlyExpiredKeys` and `ExpiryIT.afterExpiryTheSameKeyIsANewPayment` pass

---

## Task 9: Where Else, and When You Do Not Need a Key Table (15 min)

**Story:** *"How would you make this idempotent?" is asked about much more than payment endpoints.
Apply what you built to three other cases, then decide when the key table is the wrong tool.*

**Steps:**
1. A consumer reads an `OrderPlaced` message from a queue and sends a confirmation email. The email
   was sent three times for one message. Why can that happen? What plays the role of the
   `Idempotency-Key`, and where do you store it?
2. A payment provider calls your webhook `POST /webhooks/payment-succeeded`. It retries until you
   answer 200. What do you use as the key?
3. `PUT /api/customers/1` with the full customer in the body. Sent three times. Do you need a key
   table? Why not?
4. Orders have an `order_id` made up by the client. Payments are "one per order". Write the
   constraint and the insert that make the payment idempotent without any key table.
5. `POST /api/customers/1/balance/add` with `{"amountCents": 500}`, and
   `PUT /api/customers/1/balance` with `{"balanceCents": 10500}`. Which one is safe to retry?
6. Somebody proposes `GET /api/orders/42/cancel` because "it is easier to call from a link". What
   goes wrong?

**Acceptance Criteria** (no tests: you answer)
- [ ] You can explain at-least-once delivery and why a consumer must expect duplicates
- [ ] You can give two ways to be idempotent without a key table, and one case that needs the table
- [ ] You can explain why GET must be safe, and what breaks when it is not
- [ ] You can give the model answer in "How to say it in an interview"
      ([`TASKS_GUIDED.md`](TASKS_GUIDED.md), Task 9) in your own words

---

## Questions to Ask Afterwards

Answer out loud, in full sentences, before reading the answer in brackets.

- What is idempotency? (An operation is idempotent if doing it several times has the same effect on
  the server as doing it once. It is about the effect, not the response. It matters because networks
  lose answers: a client that gets a timeout cannot know whether the request was processed, so it
  has to retry, and the retry must not repeat the effect.)
- How would you make a payment endpoint idempotent? (The client generates a unique key per payment,
  normally a UUID, and sends it in an `Idempotency-Key` header on every attempt. The server has a
  table with a unique constraint on client and key. In one transaction it first inserts the key,
  then charges, then stores the response in that row. If the insert finds the key already there, it
  does not charge: it returns the stored response. It also stores a hash of the request and rejects
  the same key with a different body.)
- Which HTTP methods are idempotent? (GET, HEAD, PUT, DELETE and OPTIONS are idempotent by
  definition. GET, HEAD and OPTIONS are also safe: they must not change anything. POST is not
  idempotent, and neither is PATCH in general. PUT replaces a resource with what is in the body, so
  twice gives the same state as once. DELETE twice leaves the resource deleted, even if the second
  answer is a 404.)
- Why is POST not idempotent? (POST means "process this", typically "create a new thing". Nothing in
  the request says which thing, so two identical POSTs are two creations. The idempotency key adds
  the missing identity: it names the operation, so the server can recognise the second copy.)
- Is cancelling an order with a GET a problem? (Yes. GET must be safe: it must not change state.
  Browsers prefetch links, crawlers follow them, proxies and clients retry and cache GETs freely, and
  link previews in chat apps open them. Any of those would cancel the order. A state change is a POST,
  or a PUT or PATCH on the order's status, or a DELETE.)
- One message was received and the email was sent three times. Why, and how do you fix it? (Queues
  deliver at least once. If the consumer sends the email and then crashes, or is too slow to
  acknowledge, the broker delivers the message again, and the consumer sends again. The fix is the
  same technique: the message has an id, the consumer records "processed message X" in a table with
  a unique constraint, in the same transaction as its own work, and skips a message whose id is
  already there. Exactly-once delivery does not exist in general. At-least-once delivery plus an
  idempotent consumer gives the same result.)
- Who should generate the key: the client or the server? (The client. The whole problem is that the
  client does not know whether the server received the first request. A key made by the server would
  be in the answer the client never got. The client creates the key before the first attempt and
  reuses it for every retry of that same operation.)
- Why is "look the key up, then insert it" not enough? (It is check-then-act. Two requests with the
  same key can both run the check before either has inserted. Both see "new" and both charge. Only
  the database can decide atomically, with a unique constraint and an insert that either wins or
  loses.)
- What does the second request get while the first is still in progress? (Two valid designs. Here
  the key row and the charge are one transaction, so the second insert waits on the unique index
  until the first commits, then the second request returns the stored response. The other design
  commits the key row first with a status like IN_PROGRESS, does the work outside that transaction,
  and answers a concurrent duplicate at once with `409 Conflict`, "request in progress, retry
  later". That keeps transactions short when the work is a slow external call, but needs a rule for
  rows that stay IN_PROGRESS after a crash.)
- Why return the same response and not just "already done"? (The client should not need two code
  paths. For the client the retry is the request. It should get what the original got: the same
  status and the same body, including values that have changed since. A header such as
  `Idempotent-Replay: true` lets logs and curious clients tell the difference.)
- What if the same key arrives with a different body? (Reject it, `422 Unprocessable Content`.
  Returning the stored response would tell the client its new request succeeded when nothing
  happened. The server compares a stored hash of the request with the hash of the new one.)
- How long do you keep keys, and what is the scope? (Scope: per customer or per API client, so two
  clients can never collide or read each other's responses. Lifetime: longer than the longest time a
  client keeps retrying, often 24 hours. After expiry the key is unknown and a retry is a new
  request. Keeping them for ever means a table that only grows.)
- The card processor is a real external service. What changes? (A database rollback does not undo
  a call to it. You pass your idempotency key on to the processor, so that your own retry of that
  call is safe as well. And you avoid holding a database transaction open across a slow remote call:
  that is where the IN_PROGRESS design comes in.)
- When do you not need an idempotency-key table? (When the operation is naturally idempotent: a PUT
  of a full resource, a DELETE, setting a status to a fixed value, or an insert protected by a
  business unique key such as one payment per `order_id`, written as
  `INSERT ... ON CONFLICT DO NOTHING`. "Add 5 to the balance" is not idempotent. "Set the balance to
  105" is.)

## Tips for Interviewers

- The most common weak answer is "I would check if the payment already exists". Ask how, and then
  ask what happens when two requests check at the same moment. Candidates who say "unique
  constraint" without being asked are at the right level.
- Ask who generates the key. "The server returns an id and the client sends it back" misses the
  point: the client never received the first answer.
- Ask the candidate to predict `./demo --scenario double-click` after Task 2, before running it.
  Then ask them to predict it again with only the constraint added (Task 4, step 1): one 201, one
  500, and the card processor called twice. That second prediction separates levels.
- "Timeout means it failed" is the misconception to listen for in Task 1. A good candidate says the
  client cannot distinguish a lost request from a lost response.
- For stronger candidates, push on Task 5 step 6 and on the external call: what if the process dies
  after the card was charged and before the commit? The honest answer involves passing the key to
  the processor, or reconciling afterwards. Nobody solves it with a local transaction.
- Task 9 is where the topic becomes general. A candidate who connects the key table to a
  "processed messages" table for a queue consumer has understood the idea, not the endpoint.
