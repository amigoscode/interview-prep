# Flight Feasibility API — Interview Practice

A Spring Boot REST API designed as a **live coding interview practice repository**. Unlike the other
projects in this repo, this one is a **build-from-scratch** exercise: the app runs, the HTTP contract
is in place, and everything behind it is empty. The candidate implements the business rules from the
assessment brief below.

The brief is reproduced **exactly as a real company issued it** — including the parts that are
ambiguous. Working out what the rules actually mean, asking about the gaps, and defending the
decisions is a large part of what is being assessed. Do not expect the brief to answer every
question.

## Tech Stack

- **Java 25**
- **Spring Boot 4.0.2**
- **Maven**
- No database, no external services — everything is in memory

## Getting Started

### Prerequisites

- Java 25+
- Maven 3.9+ (the wrapper is included)

### Run the Application

```bash
./mvnw spring-boot:run
```

The app starts on **http://localhost:8080**.

### Run Tests

```bash
./mvnw clean test
```

### Try the Endpoint

```bash
curl -X POST http://localhost:8080/api/flights/feasibility \
  -H 'Content-Type: application/json' \
  -d '{
        "flightNumber": "IB2570",
        "takeOffTime": "10:30",
        "passengers": 180,
        "departureLatitude": 41.3851,
        "departureLongitude": 2.1734,
        "arrivalLatitude": 51.5074,
        "arrivalLongitude": -0.1278
      }'
```

Right now this returns a `500` — the controller throws `UnsupportedOperationException`. Making it
return a real answer is Task 3.

---

## The Assessment

> ### Flight Feasibility Assessment
>
> Build a REST API that determines whether a flight plan is feasible based on a set of business
> rules.
>
> The API receives a flight plan containing:
>
> - Flight number
> - Take-off time
> - Number of passengers
> - Departure latitude and longitude
> - Arrival latitude and longitude
>
> The application must calculate the flight distance using the **Haversine formula** and evaluate the
> flight against the following rules:
>
> **Maximum flight range**
> - Flights with 150 passengers or fewer cannot exceed 12,000 km.
> - Flights with more than 150 passengers cannot exceed 8,000 km.
>
> **Long-distance flights**
> - Flights longer than 9,000 km must take off between 06:00 and 14:00.
>
> **Westbound flights**
>
> Flights travelling west must:
> - take off before 15:00, and
> - not exceed 3,000 km.
>
> The API should return:
> - whether the flight is feasible;
> - a list of feedback messages explaining which rules were violated.
>
> **Additional expectations:**
> - Follow clean code principles.
> - Write unit tests.
> - Keep the solution maintainable and easy to extend.

---

## What You Get

```
src/main/java/com/amigoscode/interview/
├── FlightFeasibilityApplication.java
└── flight/
    ├── FlightPlanController.java    ← POST /api/flights/feasibility, body throws (Task 3)
    ├── FlightPlanRequest.java       ← the request contract, matching the brief's input fields
    └── FeasibilityResponse.java     ← the response contract: feasible + feedback
```

The three `flight/` classes exist so your JSON matches a known shape and the interviewer can call
your endpoint. **Everything else is yours to design and create** — the distance calculation, the
rules, the domain model, validation, error handling and all the tests.

You are free to restructure, rename and repackage anything, including those three classes, as long as
the endpoint keeps working.

## What You Build

- `POST /api/flights/feasibility` returns `{ "feasible": true|false, "feedback": [...] }`
- The Haversine distance calculation (implement it — do not add a geo library)
- The three rule groups from the brief
- Unit tests, including the boundaries
- Whatever structure makes a fourth rule cheap to add

Pick tasks from [`INTERVIEW_TASKS.md`](INTERVIEW_TASKS.md) according to level.

## Notes for Interviewers

The brief is vague in several places on purpose. The strongest signal in this exercise is **whether
the candidate notices, asks, and then states a decision** rather than guessing silently. Worth
probing whichever tasks you pick:

- Is a distance exactly equal to a limit allowed, or not? Same question for each time boundary.
- Which passenger band does exactly 150 fall into?
- What does "travelling west" actually mean, given only two longitudes?
- `takeOffTime` is a time with no date and no timezone — 06:00 where?
- Should one violation stop the evaluation, or should all of them be reported?

None of these have a single correct answer in the brief. A candidate who says "I read it this way,
here's why, and here's the test that pins it" is doing the job. A candidate who cannot say which way
they went has not read their own code.

The fastest way to find out whether the design is genuinely extensible is Task 10 — hand them a new
rule and watch which files they open.
