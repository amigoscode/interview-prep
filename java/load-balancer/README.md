# Load Balancer — Live Coding Practice

A classic **first live-coding round** task at fintech companies. Simple on purpose: the marks are
for clean structure, edge cases, tests and how you talk, not for algorithms. Time box: **30 minutes**.

Plain Java, no framework, own IDE, JUnit and Mockito: the usual interview setup.


## Tech Stack

- **Java 21** (language level; any newer JDK works)
- **JUnit 5, Mockito 5, AssertJ 3**
- Maven with the wrapper included
- No Spring, no database

## Getting Started

### Prerequisites

- JDK 21+

### Run Tests

```bash
./mvnw test
```

The starter test is green; the first real test is marked `@Disabled("story 1: remove this line to
begin")`. Removing that line is how you start.

## How the Interview Runs

One user story at a time, in the chat. Finish it with tests, say "done", get the next one. Open
[`INTERVIEW_TASKS.md`](INTERVIEW_TASKS.md), cover everything below the current task, start a timer.

## Solution

`git checkout solutions/load-balancer` then `./mvnw test`.

Reset to the skeleton: `git checkout main -- java/load-balancer`.
