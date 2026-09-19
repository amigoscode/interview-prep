# Interview Tasks — Flight Feasibility API (Build From Scratch)

This is a **build-from-scratch** challenge. The candidate reads the assessment brief in
[`README.md`](README.md), works out what the rules mean, and implements them. The app runs and the
HTTP contract exists; everything behind it is empty.

Pick **1-2 tasks** matching the candidate's level. Let them read the brief and explore the codebase
for **~10 minutes** before starting.

**Sequencing note:** Tasks 1-3 are the foundation — later tasks assume a working distance calculation
and endpoint. For a mid or senior candidate who should not spend the session on arithmetic, hand them
Task 1 as pre-work (or let them stub the distance) and start at Task 5 or 7.

**The brief is deliberately ambiguous.** Boundary wording, the definition of "travelling west", and
the timezone of the take-off time are all unresolved. Do not resolve them for the candidate — the
point is to see whether they notice, ask, and commit to a decision. "I chose X because Y" is a full
pass; guessing silently is not.

---

## Junior Level (15-20 min each)

### Task 1: Calculate the Flight Distance

**Context:** Nothing in the codebase computes distance yet. The brief requires the Haversine formula
over the departure and arrival coordinates.

**What to do:**
1. Create a class responsible for calculating great-circle distance in kilometres
2. Implement the Haversine formula (no geo library — write it yourself)
3. Unit-test it against known distances

**Acceptance Criteria:**
- Barcelona `41.3851, 2.1734` → London `51.5074, -0.1278` is about **1,139 km**
- Barcelona → Sydney `-33.8688, 151.2093` is about **17,180 km**
- The calculation lives in its own class, not in the controller
- At least one test asserts a known distance within a stated tolerance

**Hints:**
- `Math.toRadians()` already exists — no need to write a degrees-to-radians helper
- Use the Earth's mean radius, 6,371 km
- Watch out for integer division and for rounding too early
- Ask them: how accurate is this for a real flight? Is that accuracy good enough here?

---

### Task 2: Implement the Maximum Flight Range Rule

**Context:** The brief defines two passenger bands with different maximum distances. This is the
simplest of the three rules — get the shape right and the others follow.

**What to do:**
1. Implement the maximum flight range rule
2. Decide what happens when a distance is *exactly* equal to a limit, and be ready to justify it
3. Write unit tests covering both passenger bands

**Acceptance Criteria:**
- Both passenger bands from the brief are enforced
- The candidate states their reading of "cannot exceed" and the tests pin it
- Tests cover each band, not just one
- No magic numbers in the middle of a condition — named constants at minimum

**Hints:**
- Re-read the brief's wording for the bands carefully, then say out loud which band a flight with
  *exactly* 150 passengers belongs to. It is the most commonly failed detail in this exercise
- Keep the rule out of the controller

---

### Task 3: Wire Up the Endpoint

**Context:** `FlightPlanController.assess()` throws `UnsupportedOperationException`. The brief asks
for a feasibility verdict plus a list of feedback messages.

**What to do:**
1. Return a real `FeasibilityResponse` from the endpoint
2. Report **every** violated rule, not just the first one
3. Keep the controller thin — parse, delegate, respond

**Acceptance Criteria:**
- A feasible flight returns `200` with `feasible: true` and empty `feedback`
- An infeasible flight returns `200` with `feasible: false` and a message per violation
- A flight breaking two rules produces two messages
- The controller contains no distance maths and no rule logic

**Hints:**
- The brief says "a list of feedback messages" — plural, so do not return early on the first failure
- Discuss: why is an infeasible flight `200` and not `400`? What would `422` mean here?
- The feedback messages are read by an operator who has to fix the plan — include the offending value

---

### Task 4: Replace System.out.println with Proper Logging

**Context:** `FlightPlanController` logs with `System.out.println()`. This is a common code smell.

**What to do:**
1. Add an SLF4J logger to the controller
2. Replace the `System.out.println()` call with an appropriate log level
3. Use parameterised messages (e.g. `log.info("Assessing flight {}", flightNumber)`)

**Acceptance Criteria:**
- No `System.out.println()` anywhere in `src/main`
- Uses `LoggerFactory.getLogger()` (or Lombok's `@Slf4j` if they add Lombok)
- Log level is appropriate — `info` for a business event, not `error`

**Hints:**
- SLF4J comes with Spring Boot — no extra dependency needed
- Ask why string concatenation in a log call is worse than a `{}` placeholder
- Ask what else is worth logging here, and what must never be logged

---

## Mid-Level (20-25 min each)

### Task 5: Implement the Long-Distance Take-Off Window Rule

**Context:** The brief restricts take-off times for long flights only.

**What to do:**
1. Implement the rule
2. Decide exactly which flights the rule applies to, and which times are allowed
3. Test both sides of every boundary the rule has

**Acceptance Criteria:**
- The rule only affects the flights the brief says it affects
- Both ends of the time window are tested, one minute either side
- A flight of exactly the threshold distance is handled according to a stated decision
- The rule is independently testable — no Spring context needed

**Hints:**
- `LocalTime.isBefore()` and `isAfter()` are both strict. The brief says "between 06:00 and 14:00" —
  does that include 06:00 and 14:00? Make them commit to an answer
- Same question for the threshold distance: the brief says "longer than 9,000 km", so what happens at
  exactly 9,000?
- Ask what timezone 06:00 refers to. There is no right answer in the brief — listen for whether they
  notice

---

### Task 6: Implement the Westbound Rule

**Context:** The brief adds two conditions for flights "travelling west". It never defines what
travelling west means.

**What to do:**
1. Decide how to determine that a flight travels west, using only the data available
2. Implement both conditions
3. Make sure the operator can tell **which** of the two conditions failed

**Acceptance Criteria:**
- The direction test is explained and defended, not just written
- Both conditions are enforced, and a flight breaking both says so
- Flights that are not westbound are unaffected by either condition
- Tests cover a westbound flight, a non-westbound flight, and both failure modes

**Hints:**
- All they have is two longitudes. Ask what the simplest test is, then ask when it is wrong
- Prompt if needed: what does their rule say about a flight from Tokyo `139.65` to Los Angeles
  `-118.24`? Which way does that flight actually go? (Task 12 fixes this — for now, noticing is
  enough)
- If one rule class reports one message, a flight that is both too late and too far will hide a
  problem from the operator. Is that acceptable?

---

### Task 7: Make Adding a Rule Cheap

**Context:** The brief says the solution must be "maintainable and easy to extend". If the three
rules are `if` blocks in one method, that requirement is not met.

**What to do:**
1. Introduce an abstraction so each rule is its own unit
2. Let the application discover the rules rather than hard-coding a list
3. Refactor the existing rules onto it, keeping all tests green

**Acceptance Criteria:**
- Adding a fourth rule means **one new class** and no edits to any existing rule or service
- Each rule can be unit-tested without starting Spring
- The service that runs the rules does not name a single concrete rule
- Distance is calculated once per request, not once per rule

**Hints:**
- Spring will inject every bean of a type into a `List<T>` constructor parameter — that is the whole
  trick
- Ask what happens to the order of the feedback messages, and whether they can rely on it
- Ask them to name the trade-off they just accepted. Every design has one
- Push back with "isn't an interface overkill for three rules?" and see if they can defend it — the
  answer is in the brief

---

### Task 8: Add Input Validation

**Context:** The endpoint accepts anything. Send an empty body, a blank flight number, zero
passengers or a latitude of 500 and it will happily try to assess it.

**What to do:**
1. Add validation for the fields described in the brief
2. Return `400` with useful, field-level messages
3. Report **all** the input problems at once, not just the first

**Acceptance Criteria:**
- Flight number is required and not blank; passengers must be positive
- Latitude is within ±90 and longitude within ±180
- An invalid request returns `400` with a message per problem
- A valid-but-infeasible request still returns `200` — validation and feasibility stay separate

**Hints:**
- `spring-boot-starter-validation` is **not** in the `pom.xml` yet — they will need to add it
- `@Valid` on the controller parameter, constraints on the record components
- Handle `MethodArgumentNotValidException` in a `@RestControllerAdvice`
- Ask why the fields in `FlightPlanRequest` are boxed types (`Integer`, `Double`) and what would
  happen if `departureLatitude` were a primitive `double` and the caller omitted it. The answer is
  that `0.0` is a perfectly valid coordinate — a real bug

---

### Task 9: Separate Bad Requests from Infeasible Flights

**Context:** Two different failures are easy to conflate: a request the API cannot understand, and a
flight plan it understands perfectly and rejects.

**What to do:**
1. Make sure a client mistake never surfaces as a `500`
2. Give the two cases distinct, deliberate responses
3. Check what happens today with `"takeOffTime": "25:99"` and with a truncated JSON body

**Acceptance Criteria:**
- Unparseable input returns `400` with a helpful body, not a stack trace
- An infeasible flight returns `200` with `feasible: false`
- No handler swallows genuine server errors silently

**Hints:**
- `ProblemDetail` (RFC 9457) is built into Spring — worth discussing even if they roll their own
- Ask what is wrong with `@ExceptionHandler(Exception.class)`. Answer: it converts real bugs into
  tidy responses with nothing logged, and can shadow Spring's own sensible defaults
- `HttpMessageNotReadableException` is the one to catch for malformed JSON

---

## Senior Level (25-30 min each)

### Task 10: Add a Brand-New Rule, Live

**Context:** This is the real test of Task 7. The interviewer invents a rule on the spot and the
candidate implements it while being watched.

**What to do:** Give them **one** of these, and do not let them see it in advance:
1. *"No take-offs between 23:00 and 05:00."*
2. *"Aircraft with more than 300 passengers may not fly further than 5,000 km."*
3. *"Eastbound flights over 10,000 km must take off before 09:00."*

**Acceptance Criteria:**
- Implemented as one new class with **no edits to existing rules or the service**
- Comes with its own unit tests, including the boundaries
- Done in under ten minutes

**Hints:**
- Watch **which files they open** — that is the actual measurement, not whether it compiles
- Option 1 is the sharpest: the window spans midnight, so the natural
  `isAfter(23:00) && isBefore(05:00)` is *always false*. It needs `||`. A test at 02:00 catches it
  instantly — do they write one?
- If they have to modify the service to register the rule, revisit Task 7 with them

---

### Task 11: Make the Limits Configurable

**Context:** Operations wants to change the 8,000 km limit without a code change. Today every
threshold is a constant in the source.

**What to do:**
1. Move the thresholds into `application.yml`
2. Bind them type-safely and inject them into the rules
3. Keep the current values as defaults so nothing breaks

**Acceptance Criteria:**
- Changing a limit in `application.yml` changes the behaviour with no recompile
- Configuration is bound to a typed object, not read with raw `@Value` strings scattered about
- Existing tests still pass, and the rules can still be tested without Spring
- Invalid configuration is rejected at startup, not on the first request

**Hints:**
- `@ConfigurationProperties` bound to a record, with `@EnableConfigurationProperties` or
  `@ConfigurationPropertiesScan`
- Ask what "without a deploy" really means — this is still a restart. What would runtime changes cost?
- Ask whether the *rules themselves* should be configurable, and where they would stop

---

### Task 12: Define "Travelling West" Correctly

**Context:** The obvious direction test — comparing the two longitudes — is wrong for flights crossing
the antimeridian. Tokyo `139.65` → Los Angeles `-118.24` has a smaller arrival longitude, but the
great-circle route heads **east** across the Pacific.

**What to do:**
1. Implement a correct direction test
2. Keep every existing test green
3. Explain the trade-off between the simple version and the correct one

**Acceptance Criteria:**
- Tokyo → Los Angeles is **not** classified as westbound
- Barcelona → London still is
- Flights on the same meridian are handled deliberately
- Covered by tests that would fail against the naive implementation

**Hints:**
- Normalise the longitude difference into (−180, 180] — add or subtract 360 until it is in range —
  then test the sign
- Or compute the initial great-circle bearing and check whether it points west
- Ask what happens at the poles, where longitude is degenerate
- Ask whether they would have shipped the simple version with a comment, and when that is the right
  call

---

### Task 13: Make the Feedback Machine-Readable

**Context:** A client team says they are string-matching on the feedback messages to decide what to
show the user, and it keeps breaking when wording changes. Another team wants the messages in Spanish.

**What to do:**
1. Give every violation a stable code alongside its human-readable message
2. Discuss what this does to existing clients and how you would roll it out
3. Sketch how the same change enables translation

**Acceptance Criteria:**
- Each violation carries a stable identifier that does not change when wording does
- The response change is versioned or additive — existing clients are considered explicitly
- Ordering of the violations is deterministic
- Translation is possible without touching the rules

**Hints:**
- A `record Violation(String code, String message)` is the obvious shape — then `feedback` becomes a
  list of those
- `MessageSource` plus message keys is how Spring does i18n; the rules should emit keys, not sentences
- This is where non-deterministic rule ordering from Task 7 becomes a client-visible problem — do they
  connect the two?
