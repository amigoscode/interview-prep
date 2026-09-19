# Interview Practice Repository

A collection of hands-on coding interview projects organized by language and interview format. Each project is a self-contained codebase with intentional issues, gaps, and areas for improvement — giving interviewers realistic scenarios to test candidates.

## How It Works

1. **Interviewer** picks a project and 1-2 tasks from its `INTERVIEW_TASKS.md`
2. **Candidate** clones the repo, explores the codebase (~10 min)
3. **Live coding** — candidate works through the assigned tasks while explaining their thought process

## Projects

| Language | Format | Project | Stack | Status |
|----------|--------|---------|-------|--------|
| Java | Pair Programming | [Bookstore API](java/pair-programming/bookstore-api/) | Spring Boot 4, Java 25, H2, JPA | Done |
| Java | Pair Programming | [Todo API](java/pair-programming/todo-api/) | Spring Boot 4, Java 25, H2, JPA | Done |
| Java | Pair Programming | [Delivery Food System](java/pair-programming/delivery-food-system/) | Spring Boot 3.4, Java 21, MongoDB, Kafka | Done |
| Java | Pair Programming | [Flight Feasibility API](java/pair-programming/flight-feasibility/) | Spring Boot 4, Java 25, Maven | Done |
| Java | Live Coding | [Revolut: Ledger](java/live-coding/revolut/01-ledger/) | Plain Java 21, JUnit 5, Mockito, no Spring | Done |
| Java | Live Coding | [Revolut: Load Balancer](java/live-coding/revolut/02-load-balancer/) | Plain Java 21, JUnit 5 | Done |
| Java | Live Coding | [Revolut: URL Shortener](java/live-coding/revolut/03-url-shortener/) | Plain Java 21, JUnit 5 | Done |
| Java | Live Coding | [Revolut: Rate Limiter](java/live-coding/revolut/04-rate-limiter/) | Plain Java 21, JUnit 5 | Done |
| Java | Live Coding | [Revolut: SQL Transfer](java/live-coding/revolut/05-sql-transfer/) | Plain Java 21, JDBC, H2 in-memory | Done |
| Java | Live Coding | [Revolut: Ledger over HTTP](java/live-coding/revolut/06-ledger-http/) | Plain Java 21, Javalin, Mockito | Done |

> **Note on formats:** most projects hand the candidate a working codebase with intentional issues to
> find and fix. **Flight Feasibility** is different — it is *build from scratch* against a real,
> deliberately ambiguous assessment brief, with only the HTTP contract provided.
>
> The **Revolut live-coding** set is a third format: six timed exercises rehearsing Revolut's Java
> "Technical Conversation Interview" (own IDE, no Spring, no AI, user stories revealed one at a time,
> then concurrency and database questions). Skeletons live on `main`; each exercise has a complete,
> tested solution on `solutions/revolut/<exercise>`. See [java/live-coding/revolut](java/live-coding/revolut/)
> and the companion PDF in its `docs/` folder.

## Structure

```
interviews/
├── java/
│   ├── pair-programming/
│   │   ├── bookstore-api/
│   │   ├── todo-api/
│   │   ├── delivery-food-system/
│   │   └── flight-feasibility/
│   ├── live-coding/
│   │   └── revolut/              # 01-ledger ... 06-ledger-http, one Maven build
│   ├── take-home/
│   └── system-design/
├── python/
│   ├── pair-programming/
│   └── take-home/
└── typescript/
    ├── pair-programming/
    └── take-home/
```

## Each Project Contains

- **README.md** — setup instructions, endpoints, architecture overview
- **INTERVIEW_TASKS.md** — tasks organized by level (Junior / Mid / Senior) with descriptions, acceptance criteria, hints, and time estimates
- **Working codebase** — with intentional issues baked in for candidates to find and fix

## Running a Project

Each project is self-contained. Navigate to its directory and follow the README:

```bash
cd java/pair-programming/bookstore-api
./mvnw spring-boot:run
```

## Tips for Interviewers

- Let candidates **explore the codebase for 10 minutes** before assigning tasks
- Pick **1-2 tasks** matching the candidate's level
- Encourage candidates to **talk through their approach** before coding
- For senior candidates, focus on **trade-off discussions** as much as implementation
- Each project has a reference implementation candidates can learn from
