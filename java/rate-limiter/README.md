# Rate Limiter — Live Coding Practice

A favourite live-coding task across the industry. It tests the same two things as the
ledger, a small stateful domain and correctness under many threads, plus one new skill: making
time-dependent code testable by injecting a `Clock`. Time box: **30 minutes**.


## Tech Stack

- **Java 21**, **JUnit 5, Mockito 5, AssertJ 3**, Maven wrapper. No Spring, no database.

## Getting Started

- JDK 21+
- `./mvnw test`. The first real test is `@Disabled("story 1: remove this line to begin")`.

## How the Interview Runs

One user story at a time. Open [`INTERVIEW_TASKS.md`](INTERVIEW_TASKS.md), cover everything
below the current task, start a timer.

## Solution

`git checkout solutions/rate-limiter` then `./mvnw test`.

Reset to the skeleton: `git checkout main -- java/rate-limiter`.
