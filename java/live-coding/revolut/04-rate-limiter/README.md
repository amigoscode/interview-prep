# 04 Rate limiter: token bucket with an injected clock

Occasionally asked at Revolut and a favourite everywhere else. It tests the same two things
as the ledger: a small stateful domain and correctness under many threads, plus one new
skill: making time-dependent code testable by injecting a `Clock`. Time yourself: **30 minutes**.

```bash
./mvnw -pl 04-rate-limiter test
```

## User stories

### Story 1: fixed budget

> `tryAcquire(clientId)` returns `true` for the first N calls per client and `false` after.

### Story 2: refill over time

> Tokens refill at R per second up to the bucket size N. Use `java.time.Clock` so the tests
> can move time forward without sleeping.

Edge cases: refill never exceeds N, a client that has never called gets a full bucket,
fractional refill (0.5 s at 2 tokens/s is 1 token).

### Story 3: per-client isolation

> Client A exhausting its bucket must not affect client B.

### Story 4: many threads

> 8 threads call `tryAcquire` for the same client at the same time. Exactly N calls succeed.

## Questions the interviewer asks afterwards

- Token bucket vs sliding window vs fixed window: what does each get wrong?
- Where does the limiter live in a microservice architecture, and how do you make it work
  across many instances? (Redis `INCR` with a TTL, or a Lua script for the bucket.)
- What happens to memory when millions of clients each have a bucket?

## Solution

`git checkout solutions/revolut/04-rate-limiter`, then `./mvnw -pl 04-rate-limiter test`.
