# Ledger — Live Coding Practice

The task fintech Java live-coding interviews are built around: build an in-memory bank, make
transfers safe for many threads, then answer database questions about the same problem. This is the one to repeat until it is boring. Time box: **35 minutes** for the first
three tasks.

Unlike the Spring Boot projects in this repo, nothing is pre-built and there is no
framework: the typical brief is *"set up an empty project in your favourite IDE and a library for
testing (JUnit, Mockito). No Spring."* That is exactly what this folder is.


## Tech Stack

- **Java 21** (language level; any newer JDK works)
- **JUnit 5, Mockito 5, AssertJ 3**
- Maven with the wrapper included
- No Spring, no database, no external services

## Getting Started

### Prerequisites

- JDK 21+

### Run Tests

```bash
./mvnw test
```

The starter test is green; the first real test is marked `@Disabled("story 1: remove this line to
begin")`. Removing that line is how you start.

In IntelliJ: File, Open, pick `pom.xml`, then Ctrl+Shift+F10 on any test class. Turn off Copilot
and any AI assistant: the interview forbids them and you want to rehearse the way you will play.

## How the Interview Runs

The interviewer pastes **one user story at a time** into the chat. You finish it, with tests, say
"done", and only then see the next one. Practise the same way: open
[`INTERVIEW_TASKS.md`](INTERVIEW_TASKS.md), cover everything below the current task, start a timer.

## Rules

- Money is `BigDecimal`. Never `double`.
- Every task ends with green tests that cover its edge cases.
- Clarify before coding. Ask two questions out loud, write your assumptions down.
- Talk continuously, even alone. Silence is marked down.

## Solution

`git checkout solutions/ledger` then `./mvnw test`. Read it after your own attempt. The
interesting parts are `Ledger.transfer` and `LedgerConcurrencyTest`, which was checked both ways:
with caller-order locking it deadlocks and times out, with id-ordered locks it passes in
milliseconds. Do that experiment yourself once so you can describe it.

Reset to the skeleton: `git checkout main -- java/ledger`.
