# Interview Tasks — Rate Limiter

Sequential user stories, one at a time. Budget **30 minutes**.

---

## Task 1: Fixed Budget (6 min)

**Story:** *`tryAcquire(clientId)` returns `true` for the first N calls per client and `false`
after.*

**Acceptance Criteria:**
- Bucket size 0 or less, negative refill rate, null clock and blank client id are rejected
- Exactly N calls succeed, then every call fails (with no refill)

---

## Task 2: Refill Over Time (10 min)

**Story:** *Tokens refill at R per second up to the bucket size N. Use `java.time.Clock` so the
tests can move time forward without sleeping.*

**Acceptance Criteria:**
- After 1 second at 1 token/s, exactly one more call succeeds
- Fractional refill: 0.4 s at 2 tokens/s is not enough, 0.5 s is
- Refill never exceeds N, however long the wait
- A client that has never called starts with a full bucket
- A clock that goes backwards does not mint tokens

**Hints:**
- Write a `MutableClock extends Clock` in the tests with an `advance(Duration)` method
- Compute refill lazily from elapsed time inside `tryAcquire`; no background thread

---

## Task 3: Per-Client Isolation (4 min)

**Story:** *Client A exhausting its bucket must not affect client B.*

---

## Task 4: Many Threads (10 min)

**Story:** *8 threads call `tryAcquire` for the same client at the same time. Exactly N calls
succeed.*

**Acceptance Criteria:**
- 1000 contended calls against a bucket of 50 admit exactly 50
- 8 first-time calls for a new client create one bucket (two buckets would admit two)

**Hints:**
- `computeIfAbsent` for the bucket; the bucket guards its own state
- Clients then never contend with each other, only with themselves

---

## Questions to Ask Afterwards

- Token bucket vs sliding window vs fixed window: what does each get wrong?
- Where does the limiter live in a microservice architecture, and how does it work across many
  instances? (Redis `INCR` with a TTL, or a Lua script for the bucket)
- What happens to memory with millions of clients? (Evict idle buckets, or move them to Redis)

## Tips for Interviewers

- If the candidate reaches for `Thread.sleep` in a test, ask how long the suite will take with
  fifty such tests. The injected clock is the expected answer.
