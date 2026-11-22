# Backend Concepts

Small Spring Boot projects that each teach one concept interviewers keep asking about. Every project
is **broken first**: you run it, watch the problem happen on your own machine, fix it, then inspect
what your fix changed.

These are for learning a concept step by step. The timed live-coding rounds are in the other folders
under [`java/`](../).

## Projects

| # | Track | Project | You watch this go wrong |
|---|---|---|---|
| 01 | Junior / mid | [@Transactional](junior-mid/01-transactional/) | A transfer fails and the money is gone from the sender |
| 02 | Junior / mid | [Fetch types and N+1](junior-mid/02-fetch-types-n-plus-one/) | One request runs 201 SQL statements |
| 03 | Junior / mid | [Indexes](junior-mid/03-indexes/) | Postgres reads 1,000,000 rows to return 1 |
| 04 | Junior / mid | [SQL traps and slow queries](junior-mid/04-sql-traps-and-slow-queries/) | A report silently drops every customer without a paid order |
| 05 | Junior / mid | [REST and error handling](junior-mid/05-rest-and-error-handling/) | A link checker sends 3 GETs and 3 orders are cancelled |
| 06 | Junior / mid | [Thread safety](junior-mid/06-thread-safety/) | 1,000 requests are answered, the counter says 915 |
| 07 | Junior / mid | [Locking](junior-mid/07-locking/) | Two buyers both get the last item in stock |
| 08 | Junior / mid | [Idempotency](junior-mid/08-idempotency/) | One payment is retried and the customer is charged twice |

The numbers are the suggested order. Each project stands on its own, so you can start anywhere.

More are planned for `junior-mid/`, `senior/` and `messaging/` (Kafka and SQS, step by step).

## How every project is laid out

```
<project>/
├── README.md             what it teaches, how to run it
├── INTERVIEW_TASKS.md    challenge mode: story, acceptance criteria, hints
├── TASKS_GUIDED.md       the same tasks, step by step, with explanations and expected output
├── start/                the broken project. You work here
└── solution/             the same project, fixed
```

`start/` and `solution/` are each a complete project with their own Maven wrapper and
`compose.yaml`.

## The three commands

Run them from inside `start/` (or `solution/`).

| What | macOS / Linux | Windows |
|---|---|---|
| See the problem, or see it gone | `./demo` | `.\demo` |
| Look inside the database while it runs | `./inspect` | `.\inspect` |
| Check a task's acceptance criteria | `./check 2` | `.\check 2` |

## What you need

- **JDK 25**
- **Docker** (Docker Desktop on macOS and Windows, Docker Engine on Linux), running (not needed for Thread safety)

Nothing else. No local Postgres, no `psql`, no global Maven.

## The rhythm of every project

1. **See it break.** Run the demo and read the evidence. No code yet.
2. **Fix it.** The task's acceptance criteria are tests that ship with the project.
3. **Inspect the fix.** Follow the steps and answer the questions: what does the database do now?
4. Repeat for each alternative fix, then compare them.

## Continuous integration

`.github/workflows/concepts.yml` runs on every change under `java/concepts/`. For each project in
[`ci-projects.json`](ci-projects.json) it proves that `start/` fails with exactly the documented
number of passing checks, and that `solution/` passes all of them. When you add a project, or change
how many checks one has, update that file.
