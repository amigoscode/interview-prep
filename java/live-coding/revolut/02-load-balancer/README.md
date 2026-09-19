# 02 Load balancer: the classic first-round task

The task Revolut has given in its first live-coding round since at least 2021, reported again
by graduates in Dec 2024 and Aug 2026. Simple on purpose: the marks are for clean structure,
edge cases, tests and how you talk, not for algorithms. Time yourself: **30 minutes**.

```bash
./mvnw -pl 02-load-balancer test
```

## User stories

### Story 1: register

> A load balancer holds up to N instance addresses. `register(address)` adds one and returns
> `true`; it returns `false` when the address is already present or the balancer is full.

Edge cases: capacity of 0 or less is an error, duplicates, null address.

### Story 2: unregister

> `unregister(address)` removes one and returns whether it was present.

### Story 3: get

> `get()` returns one registered address at random. With none registered it throws.

Ask: "random with equal probability?" and "does the caller need to know which one?"

### Story 4: strategy

> Make the selection algorithm pluggable: random and round robin. Round robin must cycle
> through all addresses in registration order and wrap around.

### Story 5: many threads

> Threads register, unregister and `get` at the same time. Nothing may throw a
> `ConcurrentModificationException`, and round robin must stay correct.

## Questions the interviewer asks afterwards

- What is the time complexity of your duplicate check? How does `HashSet.contains` work
  internally? (The word they want is **buckets**, then `equals` inside the bucket.)
- What do `equals` and `hashCode` have to do with each other?
- Why `LinkedHashSet` and not `HashSet` or `ArrayList`?
- Is `synchronized` acceptable here? When would you reach for `ReadWriteLock` or
  `CopyOnWriteArrayList` instead?

## Solution

`git checkout solutions/revolut/02-load-balancer`, then `./mvnw -pl 02-load-balancer test`.
