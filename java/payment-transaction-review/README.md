# Payment Transaction Review — Interview Practice (Live Code Review)

A small Spring Boot transaction API for a payment gateway, used as a **live code review**: the
candidate imports a runnable project into their own IDE, reviews it out loud as if it were a
colleague's pull request, fixes the worst problem in code, then discusses how to extend it and how it
behaves under load. About 60 minutes: 5 to 10 of introduction, 45 on the task, 10 for questions.

This is the format payments companies use for senior backend roles instead of an algorithm round.
The code is our own and is not any company's interview exercise. It contains far more problems than
anyone can cover in the time, on purpose: the round rewards **ranking**, not a long list.

## Tech Stack

- **Java 25**
- **Spring Boot 3.5.11**
- **Gradle** (the wrapper is included)
- **Spring Security** OAuth2 resource server (JWT)
- **Spring Data JPA**, **Flyway**, **H2** in memory
- **Spring Kafka** (a broker is optional)

## Getting Started

### Prerequisites

- JDK 25. The build pins Java 25 with a toolchain and does not download it
- An IDE with Gradle support (IntelliJ IDEA recommended)
- Docker, only if you want a Kafka broker

### Run the Application

```bash
./gradlew build      # compiles and runs the tests
./gradlew bootRun    # starts the app on http://localhost:8081
```

The database is created by Flyway at start-up with a few rows of test data. Restart the app to reset
it.

### Kafka (optional)

```bash
docker compose up -d
```

Without a broker the app still starts and answers, and each write waits about two seconds for the
broker before it gives up.

### Call the API

[`requests.http`](requests.http) has ready-made requests and tokens (IntelliJ HTTP client, or copy
them into curl). The API expects a JWT bearer token with these claims:

| Claim | Example |
|---|---|
| `sub` | `merchant-a-api` |
| `merchant_id` | `merchant-a` |
| `scope` | `transactions:read transactions:write` |

`python3 scripts/mint-token.py <merchant_id> [scope ...]` makes more tokens.

---

## The Brief

> A colleague has opened a pull request that adds the first version of the transaction API for a
> payment gateway. Merchants call it to create a card transaction, capture it, refund it or void it.
> The service publishes an event to Kafka on every change and listens for settlement messages from
> the acquirer.
>
> You are the reviewer. The team wants to take this to production.
>
> 1. Review the code as you would review a real pull request. Say what you find, most serious first.
> 2. Fix what you are asked to fix.
> 3. Discuss how you would extend it and how it behaves under load.

If you are practising as the candidate, stop here, set a timer for 20 minutes and write your ranked
list before you open [`INTERVIEW_TASKS.md`](INTERVIEW_TASKS.md). That file is the answer key.
