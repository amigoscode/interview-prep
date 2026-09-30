# Array Drills — Live Coding Practice

Five short array and collections problems of the kind Delivery Hero puts in its one-hour CoderPad
round: largest, second largest and kth largest; maximum subarray (Kadane); index pairs that sum to
K; a binary search over sorted order timestamps; and the rider's final destination with a
`HashSet`, rewritten with streams and `Collectors`. None is hard on its own. What the round tests
is whether you pin down the spec before typing (duplicates? empty input? what if there is no
answer?), state the complexity out loud, and then prove it with tests. Time box: **50 minutes**
for all five. The real round gives you one problem, so for a one-hour mock the interviewer picks
two or three tasks and leaves time for the questions at the end.


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
