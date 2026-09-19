# Delivery Food System — Interview Practice (Code Review & Refactoring)

A Spring Boot order processing system designed as a **live coding interview practice repository**. Unlike the Bookstore and Todo API projects (which have CRUD gaps to fill), this project is a **code review and refactoring challenge** — the candidate reviews existing code, identifies code smells, proposes improvements, and implements the most important ones.

The codebase is intentionally messy with **architectural issues, tight coupling, missing patterns, and unused infrastructure** — giving interviewers realistic scenarios to test a candidate's ability to read, critique, and improve existing code.

> Originally sourced from [ensiehrahbar/delivery-food-system](https://github.com/ensiehrahbar/delivery-food-system).

## Tech Stack

- **Java 21**
- **Spring Boot 3.4.1**
- **Spring Data MongoDB**
- **Spring Kafka** (Redpanda)
- **Docker Compose** (MongoDB + Redpanda)
- **Maven**
- **Lombok**

## Getting Started

### Prerequisites

- Java 21+
- Docker & Docker Compose
- Maven 3.9+

### Start Infrastructure

```bash
docker-compose up -d
```

This starts:
- **MongoDB** on port `27017`
- **Redpanda** (Kafka-compatible) on port `9092`
- **Redpanda Console** on port `8090`

### Run the Application

```bash
./mvnw spring-boot:run
```

The app starts on **http://localhost:8080**.

### Run Tests

```bash
./mvnw clean test
```

## Domain

A developer created an order processing system where users can place orders, and the system processes them by applying discounts, generating invoices, and dispatching events. When the order is processed, it is marked as processed. If the order value is above 100 EUR, the application applies a 10% discount.

### Order API
- `GET  /order/hello`  — Health check
- `POST /order`        — Place an order

### Example Endpoints (reference implementations)
- `GET /test/mongo` — Test MongoDB integration
- `GET /test/kafka` — Test Kafka integration

## Project Structure

```
src/main/java/
├── com/foodtakeway/          ← Main code (focus here)
│   ├── DeliveryFoodSystemApplication.java
│   ├── Order.java                ← Entity (many issues)
│   ├── OrderApi.java             ← Controller + nested DTO
│   └── OrderService.java         ← Service (in-memory, blocking, tightly coupled)
└── examples/                     ← Reference implementations (MongoDB, Kafka)
    ├── ExampleApi.java
    ├── ExampleKafka.java
    ├── ExampleMongoDB.java
    └── event/
        └── Event.java
```

## Key Code Smells (for interviewer reference)

| Area | Issue |
|------|-------|
| Persistence | Orders stored in `ArrayList` — MongoDB dependency exists but isn't used |
| Coupling | `placeOrder()` synchronously calls `processOrder()` which blocks for 10 seconds |
| Encapsulation | Fields are package-private, no getters/setters, no `@Id` on Order |
| Architecture | No repository pattern, no layered architecture, business logic in service |
| Validation | No input validation on API or domain level |
| Error handling | No exception handling, no global error handler |
| Logging | Uses `System.out.println` instead of SLF4J |
| Event system | Kafka available but not integrated — events only logged to console |
| ID generation | `Random.nextInt(1000)` — collision risk |
| HTTP responses | Returns 200 for creation (should be 201), generic string responses |
| Code organization | `OrderRequest` DTO nested inside controller file |
| Testing | Only `contextLoads()` — no business logic tests |

## Sample Requests

### Place an Order
```bash
curl -X POST http://localhost:8080/order \
  -H "Content-Type: application/json" \
  -d '{"userEmail": "john@example.com", "amount": 150.0}'
```

**Note:** This request will block for ~10 seconds due to the intentional `Thread.sleep(10000)` in `processOrder()`.

## Interview Tasks

See [`INTERVIEW_TASKS.md`](INTERVIEW_TASKS.md) for the full task list organized by level (Junior / Mid / Senior).

## Tips for Interviewers

- Let candidates **explore the codebase for 10 minutes** before starting
- The `examples/` package shows how MongoDB and Kafka work — candidates can reference it
- Encourage candidates to **talk through what they see** before coding
- If a change would take too long, a **TODO comment** is sufficient
- Focus on **thought process and prioritization** as much as implementation
