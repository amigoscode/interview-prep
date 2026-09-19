# Revolut Java live coding: six timed projects

Six hands-on projects that rehearse the Revolut Java technical interview (the "Technical
Conversation Interview": live coding in your own IDE, then concurrency and database
questions). Each folder is one question, written the way the interviewer gives it: as user
stories revealed one at a time, with a strict time box.

The companion PDF (`docs/revolut-technical-interview-prep.pdf` in this folder) explains the
interview, the trap, the questions and the model answers. This folder is where you practise.

## Zero-setup start

Requirements: **JDK 21 or newer** on your PATH. Nothing else: the Maven wrapper downloads
Maven, H2 is an in-memory database that lives inside the tests, and there is no Spring anywhere.

```bash
git clone https://github.com/amigoscode/interview-prep.git
cd interview-prep/java/live-coding/revolut
./mvnw test                     # builds every module, runs the starter tests (all green)
./mvnw -pl 01-ledger test       # one module
```

Every `./mvnw` command in these READMEs is run from `java/live-coding/revolut/`.

Windows: `mvnw.cmd` instead of `./mvnw`. In IntelliJ: File, Open, pick the root `pom.xml`,
then run tests with Ctrl+Shift+F10 on any test class. Turn off Copilot and any AI assistant:
the interview forbids them and you want to practise the way you will play.

## The projects, in the order to do them

| Folder | What it rehearses | Time box | Do it |
|---|---|---|---|
| `01-ledger` | The interview task: accounts, transfer, then thread-safe transfer with lock ordering | 35 min | 4 to 5 times |
| `02-load-balancer` | The first-round task: register/unregister/get, strategy, thread safety | 30 min | twice |
| `03-url-shortener` | The other first-round task; `computeIfAbsent` and idempotency | 30 min | once |
| `04-rate-limiter` | Token bucket with an injected `Clock`; time-dependent code under test | 30 min | once |
| `05-sql-transfer` | The same transfer in SQL on H2: reproduce the lost update, fix it with `FOR UPDATE`, then optimistic locking | one evening | once, take notes |
| `06-ledger-http` | The ledger behind a Spring-free HTTP API with Javalin, Mockito for the handlers | one evening | optional |

Each folder's `README.md` has the stories, the edge cases, and the questions the interviewer
asks afterwards.

## How to practise a project

1. Open the folder's README but read **only the next story**. Cover the rest.
2. Start a timer. Say the story back in one sentence, ask two clarifying questions out loud
   (write your answers down as assumptions), state your plan.
3. Write a failing test, make it pass, cover the edge cases, run the whole module green.
4. Say "done", reveal the next story. Do not refactor mid-story; note it, come back at the end.
5. When the timer ends, stop, and answer the "questions the interviewer asks afterwards"
   out loud, in full sentences.
6. Then, and only then, compare with the solution branch.

## Solutions

Every project has a complete, tested solution on its own branch:

```bash
git checkout solutions/revolut/01-ledger          # then ./mvnw -pl 01-ledger test
git checkout solutions/revolut/02-load-balancer
git checkout solutions/revolut/03-url-shortener
git checkout solutions/revolut/04-rate-limiter
git checkout solutions/revolut/05-sql-transfer
git checkout solutions/revolut/06-ledger-http     # includes the 01-ledger solution it depends on
git checkout main                         # back to the skeletons
```

`git diff main solutions/revolut/01-ledger -- 01-ledger` shows exactly what the solution added.
Your own work is safest on a branch of your own: `git checkout -b yourname/revolut-01-ledger main`.

## Reset a project to the skeleton

```bash
git checkout main -- java/live-coding/revolut/01-ledger
```

## What is on the classpath

JUnit 5, Mockito 5, AssertJ 3 in every module. H2 in `05-sql-transfer`. Javalin and Jackson in
`06-ledger-http`. Java 21 language level. That is deliberately the interview's own setup:
"set up an empty project in your favourite IDE and a library for testing (JUnit, Mockito)".
