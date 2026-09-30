# Interview Tasks — Order API

Sequential user stories, one at a time. Budget **45 minutes**.

---

## Task 1: Create and Read an Order (10 min) (reported at Delivery Hero, 2023)

Reported for a Senior Software Engineer II interview in Berlin as "Implement 2 REST APIs".

**Story:** *`POST /orders` creates an order for a customer at a restaurant, with items (name,
quantity, priceCents). `GET /orders/{id}` returns it.*

**Acceptance Criteria:**
- `POST /orders` returns `201 Created`, a `Location` header pointing at `/orders/{id}`, and the
  order: `id`, `status` `CREATED`, the items and `totalCents`
- `totalCents` is computed by the server (sum of quantity x priceCents); a total sent by the
  client is ignored
- `GET /orders/{id}` returns the order, or `404` for an unknown id
- Money is an integer number of cents, never a `double`

**Hints:**
- `ServletUriComponentsBuilder.fromCurrentRequestUri().path("/{id}")` builds the `Location`
- A `ConcurrentHashMap<UUID, Order>` behind a small repository class is enough

---

## Task 2: List, Update, Cancel (12 min) (reported at Delivery Hero, 2023)

Reported for an engineering manager round as "implement a CRUD API".

**Story:** *A customer lists their orders, page by page. While an order is still `CREATED` they can
change its items or cancel it.*

**Acceptance Criteria:**
- `GET /orders?customerId=...&page=0&size=20` returns only that customer's orders, with `page`,
  `size`, `totalElements` and `totalPages`; a page past the end is empty, not an error
- `PUT /orders/{id}` replaces the items and recomputes the total
- Cancel with either `DELETE /orders/{id}` or `POST /orders/{id}/cancel`: choose one and justify it
- Status codes: `400` for a missing `customerId` or bad paging, `404` for an unknown order, `409`
  when updating an order that is no longer `CREATED`
- Decide (and say) what cancelling an already cancelled order returns

**Hints:**
- A cancelled order still exists (the customer, support and the restaurant need it), which is an
  argument for `POST .../cancel` over `DELETE`
- An update racing a cancel must not change a cancelled order: `ConcurrentHashMap.computeIfPresent`
  applies a change atomically

---

## Task 3: Validation and Error Bodies (8 min) (predicted)

Predicted from Delivery Hero job ads and their Reliability Manifesto: clients get clear, consistent
errors.

**Story:** *Reject bad input with Bean Validation, and return every error as an RFC 9457
`ProblemDetail` (`application/problem+json`) from one `@RestControllerAdvice`.*

**Acceptance Criteria:**
- `customerId`, `restaurantId` and item `name` are not blank; `items` is not empty
- `quantity` is 1 to 50 (both ends allowed); `priceCents` is positive; missing numbers are errors,
  not zeros
- A `400` body names each invalid field (for example `items[0].quantity`)
- `404`, `409`, malformed JSON and a non-UUID id are all `ProblemDetail` bodies too
- The same rules apply to `PUT`

**Hints:**
- `@Valid` on the `@RequestBody`, `List<@Valid OrderItemRequest>` for nested items
- Extending `ResponseEntityExceptionHandler` turns Spring's own 400s into `ProblemDetail` for free
- Once a handler has constraints on plain parameters (`@Min` on `page`), Spring validates the whole
  method and raises `HandlerMethodValidationException` instead of `MethodArgumentNotValidException`

---

## Task 4: Idempotent Retries (10 min) (predicted)

Predicted: idempotency is one of Delivery Hero's reliability rules.

**Story:** *The mobile app times out on `POST /orders` and retries. Support an `Idempotency-Key`
header so a retry never creates a second order.*

**Acceptance Criteria:**
- Same key and same body returns the original order (same `id`). Choose `201` or `200` and justify
- Same key with a different body returns `422` (or `409`: choose)
- No key behaves as before; different keys create different orders
- Two simultaneous requests with the same key create exactly one order. Prove it with a test:
  `ExecutorService` plus a `CountDownLatch` start gate

**Hints:**
- `get` then `put` on a map is a race; `ConcurrentHashMap.computeIfAbsent` runs the creation once
  per key
- Scope the key to the customer, so two customers who pick the same key do not collide
- Replaying the original response (`201`, same `Location`) means the client needs no special case

---

## Task 5: Rate Limit a Noisy Customer (5 min) (reported at Delivery Hero, 2023)

Reported as a follow-up question: "What would you do if a customer repeatedly sends 500 requests to
the same API in production?"

**Story:** *Limit `POST /orders` per customer. Over the limit, return `429 Too Many Requests` with a
`Retry-After` header.*

**Acceptance Criteria:**
- A burst up to the bucket size succeeds, the next call is `429` with `Retry-After` in whole seconds
  (rounded up) and a `ProblemDetail` body
- After waiting, calls succeed again; one customer never uses up another's budget
- Rejected requests create no order; reads are not limited
- Tests move an injected `Clock`, never `Thread.sleep`

**Hints:**
- An in-memory token bucket per customer, refilled lazily from `Clock.instant()`
- For the full exercise (fractional refill, backwards clocks, contention), see `java/rate-limiter`

---

## Questions to Ask Afterwards

- How does rate limiting work across many instances? (Redis with a Lua script or `INCR` plus TTL,
  or at the API gateway before the service)
- Where are idempotency keys stored in production, and for how long? (Redis or a DB table with a
  unique constraint, TTL around 24 hours; what happens to a request still in flight?)
- `PUT` vs `PATCH` for changing the items: which did you use and why?
- How would you secure this API? (JWT from the identity provider; authorization so a customer can
  only see and change their own orders, which also means `customerId` comes from the token, not the
  query string)
- What is CORS, and when would this API need it? (reported question)
- What would you log and measure? (orders created, 4xx and 5xx by endpoint, 429s per customer,
  idempotent replays, latency percentiles; never log payment data)
- After an order is placed it must reach payment and the restaurant. How? (saga across services,
  transactional outbox so the event is published exactly when the order is committed)

## Tips for Interviewers

- Ask for the status code before they write each endpoint. `200` for a create, or `500` for an
  unknown id, is worth a follow-up question.
- If the total is taken from the request, ask what a malicious client would send.
- A candidate who reaches for `synchronized` on the whole service for Task 4 has a correct answer;
  ask what it does to throughput across unrelated customers, then point at `computeIfAbsent`.
- Should an idempotent replay spend a rate-limit token? There is no single right answer; the
  signal is whether they notice the question.
- If the candidate writes `Thread.sleep` in the rate-limit test, ask how long the suite will take
  with fifty such tests. The injected clock is the expected answer.
