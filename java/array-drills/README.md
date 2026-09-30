# Array Drills — Live Coding Practice

Four short array problems of the kind Delivery Hero puts in its one-hour CoderPad round: largest,
second largest and kth largest; maximum subarray (Kadane); index pairs that sum to K; and a binary
search over sorted order timestamps. None is hard on its own. What the round tests is whether you
pin down the spec before typing (duplicates? empty input? what if there is no answer?), state the
complexity out loud, and then prove it with tests. Time box: **35 minutes**.


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

`git checkout solutions/array-drills` then `./mvnw test`.

Reset to the skeleton: `git checkout main -- java/array-drills`.
