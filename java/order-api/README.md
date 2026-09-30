# Order API — Live Coding Practice

Build a small food-ordering REST API in Spring Boot, one story at a time: create and read an order,
then list, update and cancel, then validation with proper error bodies, then idempotent retries and
per-customer rate limiting. Delivery Hero candidates have reported being asked to "implement 2 REST
APIs" and "implement a CRUD API" in their live rounds, followed by production questions about
retries and abusive clients. This exercise covers that whole path: HTTP status codes, a server-side
computed total, Bean Validation, RFC 9457 `ProblemDetail`, an `Idempotency-Key` that survives
concurrent retries, and a `Retry-After` header. Time box: **45 minutes**.

## Tech Stack

- **Java 25**, **Spring Boot 4.0.2** (Web MVC, Bean Validation), Maven wrapper
- **JUnit 5, AssertJ, MockMvc** (`@SpringBootTest` + `@AutoConfigureMockMvc`) plus plain unit tests
- No database: an in-memory `ConcurrentHashMap` store is enough

## Getting Started

- JDK 25+
- `./mvnw spring-boot:run` starts the app on http://localhost:8080. Every endpoint answers
  `501 Not Implemented` until you build it.
- `./mvnw test`. The first real test is `@Disabled("story 1: remove this line to begin")`.

```bash
curl -i -X POST http://localhost:8080/orders \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: 5f1c2a' \
  -d '{"customerId":"c-1","restaurantId":"r-42",
       "items":[{"name":"Margherita","quantity":2,"priceCents":950}]}'
```

The skeleton gives you the application class, the controller with every endpoint mapped, an empty
`OrderService`, the request/response records, a `Clock` bean and an empty `RateLimiter`. Change
any of it.

## How the Interview Runs

One user story at a time. Open [`INTERVIEW_TASKS.md`](INTERVIEW_TASKS.md), cover everything
below the current task, start a timer. Talk through the design first, then write the tests.

## Solution

`git checkout solutions/order-api` then `./mvnw test`.

Reset to the skeleton: `git checkout main -- java/order-api`.
