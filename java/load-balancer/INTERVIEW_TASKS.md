# Interview Tasks — Load Balancer

Sequential user stories, one at a time, each finished with tests before the next. Budget
**30 minutes** for tasks 1 to 4, task 5 if time remains.

---

## Task 1: Register (6 min)

**Story:** *A load balancer holds up to N instance addresses. `register(address)` adds one and
returns `true`; it returns `false` when the address is already present or the balancer is full.*

**Acceptance Criteria:**
- Capacity of 0 or less is rejected in the constructor
- Duplicates return `false` and do not change the size
- `null` is rejected

**Hints:**
- Ask: "Is an address a `String`, or should I model it?" A `String` is fine; say why
- A `Set` gives uniqueness for free; which `Set`, and why, is the follow-up

---

## Task 2: Unregister (3 min)

**Story:** *`unregister(address)` removes one and returns whether it was present.*

**Acceptance Criteria:**
- Removing an unknown address returns `false`
- Removing frees capacity for a new registration

---

## Task 3: Get (6 min)

**Story:** *`get()` returns one registered address at random. With none registered it throws.*

**Acceptance Criteria:**
- Empty balancer throws a specific exception
- Only registered addresses are ever returned
- Over many calls every address is eventually returned

**Hints:**
- Ask: "Equal probability?" and "Does the caller need to know which one?"
- `ThreadLocalRandom` rather than a shared `Random`

---

## Task 4: Strategy (8 min)

**Story:** *Make the selection algorithm pluggable: random and round robin. Round robin must cycle
through all addresses in registration order and wrap around.*

**Acceptance Criteria:**
- A `SelectionStrategy` interface with two implementations
- Round robin over `a, b, c` yields `a, b, c, a, b`
- Round robin adapts when an address is unregistered mid-cycle

**Hints:**
- Registration order means an ordered set: `LinkedHashSet`
- `Math.floorMod` keeps the counter correct after it wraps

---

## Task 5: Many Threads (7 min)

**Story:** *Threads register, unregister and `get` at the same time. Nothing may throw a
`ConcurrentModificationException`, and round robin must stay correct.*

**Acceptance Criteria:**
- A test with 10 threads hammering all three operations completes without exceptions
- Round robin over three addresses under 6 concurrent threads distributes exactly evenly

**Hints:**
- The critical sections are tiny and contention is low: `synchronized` is the right tool, and
  saying when you would switch to a `ReadWriteLock` or `CopyOnWriteArraySet` is the point

---

## Questions to Ask Afterwards

- What is the time complexity of your duplicate check? How does `HashSet.contains` work? (The
  word they want is **buckets**, then `equals` inside the bucket. Candidates have been rejected for
  saying "memory addresses".)
- What do `equals` and `hashCode` have to do with each other?
- Why `LinkedHashSet` and not `HashSet` or `ArrayList`?
- When is `synchronized` not enough?

## Tips for Interviewers

- This round is about code quality and communication. A candidate who writes a perfect
  algorithm silently scores lower than one who narrates a simple one.
- Ask for the tests before the next story, every time.
