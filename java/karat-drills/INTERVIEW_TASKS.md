# Interview Tasks — Karat Drills

One drill is one Karat coding question. Budget **35 minutes** per drill: about 8 for Part 1, 20 for
Part 2, 7 for Part 3 where there is one. Parts are revealed one at a time: cover everything below the
current part. Each drill names its package under `com.amigoscode.karat`; the test class is in the same
package under `src/test`, and the spec for every Part 2 method is the Javadoc on its stub.

The same four habits decide every drill, so they are listed once:

- **Run first.** Delete the class-level `@Disabled`, run the drill's tests, read the failure out loud:
  expected, actual, which rule in the Javadoc it breaks. Then find the cause in the code. Never edit
  a test.
- **Say the edge cases before coding Part 2.** Unknown id (ignore, return false, return null, or
  throw: the spec says which), empty input, a group with nothing in it (omit it or report 0: the spec
  says which), ties (the spec gives the rule), and division by zero.
- **One method per rule.** A private `findX(id)` returning `Optional` kills most null bugs; a map
  built with `computeIfAbsent` or `merge` kills most "missing key" bugs.
- **Finish.** A clean Part 2 beats a clever Part 2 that is half done when time is called.

---

## Drill 1: Access Control (10 min) (`access`, reported; Part 2 predicted)

**Story:** *Physical access to a campus. Users have a level, VISITOR < STAFF < ADMIN, and a user is
authorized for a level when their own level is the same or higher.*

**Part 1:** `AccessManager.isAuthorized` fails the provided test.
- STAFF is authorized for VISITOR areas, ADMIN for everything, VISITOR only for VISITOR
- An unknown user is never authorized

**Part 2 (our prediction):** `getUserIdsAtOrAbove(AccessLevel)` returns the ids sorted;
`getCountByLevel()` returns a map with only the levels that have at least one user.

**Hints:** the enum already carries a rank; compare ranks, not constants. `EnumMap` keeps the
summary in level order.

---

## Drill 2: Gym Membership (35 min) (`gym`, reported three times)

**Story:** *A gym tracks members with a unique id, name and membership status. Staff add members,
update a status and get statistics.*

**Part 1:** `getMembershipStatistics` fails the provided test: 4 paid out of 6 must be 66.67.

**Part 2:** `addWorkout(memberId, workout)` ignores unknown members; `getAverageWorkoutDurations()`
maps each member id to the average workout length in minutes.
- Member 12 with workouts of 10, 55 and 10 minutes averages 25.0
- A workout for member 4, who does not exist, is ignored and 4 is not in the map
- Decide and say what a member with no workouts gets. The provided test does not check; the recorded
  candidate solution crashed on it

**Part 3 (reported variant):** `getTotalMonthlyRevenueCents()`. BRONZE is free, SILVER 29.99,
GOLD 49.99 a month. Four members, one of each paid tier plus a second SILVER, total 10997 cents.

**Hints:** `(a / b) * 100.0` with two ints is 0. Where do workouts live: on the member or in a map
on the manager? Either is fine, say why. Money stays in `long` cents.

---

## Drill 3: Transaction Account (30 min) (`payments`, reported)

**Story:** *A payment monitoring system tracks accounts and transactions. CREDIT increases the
balance, DEBIT decreases it.*

**Part 1:** `getBalance` fails the provided test: 100 - 30 - 20 + 10 is 60, and after a 70 debit it
is -10.

**Part 2:** `getAverageTransactionAmountByAccount()` maps each account id to the average of its
transaction amounts, both types, as absolute values.
- Account 1 with 100, 30, 20, 10 averages 40.0; account 2 with 80, 5.5, 14.5 averages 33.3333
- Account 3 has no transactions and is not in the map

**Hints:** two maps (sum and count) or one `Map<Integer, List<Double>>`, then a second pass. Amounts
are always positive in the input; say what you would do if they were not.

---

## Drill 4: Game Manager (30 min) (`games`, reported)

**Story:** *An online gaming platform records each match from one player's perspective: opponent,
outcome WIN/LOSS/DRAW, score, timestamp.*

**Part 1:** `getPlayerStatistics` fails the provided test: player 1 has 2 wins in 4 matches, win
rate 0.5.

**Part 2:** `addMatchResult` ignores results for unknown players; `getAverageScoreByOutcome(playerId)`
maps each outcome the player has to the average score for it.
- Player 1 with wins of 80 and 90 and a draw of 70 gets {WIN: 85.0, DRAW: 70.0} and no LOSS key
- A player with no results gets an empty map

**Hints:** `EnumMap<Outcome, ...>`. Count and sum in one pass with `merge`.

---

## Drill 5: Clinic Appointments (35 min) (`clinic`, reported)

**Story:** *A clinic tracks doctors and appointments with status SCHEDULED, COMPLETED, CANCELLED or
NO_SHOW, and reports totals, completed count and no-show rate.*

**Part 1:** `getAppointmentStatistics` fails the provided test: 5 appointments, 2 completed, 1 no-show
is a rate of 0.2.

**Part 2:** `addPatientVisit(visit)` ignores visits whose doctor is unknown;
`getAverageVisitDurationByType(doctorId)` maps each visit type the doctor has to the average
duration.
- Doctor 1 with consultations of 30 and 50 and a follow-up of 20 gets {CONSULTATION: 40.0, FOLLOWUP: 20.0}
  and no EMERGENCY key
- A doctor with no visits gets an empty map

**Hints:** the rate is `noShows / (double) total`, guarded for zero appointments. The visit list is a
new field: initialise it, the recorded candidate's `doctors` map was null until the constructor ran.

---

## Drill 6: Equipment Reservations (35 min) (`equipment`, reported twice)

**Story:** *A sports facility reserves equipment for members over a time window in minutes from the
start of the day. Two reservations conflict when they overlap on the same equipment.*

**Part 1:** two provided tests fail.
- A cancelled reservation must free its slot for a new booking
- `cancelReservation` returns true when it cancelled something and false when the id is unknown; the
  cancelled reservation stays in the list with status CANCELLED and every other field unchanged

**Part 2:** `getActiveReservationIds()` returns the ACTIVE ids sorted ascending, empty list if none;
`getTotalMinutesByStatus()` returns a map with the keys "ACTIVE" and "CANCELLED" always present,
each the total duration of the reservations in that status, 0 when there are none.

**Hints:** the overlap test `start < other.end && end > other.start` is right; the status check in
front of it is missing. Seed the status map with both keys before the loop.

---

## Drill 7: Stock Prices (30 min) (`stocks`, reported)

**Story:** *Stock trading data: a `StockCollection` holds price records (int price, date
"YYYY-MM-DD") for one stock and answers max, min and average.*

**Part 1:** the provided test fails on an empty collection: max and min are -1 and the average is
-1.0, not an exception.

**Part 2:** `getBiggestChange()` returns the largest change in price between two consecutive days as
`PriceChange(change, startDate, endDate)`, or null with fewer than 2 records.
- Prices 90 (06-25), 110 (06-29), 112 (07-01), 105 (07-06) give +20 from 06-25 to 06-29
- Prices 210 (12-30), 180 (01-01), 190 (01-03), 200 (01-04) give -30 from 12-30 to 01-01
- "Consecutive" means consecutive after sorting by date, not in insertion order; "largest" is by
  absolute value and keeps the sign; the first of equal changes wins

**Hints:** `IntStream.max()` returns `OptionalInt`; `orElse(-1)`. ISO dates sort correctly as
strings. Sort a copy, never the field.

---

## Drill 8: Music Library (35 min) (`music`, reported)

**Story:** *A streaming platform tracks songs and play events. A play is completed when
listenedSeconds is at least the song's duration, otherwise skipped.*

**Part 1:** `getListenerStats` fails the provided test: 4 plays, 3 unique songs, completion rate 0.5.

**Part 2:** three methods.
- `getMostPlayedSong()`: every play counts, completed or skipped; ties go to the smallest songId;
  null with no plays
- `getAverageListenTime(userId)`: average listenedSeconds over the user's plays; 0.0 with none
- `getMostActiveListener()`: the userId with most plays; ties go to the smaller userId; -1 with none

**Hints:** total plays and unique songs are over all of the user's plays; only the rate uses the
completed subset. For the tie rules, iterate a `TreeMap` or compare `(count desc, id asc)` explicitly.

---

## Drill 9: Order Notifications (35 min) (`notifications`, reported twice)

**Story:** *A retail order notification service creates notifications for orders. Delivery updates
for a notification arrive from a provider in batches, and the same update can be reported more than
once.*

**Part 1:** `getOrderNotifications(orderId)` fails the provided test: order 1 owns notifications 1, 3
and 5, returned sorted.

**Part 2:** `addUpdates(notificationId, updates)` stores the (status, timestamp) pairs not already
stored and returns how many were new, 0 and nothing stored for an unknown notification;
`getUpdateCount(notificationId)` returns how many are stored, 0 if none or unknown.
- [(SENT, 5), (DELIVERED, 15)] returns 2; then [(SENT, 5), (FAILED, 30)] returns 1; the count is 3
- A pair repeated inside one batch counts once

**Hints:** `Update` already has `equals`/`hashCode`, which is the hint: a `Set<Update>` per
notification, and `Set.add` tells you whether it was new.

---

## Drill 10: Incident Alerting (35 min) (`alerts`, reported)

**Story:** *An on-call alert router. An alert is open while resolvedAt is null. Severity is ranked
LOW < MEDIUM < HIGH < CRITICAL.*

**Part 1:** `hasOpenAlert(service)` fails the provided test: "payments" has two resolved alerts
before its only open one and must report true.

**Part 2:** `recordAttempt(alertId, attempt)` associates a delivery attempt with the alert and returns
true, or false and ignores it when no alert has that id; `getAttemptCounts()` maps each alert id with
at least one attempt to its count.
- A resolved alert still accepts attempts; the same responder again is a new attempt
- Alert 30, never attempted, is not in the map

**Hints:** keep looping until you have seen every alert of the service. A `Map<Integer,
List<DeliveryAttempt>>` on the router; the count map is one stream over its entries.

---

## Drill 11: License Pool (35 min) (`licenses`, reported)

**Story:** *A floating license pool. A license is AVAILABLE or IN_USE (strings in this codebase),
with an assigned user, a checkout time and a last heartbeat.*

**Part 1:** `isLicenseAvailable` fails the provided tests: AVAILABLE with an assigned user ("U-2" or
"") is not available.

**Part 2:** `checkoutLicense(userId, currentTime)` assigns the available license with the lowest id,
sets IN_USE, the user, checkedOutAt and lastHeartbeatAt to currentTime, and returns its id; returns
null when the user already holds a license, none is available or the pool is empty, and changes
nothing. `returnLicense(userId)` frees the user's license (AVAILABLE, user and both times null) and
returns true, or false when the user holds none.

**Hints:** "lowest id" means sort the candidates, the map is a `HashMap`. Check "already holds one"
before looking for a free license. A returned license can be checked out again immediately.

---

## Drill 12: Compute Pool Reservations (35 min) (`compute`, reported)

**Story:** *Customers reserve capacity units in compute pools. A reservation is ACTIVE, CANCELLED or
EXPIRED; only ACTIVE reservations consume capacity.*

**Part 1:** `getAvailableCapacity(poolId)` fails the provided test: pool of 100 with an active 30, a
cancelled 20 and an expired 20 has 70 available; an unknown pool has 0.

**Part 2:** `addReservation(reservation)` stores it only if the pool exists and capacityUnits is at
most the pool's available capacity, otherwise ignores it; `getReservedCapacityByCustomer()` maps each
customer to the total ACTIVE reserved capacity.
- Four stored, the fourth is id 204; a reservation of 60 that would exceed capacity is dropped
- A customer whose active total is 0 is not in the map, even with an ACTIVE reservation of 0 units

**Hints:** reuse `getAvailableCapacity` inside `addReservation`. Filter out zero totals after summing,
or skip zero-unit reservations before.

---

## Drill 13: Stock Ledger (35 min) (`stockledger`, reported)

**Story:** *Inventory per SKU per warehouse. Updates arrive out of order; `updatedAt` is when the
warehouse recorded the stock, and an update older than the record's current `updatedAt` is stale and
must not replace it. Available stock is onHand minus reserved, floored at 0.*

**Part 1:** `applyInventoryUpdate` fails the provided test: an update stamped 2000 must not overwrite
a record stamped 3000, and an update stamped exactly 4000 on a record stamped 4000 is applied.

**Part 2:** `previewUpdate(update)` returns the change in on-hand quantity the update would cause
(update minus record) without applying it, or null for an unknown SKU and warehouse;
`getAvailableStockBySku()` maps each SKU to its available stock summed over warehouses, and a SKU
with 0 available still appears.

**Hints:** the comparison is `update.updatedAt >= record.updatedAt`; say why equal is applied (same
second, last writer wins). `Integer`, not `int`, for a method that can return null. `merge(sku,
available, Integer::sum)`.

---

## Drill 14: Food Delivery (35 min) (`fooddelivery`, reported, given in C#)

**Story:** *Orders move PLACED, PREPARING, OUT_FOR_DELIVERY, DELIVERED or CANCELED. Active means
not yet delivered or cancelled.*

**Part 1:** `getOrderStatistics` fails the provided test: 5 orders, 3 active, 2 closed.

**Part 2:** `addDelivery(orderId, delivery)` attaches a delivery (an order can have several) and
returns true, or false for an unknown order; `getAverageDeliveryTimeByRestaurant()` averages ALL
deliveries across each restaurant's orders.
- Restaurant 10 with deliveries of 30, 30 and 60 minutes averages 40.0; restaurant 11 with 30 is 30.0

**Part 3:** `getDeliveryFees()` maps each order id to its fee: round the distance UP to whole km;
base fee 2 for the first km plus 1 per extra km; order value 50 or more is free; 30 or more is half
the base; otherwise the base.
- 1.0 km at 25.0 is 2.0; 2.1 km at 35.0 is 2.0; 10 km at 55.0 is 0.0; 1.2 km at 29.0 is 3.0

**Hints:** `Math.ceil` on the distance, once, before the base fee. Do not round the halved fee: 2.0
km at 30.0 is 1.5, the recorded candidate solution returned 2.0.

---

## Drill 15: CRM Data Quality (35 min) (`crm`, reported)

**Story:** *A CRM data quality engine stores customer records and finds, validates and normalises
them.*

**Part 1:** `findByEmail` fails the provided test: matching is case-insensitive and the ids come back
sorted.

**Part 2:** `validateRecord(recordId)` returns the names of the failing fields sorted alphabetically:
"name" non-empty after trim, "email" exactly one "@", "phone" non-empty and only ASCII digits; empty
list when valid; null when the record does not exist. `getCitySummary()` maps each city, lowercased
and trimmed, to its record count; a blank city is counted under "".
- " " / "bobexample.com" / "555-987-6543" fails ["email", "name", "phone"]
- "New York", "new york" and " NEW YORK " are the same city, count 3

**Hints:** `email.chars().filter(c -> c == '@').count() == 1`. `phone.chars().allMatch(c -> c >= '0'
&& c <= '9')`, and remember `allMatch` on an empty string is true, so check non-empty first.

---

## Drill 16: Bank Imported Transactions (35 min) (`bank`, reported)

**Story:** *A bank imports transactions from several sources. DEPOSIT and INTEREST are credits;
WITHDRAWAL, FEE and TRANSFER are debits.*

**Part 1:** `getAccountSummary` fails the provided test: account 101 has credits 820, debits 400, net
420 over 6 transactions.

**Part 2:** a public `balances` map on the system, initially empty; `addTransaction` also updates the
account's running balance (credits add, debits subtract, a new account starts at 0);
`getTypeSummary()` maps each type name present to {"count": Integer, "total_amount": Double}.
- After a 500 deposit, a 150 withdrawal, a 50 fee and 20 interest, account 101 holds 320.00
- A brand-new account whose first transaction is a 40 fee holds -40.00
- Types with no transactions are not in the summary

**Hints:** put the credit/debit decision in one method (`isCredit(type)` or a method on the enum) and
use it from both the summary and the balance. The provided tests use `double`; say that production
money is `long` cents or `BigDecimal`.

---

## Drill 17: Customer Sync Queue (35 min) (`syncqueue`, reported; tests ours)

**Story:** *Customer changes are queued as single-field updates and pushed to an external CRM. An
update is PENDING, PROCESSING, SUCCEEDED, FAILED or CANCELLED; a FAILED update is RETRYABLE or
NON_RETRYABLE. Only PENDING and retryable FAILED updates are eligible for synchronization.*

**Part 1:** `countPendingUpdates` fails the provided test: 2 pending, 1 retryable failure, 1
non-retryable failure, 1 succeeded, 1 cancelled, 1 processing is 3.

**Part 2:** `queueUpdate(customerId, fieldName, newValue, createdAt)` creates and stores a PENDING
update with the next id, 0 attempts and no error type, and returns it; returns null and stores
nothing when the customer is unknown, the field is not one of name, email, phone, company, or the
new value equals the customer's current value. `cancelPendingUpdates(customerId, fieldName)` sets
every PENDING update for that customer and field to CANCELLED and returns how many; other statuses
are untouched; 0 for an unknown customer.

**Hints:** a `switch` on the field name to read the current value. Increment `nextUpdateId` only
after validation passes, or ids get holes.

---

## Drill 18: IoT Device Fleet (30 min) (`devices`, reported; Part 2 predicted)

**Story:** *A fleet of IoT devices with a lifecycle REGISTERED, ACTIVE, SUSPENDED, MAINTENANCE_DUE,
RETIRED and a table of valid transitions.*

**Part 1:** `updateDeviceStatus` fails the provided tests: ACTIVE to REGISTERED, anything from
RETIRED, and ACTIVE to ACTIVE are invalid and must return false without changing the device.

**Part 2 (our prediction):** `registerDevice(device)` stores a device only if its type is supported,
its id is new and its status is REGISTERED; `getDevicesDueForMaintenance(currentDay)` returns the ids,
sorted, of ACTIVE devices whose nextMaintenanceDay is on or before currentDay.

**Hints:** the transition table is already there; look up the current status's allowed set. A status
that is not in the table at all is also invalid.

---

## Drill 19: Theory Snippets (15 min) (`snippets`, reported in the theory half)

**Story:** *Five snippets the interviewer puts on screen in the theory half and asks "is this
correct, what is missing, what pattern is missing?". Here each one has a test.*

- `BooleanCheck.isValid(null)` is false, not a `NullPointerException`
- `Employee.getJoiningDate()` cannot be used to change the employee's date, and neither can the
  `Date` passed to the constructor
- `WordSorter.sortedByLength` returns a new list and leaves the input alone; written with a lambda
  or `Comparator.comparingInt`
- `UserDirectory.rename` keeps the renamed user findable: a `HashSet` key must not be mutated
- `ProductService.getProduct` throws `ProductNotFoundException` with the id in the message, not
  `NoSuchElementException`

**Hints:** literal first (`Boolean.TRUE.equals(value)`); copy in and copy out, or `LocalDate`;
`stream().sorted(...).toList()`; remove, then add the new key; `orElseThrow(() -> ...)`.

---

## Questions to Ask Afterwards

Every theory question from the bank, with answers, is in [`../KARAT.md`](../KARAT.md). The ones
that pair naturally with a drill:

- After `gym`, `clinic`, `games`: why `(a / b) * 100.0` is 0, and what you do about a group with no
  data (omit versus 0, and who decides)
- After `snippets`, `notifications`: why `equals`/`hashCode` must agree, why a `HashSet` key must not
  change, and what `Set.add` returning false tells you
- After `stocks`, `payments`, `bank`: why `double` is wrong for money and what you use instead
- After `licenses`, `devices`, `equipment`: String statuses versus enums, and how you would turn the
  transition table into a sealed interface or an `EnumMap`
- After `compute`, `stockledger`: what happens when two requests check out the last unit at the same
  time (a race on a `HashMap`; `synchronized`, `ConcurrentHashMap`, or a database row lock)
- After any drill: how you would make the manager class testable in a Spring service (constructor
  injection of a repository interface, a `Clock` for timestamps)

## Tips for Interviewers

- The bank is consistent: candidates who passed describe running the test first and naming the bug
  before fixing it. Ask "what does the test expect and what did it get?" before any code is typed.
- Part 2 is marked on the edge cases that are written in the spec: unknown id, empty group, tie
  rule, the always-present keys. Ask the candidate to list them before coding; silence is the signal.
- The recorded candidate attempts fail in the same three places: a null list that was never
  initialised, division by zero for an empty group, and a `getAsInt()` on an empty stream. If the
  candidate's Part 2 passes the provided tests, add a member or account with no data and watch.
- The theory half is rapid: one headline sentence and one example per question, then move on.
  Candidates who lecture run out of coding time. Time the answers at 60 to 90 seconds each.
- Predicted parts (`access` Part 2, `devices` Part 2) are ours; say so if the candidate asks whether
  the real screen had them.
