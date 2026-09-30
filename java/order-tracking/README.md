# Order Tracking — Live Coding Practice

A food delivery platform consumes a stream of order events and has to answer "what state is this
order in?", "which zones are running late?" and "which restaurants are busiest?". It tests a small
state machine, the realities of a message stream (duplicates, out-of-order delivery), sliding
windows over time with an injected `Clock`, and correctness under many consumer threads. Delivery
Hero's own engineering writing says tests must cover duplicated and out-of-order messages, and a
2026 bar-raiser round was described as "a simple stream-processing style problem, data
continuously coming in". Time box: **45 minutes**.

## Tech Stack

- **Java 21**, **JUnit 5, Mockito 5, AssertJ 3**, Maven wrapper. No Spring, no database, no Kafka.

## Getting Started

- JDK 21+
- `./mvnw test`. The first real test is `@Disabled("story 1: remove this line to begin")`.

## How the Interview Runs

One user story at a time. Open [`INTERVIEW_TASKS.md`](INTERVIEW_TASKS.md), cover everything
below the current task, start a timer.

## Solution

`git checkout solutions/order-tracking` then `./mvnw test`.

Reset to the skeleton: `git checkout main -- java/order-tracking`.
