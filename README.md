# Interview Practice Repository

A collection of hands-on coding interview projects organized by language. Each project is a self-contained codebase with intentional issues, gaps, and areas for improvement — giving interviewers realistic scenarios to test candidates.

## How It Works

1. **Interviewer** picks a project and 1-2 tasks from its `INTERVIEW_TASKS.md`
2. **Candidate** clones the repo, explores the codebase (~10 min)
3. **Live coding** — candidate works through the assigned tasks while explaining their thought process

## Projects

| Language | Project | Stack | Status |
|----------|---------|-------|--------|
| Java | [Bookstore API](java/bookstore-api/) | Spring Boot 4, Java 25, H2, JPA | Done |
| Java | [Todo API](java/todo-api/) | Spring Boot 4, Java 25, H2, JPA | Done |
| Java | [Delivery Food System](java/delivery-food-system/) | Spring Boot 3.4, Java 21, MongoDB, Kafka | Done |
| Java | [Flight Feasibility API](java/flight-feasibility/) | Spring Boot 4, Java 25, Maven | Done |
| Java | [Ledger](java/ledger/) | Plain Java 21, JUnit 5, Mockito, no Spring | Done |
| Java | [Load Balancer](java/load-balancer/) | Plain Java 21, JUnit 5 | Done |
| Java | [URL Shortener](java/url-shortener/) | Plain Java 21, JUnit 5 | Done |
| Java | [Rate Limiter](java/rate-limiter/) | Plain Java 21, JUnit 5 | Done |
| Java | [SQL Transfer](java/sql-transfer/) | Plain Java 21, JDBC, H2 in-memory | Done |
| Java | [Ledger over HTTP](java/ledger-http/) | Plain Java 21, Javalin, Mockito | Done |
| Java | [String Drills](java/string-drills/) | Plain Java 21, JUnit 5 | Done |
| Java | [Array Drills](java/array-drills/) | Plain Java 21, JUnit 5 | Done |
| Java | [Data Structure Drills](java/data-structure-drills/) | Plain Java 21, JUnit 5 | Done |
| Java | [Order Tracking](java/order-tracking/) | Plain Java 21, JUnit 5 | Done |
| Java | [Order API](java/order-api/) | Spring Boot 4, Java 25 | Done |
| Java | [Rider Dispatch](java/rider-dispatch/) | Plain Java 21, JUnit 5 | Done |
| Java | [Fix the Service](java/fix-the-service/) | Plain Java 21, JUnit 5 | Done |

> **Note on formats:** most projects hand the candidate a working codebase with intentional issues to
> find and fix. **Flight Feasibility** is different — it is *build from scratch* against a real,
> deliberately ambiguous assessment brief, with only the HTTP contract provided.
>
> **Ledger, Load Balancer, URL Shortener, Rate Limiter, SQL Transfer and Ledger over HTTP** are a
> third format: timed live-coding exercises in the style of fintech backend interviews (own IDE, no
> Spring, no AI, user stories revealed one at a time, then concurrency and database questions). Each
> is a standalone plain-Java project with a compiling skeleton on `main` and a complete, tested
> solution on `solutions/<project>`, for example `solutions/ledger`.

> **Delivery Hero prep:** String Drills, Array Drills, Data Structure Drills, Order Tracking, Order API,
> Rider Dispatch and Fix the Service practise the questions reported in Delivery Hero's technical round (and
> at its brands Glovo, talabat and foodpanda). They use the same live-coding format, with solutions on
> `solutions/<project>`. **Fix the Service** starts with 4 failing tests on purpose. See
> [java/DELIVERY_HERO.md](java/DELIVERY_HERO.md) for every question mapped to its exercise.

## Structure

```
interview-prep/
├── java/
│   ├── bookstore-api/
│   ├── todo-api/
│   ├── delivery-food-system/
│   ├── flight-feasibility/
│   ├── ledger/
│   ├── load-balancer/
│   ├── url-shortener/
│   ├── rate-limiter/
│   ├── sql-transfer/
│   ├── ledger-http/
│   ├── string-drills/
│   ├── array-drills/
│   ├── data-structure-drills/
│   ├── order-tracking/
│   ├── order-api/
│   ├── rider-dispatch/
│   ├── fix-the-service/
│   └── DELIVERY_HERO.md
├── python/
└── typescript/
```

## Each Project Contains

- **README.md** — setup instructions, endpoints, architecture overview
- **INTERVIEW_TASKS.md** — tasks organized by level (Junior / Mid / Senior) with descriptions, acceptance criteria, hints, and time estimates
- **Working codebase** — with intentional issues baked in for candidates to find and fix

## Running a Project

Each project is self-contained. Navigate to its directory and follow the README:

```bash
cd java/bookstore-api
./mvnw spring-boot:run
```

## Tips for Interviewers

- Let candidates **explore the codebase for 10 minutes** before assigning tasks
- Pick **1-2 tasks** matching the candidate's level
- Encourage candidates to **talk through their approach** before coding
- For senior candidates, focus on **trade-off discussions** as much as implementation
- Each project has a reference implementation candidates can learn from
