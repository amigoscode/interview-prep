# String Drills — Live Coding Practice

Five short string problems, all reported from Delivery Hero's live-coding and bar raiser rounds.
None needs a framework; what the interviewer watches is how you pin down an underspecified problem,
how you walk a string with indexes instead of reaching for a library call, and whether you can write
the test cases yourself. Task 4 (Valid Word Abbreviation, from a 2026 bar raiser) is the same
two-pointer skill as Task 3: parse a digit run as a count and move the pointers past it without
expanding anything. Task 5 ends with a rewrite in streams and lambdas, because the interviewer wanted
to see the candidate is comfortable with modern Java. Delivery Hero runs this on CoderPad with no
autocomplete, so practise with the IDE's suggestions switched off. Time box: **45 minutes** for all
five; in the real round expect one or two of them.

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
