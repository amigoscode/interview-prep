# Data Structure Drills — Live Coding Practice

Four short data-structure problems reported by candidates at Delivery Hero brands (talabat,
foodpanda, Glovo), which run the same style of round: one easy/medium problem, talk first, then
code and test it live. They test whether you can pick the right structure, justify its complexity
out loud, and prove it works with tests, not just type a memorised answer. The interviewer picks
**2 of the 4 tasks**. Time box: **45 minutes**.


## Tech Stack

- **Java 21**, **JUnit 5, AssertJ 3**, Maven wrapper. No Spring, no database.

## Getting Started

- JDK 21+
- `./mvnw test`. Each task has one test marked `@Disabled("story N: remove this line to begin")`;
  remove the line for the task you were given.

## How the Interview Runs

The interviewer picks two tasks. Open [`INTERVIEW_TASKS.md`](INTERVIEW_TASKS.md), cover
everything except the current task, start a timer. Say the approach and its complexity before
you write code, then drive it with tests.

## Solution

`git checkout solutions/data-structure-drills` then `./mvnw test`.

Reset to the skeleton: `git checkout main -- java/data-structure-drills`.
