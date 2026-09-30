# Rider Dispatch — Live Coding Practice

The core of a food-delivery logistics platform in miniature: find the nearest riders to a
restaurant, respect their shifts, assign each order to exactly one rider, and stay correct when
many threads dispatch at once. It tests a classic algorithm (top-K with a bounded heap, merge
intervals), a small stateful domain, and correctness under concurrency. Every story is
**(predicted)**, not reported: it is modelled on Delivery Hero's logistics and dispatch
engineering, where Glovo's "Jarvis" dispatcher re-optimises each city every ~10 seconds and must
never publish two conflicting assignments. Time box: **45 minutes**.


## Tech Stack

- **Java 21**, **JUnit 5, AssertJ 3**, Maven wrapper. No Spring, no database.

## Getting Started

- JDK 21+
- `./mvnw test`. The first real test is `@Disabled("story 1: remove this line to begin")`.

## How the Interview Runs

One user story at a time. Open [`INTERVIEW_TASKS.md`](INTERVIEW_TASKS.md), cover everything
below the current task, start a timer. Talk through the approach and its complexity first, then
write the code, then the tests.

## Solution

`git checkout solutions/rider-dispatch` then `./mvnw test`.

Reset to the skeleton: `git checkout main -- java/rider-dispatch`.
