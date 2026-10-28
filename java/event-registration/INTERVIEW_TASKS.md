# Interview Tasks — Event Registration

Sequential user stories, one at a time. Budget **35 minutes**.

The public API is:

```java
boolean addEvent(String eventId, String eventType, int capacity, String requirements)
int registerParticipant(String eventId, String participantId, String email, String skillLevel)
boolean cancelRegistration(String eventId, String participantId)
String[] findRegisteredEvents(String participantId)
```

`registerParticipant` returns `0` for a successful registration, a positive one-based waitlist
position, or `-1` for failure. `findRegisteredEvents` includes only confirmed registrations, never
waitlisted entries.

---

## Task 1: Events and Valid Registration (10 min)

**Story:** *Create events with capacity and requirements, then register eligible participants
until the event reaches capacity.*

**Acceptance Criteria:**
- `addEvent` returns `true` for a new valid event and `false` for a duplicate event id or invalid
  parameters; a rejected duplicate does not replace the original event
- Event id and type must be non-blank, capacity must be positive, and requirements must be valid
- With no requirements (`""`), any participant with a well-formed email and a supported skill
  level can register while capacity remains
- `"business_email"` requires a well-formed email whose domain is not a common personal provider
  such as `gmail.com`, `yahoo.com`, `hotmail.com`, or `outlook.com`
- `"skill_level:BEGINNER"`, `"skill_level:INTERMEDIATE"`, and `"skill_level:ADVANCED"` require
  an exact skill-level match
- Multiple requirements are comma-separated and all must pass, for example
  `"business_email,skill_level:INTERMEDIATE"`
- Unknown events, blank event or participant ids, malformed emails, unsupported skill levels,
  malformed requirements, and unmet requirements return `false` from `addEvent` or `-1` from
  `registerParticipant`, as applicable
- A successful registration returns `0`; registrations beyond capacity are handled in Task 2

**Hints:**
- Parse and validate requirements once when adding the event
- Keep confirmed registrations and waitlisted participants as separate collections

---

## Task 2: Waitlist and Duplicate Prevention (8 min)

**Story:** *When an event is full, eligible participants join its waitlist in arrival order.
A participant can appear only once in an event, whether registered or waitlisted.*

**Acceptance Criteria:**
- The first participant after capacity is reached returns waitlist position `1`, the next `2`,
  and so on
- Waitlisted participants still have to satisfy every event requirement
- A repeated registration attempt by a participant already registered or waitlisted returns `-1`
  and does not change capacity or waitlist positions
- The same participant may register for different events
- Waitlist positions are one-based and reflect arrival order

---

## Task 3: Cancellation and Promotion (9 min)

**Story:** *Cancelling a confirmed registration frees a place and automatically promotes the
oldest waitlisted participant. Cancelling a waitlisted participant removes them from the queue.*

**Acceptance Criteria:**
- `cancelRegistration` returns `true` when it removes a confirmed or waitlisted participant and
  `false` for an unknown event or participant
- After a confirmed cancellation, the first waitlisted participant becomes registered immediately
- Promotion preserves waitlist order and leaves the event at capacity
- With no waitlist, cancellation simply frees one place
- Cancelling a waitlisted participant removes them and closes the gap in the returned positions
- A second cancellation of the same participant returns `false`

**Hints:**
- Store the waitlisted participant's email and skill level so promotion does not need new input
- A `LinkedHashSet` or a queue plus a membership set makes first-in-first-out order explicit

---

## Task 4: Registered Events Lookup (8 min)

**Story:** *Return all events for which a participant is successfully registered, sorted by event
id. Waitlisted events do not count.*

**Acceptance Criteria:**
- Results are sorted in natural lexicographic order by event id
- Confirmed registrations appear, including participants promoted from the waitlist
- Waitlisted-only events and cancelled registrations do not appear
- An unknown participant, blank participant id, or participant with no confirmed registrations
  returns an empty array rather than `null`

---

## Command Input Format

If the interviewer asks for command-line input, each line contains one command with
space-separated parameters:

```text
addEvent <eventId> <eventType> <capacity> <requirements>
registerParticipant <eventId> <participantId> <email> <skillLevel>
cancelRegistration <eventId> <participantId>
findRegisteredEvents <participantId>
```

The four API methods are the required deliverable; do not build the parser unless requested.

---

## Questions to Ask Afterwards

- Which data structures give constant-time duplicate checks while preserving waitlist order?
- What should happen if requirements change after participants have joined the waitlist?
- How would you make registration, cancellation, and promotion safe under concurrent requests?
- How would you preserve the same behaviour with multiple service instances and a database?
- Should promotion be transactional, and how would you notify the promoted participant?

## Tips for Interviewers

- Watch for duplicate checks that inspect confirmed registrations but forget the waitlist.
- A cancellation followed by promotion is one state transition; ask what could go wrong if the
  participant is removed before the replacement is chosen.
- Ask the candidate to trace waitlist positions after both kinds of cancellation.
