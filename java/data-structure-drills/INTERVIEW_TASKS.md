# Interview Tasks — Data Structure Drills

Sequential user stories, one at a time. The interviewer picks **2 of the 4 tasks**. Budget
**45 minutes** for the two, including the questions at the end.

---

## Task 1: Multiply Without `*` (15 min) (reported at talabat, 2023)

**Story:** *`Multiplier.multiply(x, y)` returns `x * y` for two ints without using the `*`
operator. Drive it with JUnit tests.*

**Acceptance Criteria:**
- Positive, negative and mixed signs: `6 x 7 = 42`, `-6 x 7 = -42`, `-6 x -7 = 42`
- Zero on either side gives zero
- Boundaries that fit: `Integer.MIN_VALUE x 1`, `-65536 x 32768 = Integer.MIN_VALUE`
- Overflow throws `ArithmeticException`, like `Math.multiplyExact`
  (`Integer.MAX_VALUE x 2`, `Integer.MIN_VALUE x -1`)
- O(log n) shift-and-add, not a loop of `|y|` additions

**Hints:**
- Russian peasant: while `b != 0`, add `a` when `b`'s low bit is set, then `a <<= 1`, `b >>= 1`
- Do the work on `long` magnitudes: `Math.abs(Integer.MIN_VALUE)` is still negative as an `int`
- The talabat interviewers valued the TDD approach: write the negative, zero and overflow tests
  first

---

## Task 2: Ordered Set (20 min) (reported at foodpanda, 2022, Staff/Principal)

**Story:** *Build a set with `push(value)`, `pop()` (removes and returns the last inserted value
still present), `remove(value)`, `intersect(otherSet)` (returns a new set), `values()` (any order)
and `orderedValues()` (insertion order).*

**Acceptance Criteria:**
- `pop()` after pushing `a, b, c` returns `c`, then `b`
- A duplicate push is a no-op: the value keeps its original position (decision; say it out loud
  and write the test)
- `pop()` skips values that were removed; `pop()` on an empty set throws `NoSuchElementException`
- `intersect` returns a new set and leaves both inputs unchanged
- `values()` and `orderedValues()` return snapshots the caller cannot use to modify the set

**Hints:**
- The interviewer cared about code quality and naming, not performance. Still, a
  `LinkedHashSet` already is "hash table plus doubly linked list", and in Java 21 it has
  `removeLast()`

---

## Task 3: LRU Cache (20 min) (reported at Glovo, 2025)

**Story:** *`LruCache(capacity)` with `get(key)` and `put(key, value)`, both O(1). When full, a
`put` of a new key evicts the least recently used entry (LeetCode 146).*

**Acceptance Criteria:**
- LeetCode example, capacity 2: `put(1,1)`, `put(2,2)`, `get(1)`, `put(3,3)` evicts 2,
  `put(4,4)` evicts 1
- `get` and `put` both count as a use; a miss changes nothing
- `put` on an existing key updates the value and does not evict
- Capacity 0 or less is rejected; the cache never holds more than `capacity` entries
- Build it with a `HashMap` plus your own doubly linked list, not `LinkedHashMap`
- Stretch: 8 threads calling `get` and `put` at once never corrupt the list or exceed capacity

**Hints:**
- Sentinel head and tail nodes remove every null check from the list code
- The node must store its key, or eviction cannot remove it from the map

---

## Task 4: Dinner Plate Stacks (25 min) (reported at Glovo, 2025, SE2)

**Story:** *`DinnerPlates(capacity)`: an unbounded row of stacks, each holding at most `capacity`
plates. `push` goes to the leftmost stack that is not full, `pop` takes from the rightmost stack
that is not empty, `popAtStack(index)` takes from that stack (LeetCode 1172). The reported goal
was a working solution with test cases.*

**Acceptance Criteria:**
- LeetCode example, capacity 2: push 1..5, `popAtStack(0)` is 2, push 20 and 21,
  `popAtStack(0)` is 20, `popAtStack(2)` is 21, then `pop` gives 5, 4, 3, 1, empty
- After `popAtStack` opens gaps at 1 and 3, the next pushes fill 1, then 3, then a new stack
- `pop` skips stacks emptied by `popAtStack`
- `pop`/`popAtStack` with nothing to take return an empty `OptionalInt` (LeetCode uses -1)

**Hints:**
- A `TreeSet<Integer>` (or `PriorityQueue`) of indices of stacks that are not full gives the
  leftmost in O(log n)
- Trim empty stacks off the right end so `pop` always takes from the last stack

---

## Questions to Ask Afterwards

- What is the time and space complexity of each operation you wrote?
- Task 2: can push, pop and remove all be O(1)? (`LinkedHashSet`, or a `HashMap` to nodes of a
  doubly linked list)
- Task 3: in real life, would you build this? (`LinkedHashMap` with `accessOrder = true` and
  `removeEldestEntry`)
- Task 3: how would you make the LRU cache thread-safe? (Every method `synchronized` on one
  lock. A `ReadWriteLock` does not help, because `get` reorders the list and so is a write.
  `Collections.synchronizedMap` over a `LinkedHashMap` works the same way. Striping or
  lock-free designs are where it gets hard.)
- When would you use Caffeine in production instead? (Almost always for an in-process cache:
  concurrent without a global lock, size and time based eviction, near-optimal hit rate from
  W-TinyLFU, stats, async loading)

## Tips for Interviewers

- Ask for the approach and its complexity before any code. A candidate who starts typing a loop
  of additions for Task 1 should be asked what happens with `multiply(1, Integer.MAX_VALUE)`.
- In Task 2, push on the duplicate-push decision: either answer is fine, silence is not.
- In Task 3, watch for the node that does not store its key; the eviction step exposes it.
- For Task 4, a working brute force with tests beats an unfinished `TreeSet` version. Ask for the
  optimisation once it is green.
