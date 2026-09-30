# Fix the Service — Live Coding Practice

> **On `main`, `./mvnw test` FAILS, on purpose, with exactly 4 test failures in
> `DeliveryFeeServiceTest`.** That is the exercise, not a broken checkout. Your job in Task 1 is to
> make those 4 go green by fixing the code.

A different shape of live-coding round: you are not writing from a blank file, you are handed a small,
working-but-messy service and asked to understand it, fix it and then say how you would make it
production-ready. This reproduces a Glovo (Delivery Hero brand) round reported in 2025: "a small
service in CoderPad, one file with 4 failing tests. Understand it, fix the tests, then say how to make
it production-ready (SOLID, naming, logging, magic numbers, splitting functions)". The same process
had a bar-raiser recursion question, converting every key of a nested map from snake_case to
camelCase, which is Task 3 here. It tests reading code before changing it, finding root causes rather
than patching tests, money handling, testable time, and how you talk about code quality.
Time box: **35 minutes**.

## Tech Stack

- **Java 21**, **JUnit 5, AssertJ 3**, Maven wrapper. No Spring, no database.

## Getting Started

- JDK 21+
- `./mvnw test`. Expected result on `main`: **`Tests run: 17, Failures: 4, Errors: 0, Skipped: 1`**.
  The 4 failures are in `DeliveryFeeServiceTest` (Task 1). The skipped one is Task 3's first test,
  `@Disabled("task 3: remove this line to begin")`.
- The service is `src/main/java/com/amigoscode/fixit/DeliveryFeeService.java`. Its Javadoc is the
  pricing spec the tests encode.

## How the Interview Runs

One task at a time. Open [`INTERVIEW_TASKS.md`](INTERVIEW_TASKS.md), cover everything below the
current task, start a timer. Read the code and the failing test output out loud before you touch
anything.

## Solution

`git checkout solutions/fix-the-service` then `./mvnw test` (all green).

Reset to the skeleton: `git checkout main -- java/fix-the-service`.

<details>
<summary>Spoiler: the 4 bugs and what the refactor changed</summary>

**The 4 bugs (Task 1)**

1. **Free-delivery threshold off by one.** `if (b > 30)` should be `>= 30`: a basket of exactly
   30.00 paid a fee. Test: `basketOfExactlyThirtyGetsFreeDelivery`.
2. **Truncation instead of rounding, on doubles.** `Math.floor(f * 100) / 100` cuts 2.9665 to 2.96
   instead of rounding half up to 2.97 (and `double` arithmetic can put `x * 100` just under a whole
   number, so floor can drop a cent even with no discount). Test:
   `percentageVoucherRoundsHalfUpToTheCent`.
3. **Voucher applied twice.** `EURO1` subtracts `disc` inside the voucher block and then again in the
   "apply discount" block, so 3.49 became 1.49. Test: `euroOffVoucherTakesOneEuroOff`.
4. **Peak window end is inclusive.** `h >= 12 && h <= 14` makes all of 14:00 to 14:59 peak; the
   dinner window next to it correctly uses `h < 21`. Test: `lunchPeakIsOverAtTwo`.

**The refactor (Task 2)**

- Money is a `Money` record holding `long` cents: exact sums, `percentOff` rounds `HALF_UP` in one
  place, `minusFloorAtZero` so a discount can never become a refund.
- Every magic number is a named field of a `PricingRules` record (`PricingRules.STANDARD`), injected
  into the service, so a new city or a pricing experiment is data, not an edit (open/closed).
- Time comes from an injected `java.time.Clock`; peak hours are evaluated in the clock's zone. No more
  `LocalTime.now()` and no test-only overload.
- Peak hours are `TimeWindow` records with one half-open `contains` ([start, end)) instead of hand-written
  hour comparisons.
- Vouchers are a `sealed interface Voucher` (`FreeDelivery`, `PercentOff`, `AmountOff`) looked up through
  a `VoucherCatalog` interface: a new kind of voucher is a new record, the catalog can become a database
  or promotions service without touching the fee logic (single responsibility, dependency inversion).
- The 100-line method is split into `requireDeliverable`, `qualifiesForFreeDelivery`,
  `extraDistanceFee`, `smallOrderFee`, `peakSurcharge` and `applyVoucher`, each a few lines, each
  named after the rule it implements. `d`, `b`, `v`, `f`, `p` are gone; inputs are a `DeliveryRequest`
  record.
- No swallowed exception: a missing voucher is an `Optional`, not a caught `NullPointerException`; an
  unknown code is logged at INFO and ignored on purpose. Invalid input (distance out of range or NaN,
  negative basket) fails fast with a message that says what was wrong.
- `System.out.println` is replaced by `System.Logger` (SLF4J in a Spring service) at DEBUG, with no
  string building when DEBUG is off.
- Tests: the original 15 scenarios kept with the same inputs and the same expected amounts, adapted
  mechanically to the new API, plus new tests on both sides of every fixed boundary, time zones,
  injected rules, and a `MoneyTest`.

</details>
