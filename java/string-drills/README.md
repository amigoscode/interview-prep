# String Drills — Live Coding Practice

Three short string problems, all reported from Delivery Hero's live-coding round. None needs a
framework; what the interviewer watches is how you pin down an underspecified problem, how you
walk a string with indexes instead of reaching for a library call, and whether you can write
the test cases yourself. Delivery Hero runs this on CoderPad with no autocomplete, so practise
with the IDE's suggestions switched off. Time box: **30 minutes**.

## Tech Stack

- **Java 21**, **JUnit 5, AssertJ 3**, Maven wrapper. No Spring, no database.

## Getting Started

- JDK 21+
- `./mvnw test`. The first real test is `@Disabled("story 1: remove this line to begin")`.

## How the Interview Runs

One user story at a time. Open [`INTERVIEW_TASKS.md`](INTERVIEW_TASKS.md), cover everything
below the current task, start a timer.

## Solution

`git checkout solutions/string-drills` then `./mvnw test`.

Reset to the skeleton: `git checkout main -- java/string-drills`.
