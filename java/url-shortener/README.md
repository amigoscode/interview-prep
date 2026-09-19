# URL Shortener — Live Coding Practice

The other common first-round task. Same shape as the load balancer: a small
domain, a strategy to plug in, and a concurrency story at the end where the whole answer is
`computeIfAbsent`. Time box: **30 minutes**.


## Tech Stack

- **Java 21**, **JUnit 5, Mockito 5, AssertJ 3**, Maven wrapper. No Spring, no database.

## Getting Started

- JDK 21+
- `./mvnw test`. The first real test is `@Disabled("story 1: remove this line to begin")`.

## How the Interview Runs

One user story at a time. Open [`INTERVIEW_TASKS.md`](INTERVIEW_TASKS.md), cover everything
below the current task, start a timer.

## Solution

`git checkout solutions/url-shortener` then `./mvnw test`.

Reset to the skeleton: `git checkout main -- java/url-shortener`.
