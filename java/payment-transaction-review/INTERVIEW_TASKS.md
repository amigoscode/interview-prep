# Interview Tasks — Payment Transaction Review (Live Code Review)

This is a **live code review**, run in one sitting of about 60 minutes. Unlike the other projects,
you do not pick one or two small tasks: the candidate reviews the whole pull request first, and the
tasks below are what you ask for afterwards (one fix, one extension), matched to their level.

If you are practising as the candidate, attempt the brief in [`README.md`](README.md) first. This file
is the answer key.

**There are 36 planted defects in about 450 lines.** Nobody finds all of them in 20 minutes, and that
is the point. A strong senior review names most of the blocking ones, half of the major ones, and
waves the minor ones through as "nit".

---

## Running the Session

### Run sheet

| Minutes | Part | Interviewer does |
|---|---|---|
| 0 to 5 | Setup | Candidate has the project open and shares the screen with the IDE open. Read the briefing below |
| 5 to 7 | Context questions | Wait. Do not volunteer the answers. Note whether the candidate asks before reading |
| 7 to 27 | Read and review out loud | Stay quiet for the first 5 minutes. Then ask "why" on each finding |
| 27 to 42 | Fix | Ask for the refund fix (Task 5) |
| 42 to 50 | Extend and scale | One of Tasks 7 to 10 |
| 50 to 55 | Follow-ups | Three or four from the list below |
| 55 to 60 | Candidate's questions | Their top three questions for the engineers |

### Briefing, read aloud

"This is the first version of the transaction API for a payment gateway.
Merchants create, capture, refund and void card transactions. A colleague opened
this as a pull request and the team wants to ship it. Please review it as you
would a real pull request. There is more wrong than you can cover, so prioritise:
serious issues count for more than small ones. Think out loud. Later I will ask
you to fix something and to extend it."

### Answers to the context questions

Give these only when asked.

| Question | Answer |
|---|---|
| Who calls it? | Merchants' servers, over the internet, with a token from our identity provider |
| Pull request or production code? | A pull request, first version, nothing live yet |
| Comments only, or fixes? | Review first, then I will ask for one fix |
| Load? | About 500 writes a second at peak; the largest merchant has 40 million transactions |
| Where does the merchant come from? | The `merchant_id` claim in the token |
| Is there a broker? | Assume Kafka is there in production; locally it is optional |
| Who wrote it? | A colleague in their first year |
| Can I use an AI assistant? | No, for this mock. Ask the real recruiter the same question |

### Pushing on a finding

For any finding, ask in this order and stop when the answer runs out:

1. "Why did you flag this?"
2. "What happens in production?"
3. "What would you do instead?"
4. "What does that cost?" (the trade-off)
5. "How would you test it?"

---

## Junior Level (15-20 min each)

### Task 1: Find Five Problems and Rank Them

**Context:** The whole project. A junior candidate is not expected to cover it.

**What to do:**
1. Read the controller, the service and the security configuration
2. Name five problems, most serious first
3. For each one, say what would happen in production

**Acceptance Criteria:**
- At least two of the five are from the blocking list below
- The list is ranked: not in file order, and style points are not at the top
- Each finding has an impact, not only a name

**Hints:**
- "Try `GET /api/v1/transactions/1` with no token. What do you see?"
- "Who decides which merchant a request belongs to?"
- "What type is the amount?"

---

### Task 2: Fix the Status Filter and the Error Responses

**Context:** `GET /api/v1/transactions?merchantId=merchant-a&status=CAPTURED` always returns an empty
list, and a transaction that does not exist returns a 500.

**What to do:**
1. Find out why the filter never matches and fix it
2. Return 404 for a missing transaction
3. Return a 4xx with a clear body for a refund that breaks a rule, instead of 500

**Acceptance Criteria:**
- Names the `==` on a `String` in `TransactionService.findByMerchant`
- Parses the status into the enum; an unknown status is a 400
- A `@RestControllerAdvice` (or equivalent) with one error body; no `Optional.get()`
- The try/catch that turns everything into a 500 is gone from the controller

**Hints:**
- "What does `==` compare for two strings?"
- "Which status code tells the caller the fault is theirs?"

---

## Mid-Level (20-25 min each)

### Task 3: Take the Merchant From the Token

**Context:** The merchant is read from the query string or the request body. Any caller can read,
capture or refund another merchant's transactions, and every GET is public.

**What to do:**
1. Require a token on every route
2. Read `merchant_id` from the JWT and use it for every read and write
3. Enforce the scopes: `transactions:read` for reads, `transactions:write` for writes
4. Make void a POST

**Acceptance Criteria:**
- `authorizeHttpRequests` authenticated by default; no `permitAll` on the API
- `@AuthenticationPrincipal Jwt`; the `merchantId` request parameter and body field are gone
- Lookups are by id and merchant together; someone else's transaction is a 404, not a 403
- A read only token gets 403 on a refund
- Names the test: merchant B asks for merchant A's transaction and gets 404

**Hints:**
- "Run the refund in `requests.http` with merchant B's token against transaction 3."
- "Why 404 and not 403?" (403 confirms the id exists.)

---

### Task 4: Stop Accepting the Entity as the Request Body

**Context:** `POST /create` binds the JPA entity. A caller can send an `id` and overwrite an existing
transaction, set its status, and gets the card number and CVV back in the response.

**What to do:**
1. Add a request record and a response record
2. Validate the request
3. Remove card data from the response, the log line and the Kafka event

**Acceptance Criteria:**
- Records for request and response; the entity never leaves the service
- `spring-boot-starter-validation` added to `build.gradle` (without it `@Valid` does nothing)
- Amount greater than zero, currency and reference required; a bad request is a 400 or 422
- Says that the CVV must never be stored at all, and that the card number belongs in a vault

**Hints:**
- "Send `{\"id\": 3, \"merchantId\": \"merchant-b\", \"reference\": \"x\", \"amount\": 1}`. What happened to
  transaction 3?"
- "Read the application log after a create."

---

## Senior Level (25-30 min each)

### Task 5: Make Refund Safe

**Context:** This is the fix to ask for by default. Refund has no owner check, no lower bound, a
read-check-write race, a transaction annotation that does nothing, no idempotency and an event that
can be lost.

**What to do:** Ask the candidate to make refund safe. A good fix contains the following, in the order a strong candidate does it.

**Acceptance Criteria:**
1. Owner from the token: `@AuthenticationPrincipal Jwt jwt`, then
   `findByIdAndMerchantId`, 404 when absent.
2. A request record with the amount in minor units, validated greater than zero.
3. One public `@Transactional` method. Status and cap checked inside the entity.
4. `@Version` on `Transaction` plus a migration that adds the column; a conflict
   becomes 409.
5. An `Idempotency-Key` header saved on the refund row under a unique constraint;
   a replay returns the first refund.
6. The event written to an outbox table in the same transaction, or at least
   published after commit, keyed by transaction id.
7. The test: two threads refund 80 of 100 at once, exactly one succeeds.

Finishing all seven in 15 minutes is not expected. Steps 1, 3 and 4 in code with the rest said out
loud is a pass.

**Hints:**
- "Is `saveRefund` transactional?" (No: private, and called from inside the class.)
- "Two refunds of 80 on a capture of 100 arrive together on two instances. Walk me through it."
- "Would `synchronized` fix it?"

---

### Task 6: Make the Events Reliable

**Context:** The database write and the Kafka send are separate. A failed send is caught and logged
with no id. The settlement listener adds to the settled amount on every delivery and drops any
message it cannot process.

**What to do:**
1. Say every way the database and the topic can disagree today
2. Design the fix for the producer
3. Make the listener safe against redelivery and bad messages

**Acceptance Criteria:**
- Transactional outbox (or publish after commit plus reconciliation), with the trade-off named
- Messages keyed by transaction id; a typed event with an id, a type and a version; no card data
- Listener deduplicates on an event id or sets instead of adds, and guards the state
- Bounded retries and a dead letter topic instead of catch and log

**Hints:**
- "Should the send be inside the transaction?"
- "Send the same settlement message twice (the command is at the bottom of this file)."

---

### Task 7: Extension — Partial Captures

**Context:** "Merchants want to capture in several parts."

**Acceptance Criteria:** contract and status codes first; a captures table added by a migration; the
sum capped at the authorised amount; idempotency; one event per capture; money in minor units.

### Task 8: Extension — The List Times Out

**Context:** "The largest merchant has 40 million transactions and the list endpoint times out."

**Acceptance Criteria:** query by merchant in the database; cursor pagination on `(created_at, id)`;
an index; a cap on page size; a search store for free text; `open-in-view` off and no N+1.

### Task 9: Extension — Webhooks

**Context:** "Tell merchants about every status change."

**Acceptance Criteria:** store first, then send; signed payload; retries with backoff; an event id so
the merchant can deduplicate; a way to replay.

### Task 10: Extension — A Second Payment Method

**Context:** "Add a payment method that is not a card."

**Acceptance Criteria:** card fields out of the transaction; a payment method type; one strategy per
method; the state rules unchanged.

---

## Answer Key: Every Planted Defect

Paths are under `src/main/`. Short names used below:

- `SecurityConfig` = `java/com/interview/transaction/config/SecurityConfig.java`
- `Controller` = `java/com/interview/transaction/api/TransactionController.java`
- `Service` = `java/com/interview/transaction/service/TransactionService.java`
- `Transaction` = `java/com/interview/transaction/domain/Transaction.java`
- `Publisher` = `java/com/interview/transaction/messaging/TransactionEventPublisher.java`
- `Listener` = `java/com/interview/transaction/messaging/SettlementListener.java`
- `V1`, `V2` = `resources/db/migration/`

For the commands, start the app and set:

```bash
U=localhost:8081/api/v1/transactions
A=$(python3 scripts/mint-token.py merchant-a)
B=$(python3 scripts/mint-token.py merchant-b)
RO=$(python3 scripts/mint-token.py merchant-b transactions:read)
```

Restart the app between demonstrations; several of them change the seed data.

### Tier 1: blocking (must not reach production)

| # | Defect | Where | Impact | Fix | Prove it |
|---|---|---|---|---|---|
| 1 | Every GET is public | `SecurityConfig:28` | Anyone on the network reads any transaction, with card data, with no token | Authenticated by default; no public routes on this API | `curl $U/1` returns 200 and a card number |
| 2 | A GET changes state: void | `Controller:69` | With defect 1, anyone can void any transaction with no token. Also crawlers, prefetch and retries will void | `POST /{id}/void` (or `/voids`), authenticated, owner checked | `curl $U/4/void` returns VOIDED |
| 3 | No ownership check anywhere. The merchant comes from the query string or the body, never from the token | `Controller:39, 42, 53, 59`, `Service:41` | Merchant B refunds, captures and lists merchant A's transactions. Ids are sequential, so they can be walked | Read `merchant_id` from the JWT (`@AuthenticationPrincipal Jwt`), query by id and merchant together, answer 404 when it is not theirs | `curl -X POST -H "Authorization: Bearer $B" "$U/3/refund?amount=20"` returns 200 on merchant A's transaction |
| 4 | Scopes are never checked | `SecurityConfig:30` | A read only token can refund | `hasAuthority("SCOPE_transactions:write")` per route | Same refund with `$RO` returns 200 |
| 5 | SQL injection | `Service:67` | Public (defect 1) and injectable: dumps the whole table, or worse | A derived query or a bound parameter; never concatenate | `curl -G $U/search --data-urlencode "reference=' OR '1'='1"` returns every merchant's rows |
| 6 | CVV stored, card number stored in clear | `Transaction:34, 36`, `V1`, seed rows in `V2` | Storing the CVV after authorisation is forbidden by PCI DSS; a clear card number puts the whole service in PCI scope | Never persist the CVV; tokenise the card number in a vault and keep the token and last four | Any GET shows `cvv` |
| 7 | Card number and CVV in logs, in the API response and in the Kafka event | `Controller:33` with `Transaction:74`, `Publisher:26`, every endpoint returns the entity | Card data spreads to log storage, every consumer of the topic and every caller | Log ids only; response records without card data; no card data in events | Create a transaction and read the app log |
| 8 | Money as `double`, in Java and in the schema | `Transaction:24 to 29`, `Service:23, 49, 77, 89, 107`, `V1` (`DOUBLE`) | Rounding errors in refunds and fees; reconciliation breaks. JPY has no decimals and is treated like EUR | Minor units as `long` (or `BigDecimal` with scale and rounding mode), `BIGINT` or `DECIMAL` columns, currency always beside the amount | Refund 0.10 then 0.20 on transaction 2 (captured 0.30): the second is rejected as "exceeds captured amount" |
| 9 | Refund has no lower bound | `Service:85`, `Controller:59` | A negative refund is accepted and lowers the refunded total, so more can be refunded than was captured | Validate amount greater than zero; 422 otherwise | `curl -X POST -H "Authorization: Bearer $A" "$U/3/refund?amount=-500"` returns 200 |
| 10 | Read, check, write with no lock or version | `Service:86 to 92, 107`, also capture `Service:73 to 80` | Two refunds at once both pass the check and over-refund. A real money loss | `@Version` and a retry or 409; or a pessimistic lock; or one conditional UPDATE. `synchronized` does not help across instances | By reading. The disabled test admits it (`TransactionServiceTest`, `@Disabled("flaky on CI")`) |
| 11 | `@Transactional` on a private method called from the same class | `Service:97, 98` | No transaction at all. The refund row can be saved and the transaction update lost, or the reverse. `refund` also throws a checked `Exception`, which would not roll back anyway | One public transactional method per use case; unchecked domain exceptions | By reading; ask "is this method transactional?" |
| 12 | No real idempotency. The "retry cache" is wrong in five ways | `Service:39, 42, 52` | It is a plain `HashMap` (not thread safe, unbounded, lost on restart, not shared between instances), keyed by reference without the merchant, so merchant B gets merchant A's transaction back. Capture and refund have nothing: a retried refund refunds twice | An `Idempotency-Key` header stored with a unique constraint per merchant; a replay returns the first response; same key with a different body returns 409 | Create `order-9` as A, then create `order-9` as B: B receives A's transaction |
| 13 | The request body is the entity, so the client sets the id | `Controller:32`, `Service:51` | `save` on an entity with an id is a merge: any caller overwrites any existing transaction, including its merchant, amount and status | Request and response records; map in the service; never accept an id | `POST /create` with `{"id":3,"merchantId":"merchant-b","reference":"x","amount":1}` as B: transaction 3 now belongs to B |
| 14 | Database write and Kafka send are not atomic, and a failed send is swallowed | `Service:53, 81, 105`, `Publisher:27 to 30` | The event is lost with only "Failed to publish event" in the log (no id, no stack trace), or sent for work that then fails. `send` is asynchronous and its result is ignored. In capture the send sits inside the transaction and holds a connection for up to two seconds | Transactional outbox, relayed to Kafka; or publish after commit plus a reconciliation job | Run with no broker: writes succeed, each takes 2 seconds, the log shows the error |
| 15 | No state rules | `Service:113 to 116` (void), `77, 78` (capture), `108` (refund) | Void works on a captured or refunded transaction; capture accepts any amount, above the authorisation or negative; a partial refund marks the whole transaction REFUNDED | Transitions inside the entity (`tx.capture(amount)`, `tx.refund(amount)`, `tx.voidIt()`); captured within authorised, refunded within captured; a captured transaction is refunded, not voided | `curl -X POST -H "Authorization: Bearer $A" "$U/1/capture?amount=99999"` returns captured 99999 on an authorisation of 49.99 |

### Tier 2: major (fix before or soon after launch)

| # | Defect | Where | Impact | Fix |
|---|---|---|---|---|
| 16 | Signing secret in the repository; symmetric HS256 | `application.properties:11`, `SecurityConfig:37` | Anyone with the repository mints tokens for any merchant. No issuer or audience validation | Validate against the identity provider's JWK set (`issuer-uri`); secrets from the environment or a secret store |
| 17 | H2 console on and public; frame options off for the whole app | `application.properties:9`, `SecurityConfig:25, 29` | A SQL console on the open network | Remove it, or a dev profile only |
| 18 | `@Enumerated` with no type is ORDINAL; column is `INT` | `Transaction:31`, `V1` | Reorder or insert an enum value and every stored status changes meaning | `EnumType.STRING`, a `VARCHAR` column with a check constraint |
| 19 | Listener is not idempotent | `Listener:34, 35` | Kafka delivers at least once: a redelivered message doubles the settled amount. It also sets SETTLED from any state, including VOIDED | Deduplicate on an event id (unique table) or set the amount instead of adding; state guard |
| 20 | Listener swallows every failure | `Listener:37 to 39` | A bad or early message is logged at WARN and gone for good: no retry, no dead letter topic, no stack trace. Money silently unreconciled | Let it throw; `DefaultErrorHandler` with bounded retries and a dead letter topic; alert on it |
| 21 | Error handling | `Controller:39, 64 to 66`, `Service:46, 75` | Business rule failures return 500 with the raw exception message; a zero amount returns 200 with an empty body; a missing id throws from `Optional.get()` and becomes a 500 with a token, or a 401 without one because the error page is itself protected | `@RestControllerAdvice`, one error body; 404, 409 and 422 on purpose |
| 22 | No validation | `Controller:32, 54, 60` | Null merchant, null currency, unknown fields, any amount. The validation starter is not in `build.gradle`, so adding `@Valid` alone would do nothing | Add `spring-boot-starter-validation`; constraints on request records |
| 23 | List loads the whole table and filters in memory; no pagination | `Service:58 to 62` | Fine with five rows, falls over with millions. Load is a reported theme of this kind of round | `findByMerchantIdAndStatus` with `Pageable` or a cursor; index on `(merchant_id, created_at)` |
| 24 | String compared with `==` | `Service:60` | The status filter never matches a value from the request: the list is always empty when a status is given. Also an NPE if a row has no status | Parse to the enum and compare enums, in the query |
| 25 | N+1 on refunds, hidden by Open Session in View | `Transaction:39`, every list response | One extra query per transaction while the JSON is written | Response records; fetch what is needed; `spring.jpa.open-in-view=false` |
| 26 | Schema has no constraints | `V1` | Every column nullable; no foreign key on `refunds.transaction_id`; no unique `(merchant_id, reference)`; no index on `merchant_id`; `VARCHAR(255)` for a currency | NOT NULL, foreign key, unique and check constraints, indexes, `CHAR(3)` |
| 27 | Test data in a versioned migration | `V2` | Fake transactions with card numbers would be inserted in production | A dev profile location or test fixtures |
| 28 | Events: no key, JSON built by hand, no event id or version | `Publisher:21 to 27` | No ordering per transaction across partitions; a quote in a reference breaks the JSON; consumers cannot deduplicate. REFUND_CREATED carries the transaction amount, not the refund amount. Void publishes nothing | Key by transaction id; a typed event serialised by Jackson; id, type, version, occurred at |
| 29 | Tests prove nothing | `TransactionServiceTest` | Everything mocked, only `assertNotNull`; the concurrency test is disabled and has no assertion; nothing covers security or the money rules | Name three: concurrent double refund, replayed idempotency key, cross-merchant read returns 404 |

### Tier 3: minor (say "nit" and move on)

| # | Defect | Where |
|---|---|---|
| 30 | Field injection everywhere; the controller reaches into the repository directly | `Controller:25 to 29`, `Service:26 to 36` |
| 31 | REST shape: `/create` in the path, 200 where 201 fits, refund amount as a query parameter | `Controller:31, 59` |
| 32 | `LocalDateTime.now()` with no zone or `Clock`; hard to test and wrong across regions | `Service:50, 103`, `Transaction:37` |
| 33 | Fee rate hard coded, same for every merchant and currency | `Service:23, 24, 49` |
| 34 | `new ObjectMapper()` for every message | `Listener:29` |
| 35 | `RuntimeException` and `Exception` instead of domain exceptions; `Collectors.toList()` and a hand written comparator | `Service:62, 75, 90` |
| 36 | Kafka client logs silenced to ERROR; no metrics, no audit trail of who changed what | `application.properties:16` |

Not defects, so mark down a candidate who flags them: `static void main` without
`public` (valid in Java 25), CSRF disabled on a stateless token API (correct),
`ddl-auto=none` with Flyway (correct).

### Trying the Kafka findings

With `docker compose up -d` running, send a settlement message by hand. Send it twice and transaction
3 shows a settled amount of 240 on a capture of 120:

```bash
echo '{"transactionId": 3, "amount": 120.00}' | docker compose exec -T kafka \
  /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic settlements
```

---

## Follow-up questions

- "If you could fix only one thing before production, which?" (Good: public reads
  plus no ownership check, or stored CVV. Weak: anything from tier 3.)
- "Should the Kafka send be inside the transaction?" (No: it is not part of the
  database transaction, it blocks a connection, and it cannot be rolled back.
  Outbox.)
- "The refund call times out on the merchant's side. What do they do, and what do
  we do?" (They retry with the same idempotency key; we return the first result.)
- "Two refunds for the same transaction arrive in the same millisecond on two
  instances. Walk me through it." (Both read, both pass, both write. Version
  column or lock; `synchronized` is no help.)
- "Optimistic or pessimistic locking here, and why?" (Optimistic: conflicts on
  one transaction are rare. Pessimistic for a hot row such as a balance.)
- "Is `saveRefund` transactional?" (No: private and called from inside the class,
  so the proxy never sees it.)
- "The settlement message arrives twice. What happens?" (Settled amount doubles.)
- "The settlement message arrives before the transaction is committed. What
  happens?" (`get()` throws, caught, logged, lost.)
- "How would you roll out the change from double to minor units with data already
  in the table?" (Add a column, write both, backfill, switch reads, drop the old
  column. Never in one step.)
- "This table moves to DynamoDB. What changes?" (Key design by merchant and id,
  conditional writes instead of `@Version`, a global secondary index for the
  reference, streams instead of an outbox table.)
- "What would you put on a dashboard for this service?" (Rate, errors, latency by
  route; refund conflicts; outbox lag; consumer lag; dead letter count.)

---

## Scorecard

Score each line 1 to 5. Half the weight is what was found, half is how it was
explained, as peers report for this kind of round.

| Area | 1 | 3 | 5 |
|---|---|---|---|
| Asked for context before reading | Started reading at once | One question | Purpose, callers, review or fix, load |
| Tier 1 found (of 15) | 4 or fewer | 7 to 9 | 11 or more |
| Ranking | In file order | Mostly serious first | "Three blocking, then improvements", nits labelled |
| Explanation | Names the smell only | Impact or fix | What, production impact, fix, test, one trade-off |
| Security and card data | Missed | Found public GET or ownership | Token identity, scopes, CVV, logs, injection |
| Concurrency and transactions | Missed | Found one | Race, proxy rule, idempotency, outbox |
| The fix | Could not start | Working but partial | Small steps, build run, test named or written |
| Extension and load | Boxes only | Contract or data | Contract, migration, state rule, event, load |
| Tone | Blames the author | Neutral | About the code, asks before assuming, says when unsure |
| Time | Lost in one file | Covered most files | All files read once, time left for questions |

---

## Common failure patterns to watch for

- Starting with style: field injection and naming before security and money.
- Reading in silence for ten minutes.
- Listing 20 findings with no ranking.
- Fixing without being asked, or typing in silence during the fix.
- Flagging modern Java syntax as an error.
- Saying "add `@Transactional`" without noticing why the existing one does nothing.
