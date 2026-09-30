# Interview Tasks — Order Tracking

Sequential user stories, one at a time. Budget **45 minutes**.

Every event is an `OrderEvent(eventId, orderId, sequence, state, occurredAt, order)`, where
`order` is the header (`restaurantId`, `zoneId`, `placedAt`, `promisedBy`) that every event
carries.

---

## Task 1: Order State Machine (10 min) (predicted, from Delivery Hero's order-state streaming)

**Story:** *Orders move RECEIVED -> ACCEPTED -> PICKED_UP -> DELIVERED, and can be CANCELLED from
RECEIVED or ACCEPTED only. `OrderTracker.apply(OrderEvent)` updates the state; illegal transitions
are rejected. `stateOf(orderId)` returns the current state.*

**Acceptance Criteria:**
- The happy path reaches DELIVERED; an unknown order has no state (`Optional.empty()`)
- CANCELLED works from RECEIVED and from ACCEPTED, and is rejected from PICKED_UP
- Nothing leaves DELIVERED or CANCELLED; no state goes backwards or to itself
- A rejected event changes nothing
- Null event, blank ids, a sequence below 1 and a promise before the placement are rejected

**Hints:**
- Put the rules on the enum (`next()`, `canTransitionTo`) so the table of legal moves is in one
  place
- Exception or result type? The skeleton uses a sealed `ApplyResult` (`Applied`, `Duplicate`,
  `Stale`, `Rejected`), matched with a `switch`. On a stream an illegal transition is ordinary
  data, not a bug: a consumer that throws either dies on it or needs poison-pill handling, while
  a result can be logged, counted or dead-lettered. Malformed input (null, blank id) is still an
  exception, because that is a programming error in the producer. A candidate who argues for
  an exception and handles it at the consumer loop is fine; not having a reason is not

---

## Task 2: Duplicates and Out-of-Order Messages (12 min) (predicted, from Delivery Hero's Reliability Manifesto: "tests must cover duplicated and out-of-order messages")

**Story:** *Events are delivered at-least-once and may arrive out of order. Each event carries an
`eventId` and a per-order `sequence`. Handle all three cases and test each.*

**Acceptance Criteria:**
- The same `eventId` again is ignored and returns `Duplicate`, however many times and however late
  it arrives; state does not change
- A sequence not newer than the current one is ignored and returns `Stale`
- A gap (PICKED_UP, sequence 3, arrives before ACCEPTED, sequence 2): decide buffer or
  accept-the-latest, document it, test it
- A fully shuffled stream with duplicates ends in the order's last state

**Hints:**
- Chosen here: **accept-the-latest**. The producer assigns sequences, so the newest event is the
  truth; a gap is accepted when the new state is *reachable* from the current one (`canReach`,
  not `canTransitionTo`), and the missing event later arrives as `Stale`. Every event carries the
  order header, so even DELIVERED can be the first event seen for an order
- Buffering is the other valid answer (hold sequence N+2 until N+1 arrives), but then ask: how
  big may the buffer grow, and what if N+1 never comes? It needs a bound and a timeout
- Record the `eventId` for every event handled (applied, stale or rejected), so a redelivery
  always answers `Duplicate`
- Equal sequence, different `eventId`: treat as `Stale` and say so

---

## Task 3: Late Orders per Zone (8 min) (reported at Delivery Hero, 2026)

**Story:** *Data is continuously coming in. `lateOrdersPerZone()` returns, for each zone, how many
orders are not DELIVERED or CANCELLED and are past their promised time as of `clock.instant()`.
`lateDeliveriesInLast(Duration)` counts orders DELIVERED late within the last N minutes.*

**Acceptance Criteria:**
- Exactly at the promised time is not late; one millisecond after is
- Delivered and cancelled orders are never late; zones with no late orders are left out
- The window is `(now - window, now]` and uses the delivery event's `occurredAt`, so a consumer
  that is behind still counts the delivery at the time it happened
- A zero, negative or null window is rejected

**Hints:**
- Reuse the `MutableClock extends Clock` with `advance(Duration)` from the rate limiter; never
  `Thread.sleep`
- A scan over the orders is a fine first answer. Ask what it costs with ten million orders, and
  what to evict once an order is terminal
- Event time vs processing time is the question to ask out loud before coding

---

## Task 4: Top Restaurants and Average Delivery Time (8 min) (predicted)

**Story:** *`topRestaurants(k)` returns the k restaurants with the most orders placed in the last
hour, busiest first. `averageDeliveryTime()` is the moving average of placed-to-delivered time over
the last N delivered orders.*

**Acceptance Criteria:**
- Each order counts once for its restaurant, whatever the number of events it has
- Orders placed an hour ago or more drop out; ties are broken by restaurant id
- Average over the last N only; empty when nothing is delivered; a redelivered DELIVERED or a
  cancelled order does not skew it

**Hints:**
- A min-heap on `placedAt` finds what to evict even when events arrive out of order; a map holds
  the counts; a size-k min-heap over the counts gives the top k in O(r log k)
- A ring (`ArrayDeque`) of the last N durations plus a running sum makes the average O(1)
- DELIVERED is terminal, so recording it on `Applied(DELIVERED)` happens at most once per order

---

## Task 5: Many Consumer Threads (7 min) (predicted)

**Story:** *8 consumer threads apply events for many orders at the same time. State stays
consistent and every duplicate is still ignored exactly once.*

**Acceptance Criteria:**
- 2,000 orders, every event delivered twice, shuffled, applied by 8 threads behind a start latch:
  exactly one of each pair takes effect, the other is `Duplicate`
- Every order ends in its last state; every order is counted once in the top restaurants; every
  delivery is counted once in the average
- 8 threads racing the first event of a new order create it once

**Hints:**
- One lock per order, not per tracker: `computeIfAbsent` for the order, then `synchronized` on it.
  Orders never contend with each other, only with themselves
- The duplicate check, the sequence check and the state change must be one atomic step, or two
  threads both pass the check
- The aggregates (restaurant counts, delivery ring) guard themselves

---

## Questions to Ask Afterwards

- How would this run on Kafka? (Partition by `orderId` so one order's events stay in order on one
  partition; a consumer group spreads partitions across instances, and within one consumer you can
  still hand orders to threads by hashing the key)
- Exactly-once vs at-least-once plus an idempotent consumer: which would you choose, and what does
  Kafka's exactly-once actually cover?
- What happens when a consumer restarts? (State lives in memory here: a state store or a database,
  replay from the committed offset, and why idempotency makes replay safe)
- How do you publish the state change to other services without losing it or sending it twice?
  (The outbox pattern: write the state and the outgoing event in one transaction, relay afterwards)
- Design question: the order status tracking system with a live "where is my order" screen at the
  Sunday-dinner peak. (Read model, push to clients with WebSocket or SSE, fan-out, hot zones,
  what happens when the tracker falls behind)

## Tips for Interviewers

- Reward the simplest design that works. A 2026 candidate report describes a rejection for
  over-engineering with Kafka and Protobuf on a problem that needed a map and a lock. Kafka belongs
  in the questions afterwards, not in the code.
- Grade the decisions as much as the code: exception or result type in task 1, buffer or
  accept-the-latest in task 2, event time or processing time in task 3. Each needs a reason and a
  test.
- If the candidate reaches for `Thread.sleep` in task 3, ask how long fifty such tests will take.
  The injected clock is the expected answer.
- In task 5, `synchronized` on `apply` is an acceptable MVP; ask what it costs with 8 threads and
  a million orders. The per-order lock is the refinement.
