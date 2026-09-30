# Interview Tasks — Fix the Service

> **On `main`, `./mvnw test` FAILS, on purpose, with exactly 4 failures in `DeliveryFeeServiceTest`.**
> That is the starting point of Task 1, not a broken project.

Sequential user stories, one at a time. Budget **35 minutes**.

---

## Task 1: Make the 4 Failing Tests Pass (12 min) (reported at Glovo, 2025)

**Story:** *"A small service in CoderPad, one file with 4 failing tests. Understand it, fix the
tests." `DeliveryFeeService` is live and customers are being charged the wrong delivery fee. Read it
before changing it, then make the 4 failing tests pass by fixing the cause, not the test.*

**Acceptance Criteria:**
- Run the suite first and read all 4 failures out loud: expected vs actual, and which pricing rule
  (in the class Javadoc) each one breaks
- Each fix is the smallest change to the code that makes its test pass; no test is edited, deleted or
  `@Disabled`
- The 11 tests that already passed still pass
- Explain each bug in one sentence before you fix it

**Hints:**
- `2.97` expected but `2.96` actual: which way does `Math.floor` round?
- One of the two peak windows is written differently from the other
- Follow the variable `disc` from where it is set to every place it is used

---

## Task 2: Make It Production-Ready (10 min) (reported at Glovo, 2025)

**Story:** *"Then say how to make it production-ready (SOLID, naming, logging, magic numbers,
splitting functions)." Talk through what you would change and why, then refactor as much as the time
allows with every test staying green.*

**Acceptance Criteria:**
- Magic numbers become named constants or an injected rules/config object
- The long method is split into small methods named after the pricing rules
- Money is held in cents (`long`) or `BigDecimal`, never `double`, with one explicit rounding rule
- The time comes from an injected `java.time.Clock`; no `LocalTime.now()`, no test-only overload
- Meaningful names instead of `d`, `b`, `v`, `f`, `p`
- `System.out.println` is replaced by a logger; the empty `catch (Exception e)` is gone and a null
  voucher is handled explicitly
- All tests stay green. If you change the public API, change the tests mechanically: same scenarios,
  same expected amounts

**Hints:**
- Refactor in small steps and run the tests after each one; the tests are your safety net
- Vouchers are a closed set of kinds (free, percent off, amount off): a sealed interface of records
  fits, and a lookup interface keeps the catalog replaceable

---

## Task 3: Snake Case to Camel Case, Recursively (13 min) (reported at Glovo, 2025)

**Story:** *Bar-raiser question: "Given a map of string->object where the object can be a map or
string, convert all keys from snake_case to camelCase." Implement
`KeyCaseConverter.toCamelCase(Map<String, Object>)`.*

**Acceptance Criteria:**
- Keys at every level of nesting are converted (`order_id` becomes `orderId`); values are never
  changed, only keys
- The input map, and every nested map, is not mutated
- Leading, trailing and double underscores are ignored (`_order__id_` becomes `orderId`)
- A key that is already camelCase is unchanged
- Two keys that convert to the same name (`order_id` and `orderId`) throw `IllegalArgumentException`
  naming both, instead of silently losing one value
- Stretch: values that are `List`s have every map inside them converted too

**Hints:**
- One method for a key, one for a map, one for a value that dispatches on its type (a pattern-matching
  `switch` works well) and recurses
- Decide and say what happens to a key made only of underscores, and to null values

---

## Questions to Ask Afterwards

- You are dropped into an unfamiliar codebase. How do you approach it? (Run the tests, read the spec
  and the tests before the code, find the entry points, make a small safe change first, ask about the
  history: git blame, tickets)
- What would you add before deploying this change? (Metrics: fees quoted, voucher usage, unknown-code
  rate, fee distribution per city; alerts on a sudden change in average fee or free-delivery rate; a
  feature flag or percentage rollout so the new pricing can be turned off without a deploy; a
  comparison of old vs new fee in shadow mode)
- Where is SOLID in this code, before and after? (One class doing validation, pricing, vouchers,
  logging and time; after: rules as data, vouchers behind an interface, dependencies injected)
- Why is `double` wrong for money? (Binary floating point cannot represent 0.1 exactly, so sums drift
  and `floor`/cast can lose a cent; use cents in a `long` or `BigDecimal` with an explicit
  `RoundingMode`)

## Tips for Interviewers

- The best candidates run the tests and read the Javadoc before scrolling the method. Watch for anyone
  who "fixes" a failing test by changing its expected value: ask them which rule in the spec says so.
- Bug 2 is the one that separates levels: `Math.round(f * 100) / 100.0` makes the test pass; a strong
  candidate says it still keeps money in a `double` and brings it up again in Task 2.
- Task 2 is mostly conversation in 10 minutes. Credit a clear, ordered plan ("first Clock, then extract
  constants, then money type") as much as code.
- In Task 3 ask what happens on a key collision before they code. Silently overwriting is the answer
  to push back on.
