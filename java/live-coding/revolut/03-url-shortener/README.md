# 03 URL shortener: idempotent under concurrency

The other first-round task in the Revolut repertoire. Same shape as the load balancer: small
domain, a strategy to plug in, and a concurrency story at the end. Time yourself: **30 minutes**.

```bash
./mvnw -pl 03-url-shortener test
```

## User stories

### Story 1: shorten and expand

> `shorten(longUrl)` returns a short code. `expand(code)` returns the original URL, or empty
> when the code is unknown.

Edge cases: null or blank input, invalid URL (decide with the interviewer what "valid" means
and write it down), two different URLs never share a code.

### Story 2: idempotent

> Shortening the same URL twice returns the same code.

### Story 3: pluggable code generation

> Support a counter-based base62 strategy and a random 8-character strategy. Codes must stay
> unique either way.

### Story 4: many threads

> 20 threads shorten the same URL at the same time. Exactly one code is created.

Hint: `ConcurrentHashMap.computeIfAbsent` is the whole answer, and being able to say why
(`get` then `put` is two operations, `computeIfAbsent` is one atomic one) is the point.

## Questions the interviewer asks afterwards

- Why base62? How many URLs fit in 7 characters?
- What happens to the counter strategy when you run on two servers?
- Where would this live in production, and what is the cache?

## Solution

`git checkout solutions/revolut/03-url-shortener`, then `./mvnw -pl 03-url-shortener test`.
