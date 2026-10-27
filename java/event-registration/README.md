# Event Registration — Live Coding Practice

Build the core of an event booking system: validate participants against event requirements,
enforce capacity, manage a first-in-first-out waitlist, and promote the next participant when a
place becomes available. It tests state transitions, validation, ordering, and careful handling of
duplicates. Time box: **35 minutes**.

## Tech Stack

- **Java 21**, **JUnit 5, AssertJ 3**, Maven wrapper. No Spring, no database.

## Getting Started

- JDK 21+
- `./mvnw test`. The first real test is `@Disabled("story 1: remove this line to begin")`.

## How the Interview Runs

One user story at a time. Open [`INTERVIEW_TASKS.md`](INTERVIEW_TASKS.md), cover everything
below the current task, start a timer. Talk through the data structures and return values first,
then write the code and tests.

Reset to the skeleton: `git checkout main -- java/event-registration`.
