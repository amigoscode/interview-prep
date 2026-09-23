# Secret Storage — Interview Practice

A small Spring Boot service used as a **take-home security case followed by a presentation**. The
candidate gets the repository in advance, finds and fixes the problem, and then presents the fix and
their reasoning to two interviewers for up to 15 minutes, followed by questions on the solution.

The brief below is reproduced **as a real company issued it**, adapted only to this repository's
package names and file layout. It is short on purpose. The code change is small; what is being
assessed is whether the candidate can say *who* the problem exposes, fix it without over-engineering,
and explain how the same secret should reach the app on a laptop, in CI and in production.

## Tech Stack

- **Java 25**
- **Spring Boot 4.0.2**
- **Maven**
- **Docker**
- No database, no external services

## Getting Started

> This is the **reference solution** branch. The reasoning, the presentation outline and what was
> deliberately left out are in [`SOLUTION.md`](SOLUTION.md).

### Prerequisites

- Java 25+
- Maven 3.9+ (the wrapper is included)
- Docker (optional)
- `htpasswd` to create a password hash (ships with macOS; `apache2-utils` on Debian/Ubuntu)

### Configure the Admin Password

The app never sees the password, only a bcrypt hash of it, supplied by the environment. Nothing
secret is committed and nothing secret is built into the jar or the image.

```bash
cp .env.example .env
htpasswd -bnBC 12 "" 'choose-a-password' | tr -d ':\n'   # paste the output into .env
```

Paste the hash into `.env` **unquoted**: `docker run --env-file` keeps quotes as part of the value.
Without `ADMIN_PASSWORD_HASH` the app refuses to start and says so.

### Run the Application

```bash
export ADMIN_PASSWORD_HASH='<paste the hash>'
./mvnw spring-boot:run
```

Use **single quotes**, and do not `source .env`: a bcrypt hash is full of `$`, and the shell expands
them. `$2y$12$abc...` quietly becomes `y2bashabc...`. If that happens the app refuses to start with
`must be a bcrypt hash` rather than rejecting every login.

The app starts on **http://localhost:8080** and asks for credentials.

### Run Tests

```bash
./mvnw clean test
```

The tests supply their own throwaway hash, so they need no `.env`.

### Run with Docker

```bash
docker build -t secret-storage .
docker run --env-file .env -p 8080:8080 secret-storage
```

With `docker compose`, write every `$` in the hash as `$$`, because compose interpolates it.

### Try the Endpoint

```bash
curl -u admin:choose-a-password http://localhost:8080
```

---

## The Assessment

> ### Case: Secret Storage
>
> **Background**
>
> You've just joined a small team that has built a simple internal service. The application is a
> Spring Boot web app that requires authentication to access. It's packaged as a Docker container and
> run locally.
>
> Your tech lead has asked you to take a look at the repository and address any security concerns you
> find with how the application is configured — particularly around how secrets are handled.
>
> **Your Task**
>
> 1. **Identify the security problem** in this repository.
> 2. **Fix it.** Implement a solution that resolves the issue.
> 3. **Describe your reasoning.** Briefly explain why the current approach is problematic and how your
>    solution addresses it. If your ideal solution involves infrastructure or tooling beyond this
>    repo, feel free to describe what that would look like — you don't need to build it, but show us
>    your thinking.
>
> **What We're Looking For**
>
> - Can you spot the problem?
> - Is your solution practical and well-reasoned?
> - How do you think about this in a broader context (local development, CI/CD, production)?
> - No more than 15 minutes presentation on your solution and thoughts
>
> There is no single "right" answer. We want to see how you think.
>
> **Time Expectation**
>
> This should not take long. Don't over-engineer it. A clean, simple fix with clear reasoning is
> preferred over a complex one. You can hand-wave external services that might be part of your ideal
> design.

---

## What You Get

```
secret-storage/
├── .env.example                        ← what to put in your gitignored .env
├── .dockerignore
├── Dockerfile                          ← non-root, tests run in the build, no secret baked in
├── SOLUTION.md                         ← the reasoning
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/amigoscode/interview/
    │   │   ├── SecretStorageApplication.java
    │   │   ├── hello/
    │   │   │   └── HelloController.java    ← GET /, no authentication code left in it
    │   │   └── security/
    │   │       ├── AdminProperties.java    ← validated at startup: set, and a bcrypt hash
    │   │       └── SecurityConfig.java     ← HTTP Basic, deny by default, stateless
    │   └── resources/
    │       └── application.yml             ← no secret, only ${ADMIN_PASSWORD_HASH}
    └── test/java/com/amigoscode/interview/
        └── security/
            ├── AuthenticationTest.java     ← real HTTP: 401 / 200 / malformed headers
            └── StartupValidationTest.java  ← missing or plaintext value stops startup
```

Everything works: the right credentials return `200`, anything else returns `401`. You are free to
restructure anything, add dependencies, and change the Dockerfile, as long as `GET /` still requires
authentication and the README still tells a new developer how to run it.

## What You Deliver

- The fix, as commits a reviewer can follow
- An updated README: a new developer must be able to clone and run the app from it
- Your reasoning in writing — in the README, a `SOLUTION.md`, or the PR description
- A presentation of **15 minutes at most**, then questions

Pick tasks from [`INTERVIEW_TASKS.md`](INTERVIEW_TASKS.md) according to level. If you are practising
as the candidate, do the brief first: that file discusses the answers.

## Notes for Interviewers

The one planted problem is easy to find, so finding it earns little on its own. The signal is in
everything around it. Worth probing whichever tasks you pick:

- **Who could read the secret before the fix?** Listen for more than "anyone with the code". Where
  else does that file end up once the project is built and shipped?
- **Is the old value safe once it is removed from the file?** The candidate should raise this without
  being asked.
- **Clone their fork and follow their README exactly, before the call.** Whether the app starts is
  the cheapest and most telling check you can run.
- **Send a few malformed `Authorization` headers** at their version before the call. Hand-rolled
  parsing breaks in predictable ways; asking the candidate to find the cause in their own code, live,
  is worth more than any prepared question.
- **Scope.** The brief says not to over-engineer. Rate limiting, lockout and similar additions are
  not wrong, but they are code the candidate now has to defend line by line.

A candidate who fixes one file and then speaks clearly about local, CI and production is doing better
than one who ships a large diff and cannot explain where the secret comes from in production.
