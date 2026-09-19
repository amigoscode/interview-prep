# Ledger over HTTP — Live Coding Practice

The classic fintech take-home: *"a money transfer app with a REST API, in memory, no Spring, must
handle concurrency"*. This project uses **Javalin**, a small Spring-free HTTP library, so nothing
hides the code you wrote. It is also the natural place to show **Mockito**:
the HTTP handlers are tested against a mocked `Ledger`.

**Do `ledger` first.** This project ships the same `Ledger` skeleton in
`src/main/java/com/amigoscode/ledger`; copy your finished ledger over it (or check out
`solutions/ledger` and copy from there). With the skeleton in place every endpoint
returns 500. Time box: **one evening**, optional.


## Tech Stack

- **Java 21**
- **Javalin 6** with Jackson for JSON
- **JUnit 5, Mockito 5, AssertJ 3**, Maven wrapper
- No Spring, no database

## Getting Started

- JDK 21+
- `./mvnw test`. `appStartsAndStops` is green; the first real test is
  `@Disabled("story 1: remove this line to begin")`.
- Run it: `LedgerApp.main` from the IDE, then `curl -s localhost:7070/accounts/A`

## Solution

`git checkout solutions/ledger-http` then `./mvnw test`. The branch includes the finished
ledger it depends on.

Reset to the skeleton: `git checkout main -- java/ledger-http`.
