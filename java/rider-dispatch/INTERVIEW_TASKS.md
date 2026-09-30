# Interview Tasks — Rider Dispatch

Sequential user stories, one at a time. Budget **45 minutes**. All four stories are
**(predicted)**: modelled on Delivery Hero's logistics/dispatch engineering (Glovo's "Jarvis"
dispatcher re-optimises each city every ~10 s and must never publish two conflicting
assignments), not reported from a real interview.

---

## Task 1: Nearest K Riders (12 min) (predicted)

**Story:** *Riders have an id and a location (lat/lon). Given a restaurant location and K, return
the K closest AVAILABLE riders, closest first.*

**Acceptance Criteria:**
- `Location.distanceKmTo` uses haversine, or the equirectangular approximation; document the
  choice and its trade-off in the code
- `NearestRiders.find(riders, restaurant, k, isAvailable)` returns at most K riders, closest
  first, skipping riders the predicate rejects
- Equal distances are ordered by rider id, so the answer is deterministic
- Fewer available riders than K returns all of them; K = 0 or no riders returns an empty list
- Negative K, latitudes outside [-90, 90] and longitudes outside [-180, 180] are rejected
- O(n log K) time with a bounded max-heap of size K, not a full sort

**Hints:**
- `PriorityQueue` with the reversed comparator keeps the *worst* of the current best K at the
  head: that is the one to evict when a closer rider turns up
- Build the heap on (distance, id) so the tie-break lives in one comparator
- Berlin to Munich is about 504 km; two points either side of the 180th meridian are close

---

## Task 2: Rider Shifts (10 min) (predicted)

**Story:** *Each rider has shift intervals for the day. Merge overlapping and touching intervals,
then answer `isOnShift(riderId, time)`. Only on-shift riders count as available in Task 1.*

**Acceptance Criteria:**
- `ShiftSchedule.merge`: 10:00-13:00 + 12:00-15:00 = 10:00-15:00; touching 10:00-12:00 +
  12:00-14:00 = 10:00-14:00; a shift inside another disappears; unsorted input works; gaps stay
- A shift must start before it ends
- `isOnShift` is start-inclusive and end-exclusive; an unknown rider is never on shift
- `Dispatcher.nearestAvailable(restaurant, k)` returns only riders on shift *now*, read from an
  injected `java.time.Clock`

**Hints:**
- Sort by start, then fold each shift into the last merged one while `next.start <= last.end`
- Store shifts already merged so `isOnShift` never re-merges
- An overnight shift is two shifts (22:00 to midnight, midnight to 02:00)

---

## Task 3: Assignment (10 min) (predicted)

**Story:** *`assign(order)` picks the nearest available on-shift rider and marks them busy;
`complete(orderId)` frees them. A rider never holds two orders.*

**Acceptance Criteria:**
- The nearest available rider gets the order and is then busy
- A busy rider is skipped; when nobody is available the result is an explicit
  `NoRiderAvailable`, not an exception or `null`
- `complete` frees the rider, who can take the next order; completing an unknown or already
  completed order returns `false` and frees nobody
- Assigning the same order id twice is rejected

---

## Task 4: Many Threads (13 min) (predicted)

**Story:** *8 threads assign 1,000 orders against 50 riders concurrently. No rider is ever
assigned two orders at once, and exactly min(orders, riders) assignments succeed while nobody
completes.*

**Acceptance Criteria:**
- 1,000 concurrent orders against 50 riders: exactly 50 `Assigned`, to 50 distinct riders, and
  950 `NoRiderAvailable`
- 30 concurrent orders against 50 riders: all 30 assigned, to 30 distinct riders
- Threads that assign and complete in a loop never see a rider holding two orders
- A real concurrency test: `ExecutorService` plus a `CountDownLatch` start gate

**Hints:**
- One global lock (`synchronized assign/complete`) is correct and a fine place to start. Say so,
  then say what it costs: every city's dispatch is serialised
- The step up is per-rider state: an `AtomicReference<String>` holding the current order id,
  claimed with `compareAndSet(null, orderId)`. A thread that loses the race tries the next
  closest rider
- Never lock two riders at once. If you ever must, take the locks in a fixed order (by rider id)
  to avoid a lock-ordering deadlock
- Check-then-act (`if (!busy) busy = true`) is the bug this story exists to catch

---

## Questions to Ask Afterwards

- How would you find nearby riders among 100k in a city? (A geo index: geohash or H3 cells,
  search the cell and its neighbours; or Redis `GEOSEARCH`. Not a scan of every rider)
- The dispatcher runs on several instances. How do you avoid double assignment? (A per-city lock
  or single owner per city; `SELECT ... FOR UPDATE` or a conditional `UPDATE ... WHERE status =
  'FREE'` on the rider row; fencing tokens so a stale leader's writes are rejected)
- How would you batch two orders onto one rider? (That is vehicle routing, which is NP-hard;
  use heuristics: insertion cost, same-restaurant or nearby pickups, a max detour and time
  window, then re-optimise every few seconds)
- What goes into an ETA? (Rider-to-restaurant travel time by vehicle type, food preparation
  time, wait at the restaurant, restaurant-to-customer travel, handover time at the door,
  traffic and weather, learnt from historical deliveries)
- Design question: *design the rider dispatch service.* (Rider location ingest, a geo index
  per city, order events, an assignment loop per city every few seconds, the rider app accept/
  reject flow with a timeout, idempotent state transitions, and what happens when the
  dispatcher crashes mid-assignment)

## Tips for Interviewers

- Ask the candidate to choose haversine or equirectangular out loud. Either is fine; not knowing
  there is a choice is the signal.
- If the candidate sorts all riders in Task 1, ask for the complexity, then ask for O(n log K).
- In Task 4, a candidate who writes `synchronized` first and passes the test is doing well. Push
  for the per-rider CAS only if there is time, and ask why it cannot deadlock.
- If the concurrency test has no start gate, ask whether the threads actually overlapped.
