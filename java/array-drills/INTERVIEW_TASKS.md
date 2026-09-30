# Interview Tasks — Array Drills

Sequential user stories, one at a time. Budget **50 minutes** for all five (Tasks 1-4 alone
take 35). For a one-hour mock the interviewer picks two or three tasks; Task 5 stands on its own.

For every task: say the approach and its time and space complexity before you type, and ask about
the edge cases (empty input, duplicates, negatives, no answer) before you decide them yourself.

---

## Task 1: Largest, Second Largest, Kth Largest (8 min) (reported at Delivery Hero bar raiser, 2022)

**Story:** *Given an array of ints, return the largest element. Then the second largest. Then the
kth largest.*

**Acceptance Criteria:**
- `largest` and `secondLargest` each make a single pass over the array, O(n) time, O(1) space
- Second largest means the second largest **distinct** value: `[5, 5, 3]` gives `3`
- When there is no second largest (one element, or every element equal), `secondLargest` returns
  `OptionalInt.empty()` rather than a magic value like `Integer.MIN_VALUE` (which is also a
  legitimate element)
- `kthLargest` counts duplicates, like LeetCode 215: `[5, 5, 3]` with k = 2 gives `5`. Note that
  this is deliberately different from `secondLargest`; say which one you are building
- `kthLargest` runs in O(n log k) time and O(k) space with a min-heap of size k
- An empty array is rejected with `IllegalArgumentException`, a null one with `NullPointerException`;
  k outside `1..n` is rejected
- Works with negatives and with `Integer.MIN_VALUE` / `Integer.MAX_VALUE`

**Hints:**
- Second largest: keep `max` and `second`, and a flag (or `OptionalInt`) for "second seen yet"
- Kth largest: a `PriorityQueue<Integer>` is a min-heap by default. Push each element, pop when the
  size exceeds k; the head is the answer

---

## Task 2: Maximum Subarray (8 min) (reported at Delivery Hero, 2021)

**Story:** *Return the largest sum of any non-empty contiguous subarray (LeetCode 53), in O(n) time
and O(1) extra space.*

**Acceptance Criteria:**
- `[-2, 1, -3, 4, -1, 2, 1, -5, 4]` gives `6` (the subarray `[4, -1, 2, 1]`)
- An all-negative array returns its largest element, not `0`: `[-3, -1, -2]` gives `-1`
- An empty array is rejected with `IllegalArgumentException`
- Sums do not overflow: two `Integer.MAX_VALUE` elements sum correctly (return a `long`)
- **Stretch:** `maxSubarray` also returns the start and end indices (inclusive). When several
  subarrays tie, return the one that ends first, and among those the shortest

**Hints:**
- Kadane: at each index, either extend the running subarray or start a new one here, whichever is
  larger. Track the best seen so far
- Starting `best` at `0` is the classic bug that breaks the all-negative case

---

## Task 3: Index Pairs That Sum to K (9 min) (reported at Delivery Hero, 2021)

**Story:** *Return all index pairs `(i, j)` with `i < j` whose values sum to K. The array may
contain duplicates.*

The example as the candidate reported it: `[1, 1, 2, 23, 4, 9, 13, 6, 9]`, K = 10 returns
`[[0, 8], [1, 5]]`.

**That example is ambiguous, and clarifying it is part of the exercise.** Check it yourself:
`1 + 9` sums to 10 at `(0, 5)`, `(0, 8)`, `(1, 5)` and `(1, 8)`, and `4 + 6` at `(4, 7)`. The
reported answer looks like "use each element at most once", but even under that reading `(4, 7)`
is missing, so the example was probably mis-remembered. A strong candidate asks before coding:
*every* pair, or each index at most once? Is the order of the result significant?

**The spec for this exercise (every pair):**
- Return **every** pair `i < j` with `nums[i] + nums[j] == k`, each index free to appear in many
  pairs, sorted by `i` then `j`
- The example above therefore returns `[[0, 5], [0, 8], [1, 5], [1, 8], [4, 7]]`
- No pairs gives an empty list; an empty array gives an empty list; null is rejected
- An element never pairs with itself: `[5]` with K = 10 gives nothing, `[5, 5]` gives `[[0, 1]]`
- `[3, 3, 3]` with K = 6 gives `[[0, 1], [0, 2], [1, 2]]`
- The complement `k - nums[j]` must not overflow: `[Integer.MAX_VALUE, 1]` with
  K = `Integer.MIN_VALUE` gives nothing
- One pass with a `HashMap` from value to the indices seen so far: O(n + p) time where p is the
  number of pairs returned (you cannot beat O(p) when you must return p pairs; with every element
  equal, p is n²/2)
- **Stretch:** `countPairs` returns just the number of pairs in O(n) time, without building them

**Hints:**
- The map stores what you have already seen, so every pair is found exactly once, at its `j`
- Sorting the result costs O(p log p). To get `(i, j)` order for free, scan right to left (the
  map then holds later indices) and reverse the list at the end
- For the count, store a count per value instead of a list of indices

---

## Task 4: Orders in a Time Window (10 min) (reported at Delivery Hero, 2026: binary search; the exact problem was not shared, this is our version)

**Story:** *An `OrderTimeline` holds the placement times (epoch millis) of a restaurant's orders,
sorted ascending. Answer two questions in O(log n): the first order placed at or after time `t`,
and how many orders fall in the window `[from, to)`.*

**Acceptance Criteria:**
- The constructor rejects null and unsorted input, and copies the array (later changes to the
  caller's array do not leak in). Equal timestamps are allowed
- `firstAtOrAfter(t)` returns the earliest timestamp `>= t`, or `OptionalLong.empty()` when every
  order is before `t`
- `countInWindow(from, to)` counts timestamps with `from <= ts < to`: `from` is inclusive, `to` is
  exclusive, so back-to-back windows never count an order twice
- Duplicates: with `[100, 200, 200, 200, 300]`, `firstAtOrAfter(200)` is `200`, the window
  `[200, 201)` holds 3 orders, `[100, 200)` holds 1
- Boundaries: a window entirely before or after the data gives 0, a window covering all of it
  gives n, an empty timeline gives 0 and empty, `from == to` gives 0, `from > to` is rejected
- Works at the extremes of `long` (no `(lo + hi) / 2` overflow)

**Hints:**
- Write one helper, `lowerBound(t)`: the first index whose value is `>= t` (n if none). Both
  methods fall out of it: the window count is `lowerBound(to) - lowerBound(from)`
- Use a half-open search range `[lo, hi)` and `mid = (lo + hi) >>> 1`

---

## Task 5: Final Destination, Then the Streams Rewrite (15 min) (reported at Delivery Hero, 2025: live coding on "hashing map and hashing set"; before 2023: a HackerRank problem "like Destination City")

**Story:** *A rider's route arrives as a list of legs `[from, to]`, in any order. Together they
form one path with no loops. Return the final destination: the city the rider ends up in, which is
the only city with no outgoing leg (LeetCode 1436).*

`[["London", "New York"], ["New York", "Lima"], ["Lima", "Sao Paulo"]]` gives `"Sao Paulo"`.

**Acceptance Criteria:**
- `finalDestination` runs in O(n) time with a `HashSet` of origins, not O(n²) with a nested loop
- Legs may arrive in any order; a single leg returns its `to`
- City names match exactly (case-sensitive)
- LeetCode promises valid input, a service cannot. Decide and test what happens when there is no
  answer or more than one. **The spec for this exercise:** null list, leg or city throws
  `NullPointerException`; an empty list throws `IllegalArgumentException`; so does anything with
  not exactly one city lacking an outgoing leg: zero means a loop (`A->B, B->A`), two or more means
  a fork (`A->B, A->C`) or disjoint paths (`A->B, C->D`)
- Say what the O(n) check does **not** catch: a valid path plus a separate loop
  (`A->B, C->D, D->C`) still has exactly one dead end

**Follow-up 1 (5 min) (reported at Delivery Hero, undated: "a simple one but have to use java
lambda and collections... check if I am comfortable with latest Java features"):**
*Rewrite it as `finalDestinationWithStreams`, with streams and collectors.* Same contract, same
tests: run both versions through one parameterised test.

**Follow-up 2 (5 min) (predicted, in the same spirit):** *Group today's orders by zone.
`ordersPerZone` returns each zone's order count; `busiestZone` returns the zone with the most
orders.*
- `ordersPerZone` iterates in zone-name order; zones with no orders are absent
- `busiestZone` of no orders is `Optional.empty()` (a quiet day is not an error)
- Ties go to the zone name that sorts first. The answer must not depend on `HashMap` iteration
  order: test it with many zones tied on one order each

**Hints:**
- Two passes: collect every `from` into a set, then the `to` that is not in it is the answer.
  Collect the dead ends rather than returning the first one, so you can count them
- Streams: `legs.stream().map(Leg::from).collect(Collectors.toSet())`, then
  `.map(Leg::to).filter(Predicate.not(origins::contains)).distinct().toList()`
- Busiest zone: `groupingBy(Order::zone, counting())`, then stream the entry set and take
  `max(Map.Entry.comparingByValue())`. For the tie-break, `.thenComparing` on the key, reversed,
  because `max` keeps the greatest. `groupingBy(Order::zone, TreeMap::new, counting())` gives a
  sorted map

## Questions to Ask Afterwards

- What is the time and space complexity of each task? Where did you trade space for time?
- Kth largest: min-heap of size k (O(n log k), O(k), works on a stream) vs quickselect (O(n)
  average, O(n²) worst case unless the pivot is randomised or median-of-medians, mutates or copies
  the array) vs sorting (O(n log n)). When would you pick each? What does `k` close to `n` change?
- Task 3 with a stream of values that never ends: what do you keep, and what if memory is bounded?
  (Count only; or bound the window of values you remember)
- Task 3 when the data does not fit in memory: sort externally and walk two pointers inward, or
  hash-partition both sides by value so each value and its complement land in the same partition,
  then solve each partition in memory
- Binary search off-by-one traps: `lo <= hi` vs `lo < hi`, `hi = mid` vs `hi = mid - 1`, inclusive
  vs exclusive bounds, `(lo + hi) / 2` overflow, and returning "not found" vs an insertion point.
  How did your tests catch each one?
- Task 5, streams vs loops: which version is easier to read, and for whom? Where do streams get
  worse (checked exceptions, early exit, index access, a debugger stepping through lambdas, a
  pipeline nobody can name)? On performance: both are O(n); the stream version has some per-element
  overhead (lambdas, boxing with `counting()`) that rarely matters next to I/O, so measure with JMH
  before choosing loops for speed. When would `parallelStream()` help here? (Almost never at this
  size, and never with a shared mutable collector)
- Task 5: how would you fully validate that the legs form one simple path, still in O(n)? (Every
  city at most one leg out and one in, exactly one start, and a walk from the start covers all n
  legs.) And if legs arrive as a stream of events, one rider at a time?
- `groupingBy` returns a `HashMap`. Why did `busiestZone` need an explicit tie-break, and what
  would a test that ran only one tie in a small map have missed?

## Tips for Interviewers

- Task 3 is the one to watch. Do not volunteer the ambiguity: the signal is whether the candidate
  checks the example against the input and asks before coding. If they build the "each index once"
  version after asking, that is a fine answer too; ask them to state the spec and make the tests
  match it
- If `secondLargest` returns `Integer.MIN_VALUE` for "none", ask what it returns for
  `[Integer.MIN_VALUE, 5]`
- If Kadane starts `best` at `0`, hand them `[-3, -1, -2]`
- For Task 4, ask for the duplicate test before they write the search. Most off-by-one bugs only
  show up with a run of equal timestamps at a window edge
- Task 5 is easy on purpose. The signal is whether the candidate asks what happens with bad input
  (a loop, two dead ends, no legs) instead of trusting the LeetCode guarantee, and whether the
  stream rewrite reuses the same tests. If they write a nested loop, ask for the complexity with
  100,000 legs
- In the streams follow-up, listen for why, not just how: a candidate who can say when they would
  keep the loop is stronger than one who streams everything. If `busiestZone` uses
  `max(comparingByValue())` alone, give them two zones tied and ask whether the answer is stable
