# @Transactional — How It Works and How It Fails

> **In `start/`, `./check` FAILS on purpose: 22 of 32 checks pass.** That is the exercise, not a
> broken checkout. Tasks 2, 4, 6 and 8 turn the other 10 green.

A bank transfer takes 50 from Alice and gives it to Carol. The second half fails. The method "is
`@Transactional`", so nothing should have changed. Alice is 50 short anyway.

This project lets you watch that happen, then walks through six different ways `@Transactional`
quietly does nothing, or does something you did not ask for. You fix each one and look at what
Spring does differently afterwards.

It answers the interview questions students most often get wrong:

- "How does `@Transactional` work internally?"
- "What is a transaction?"
- "A `@Transactional` method throws an exception. Is the transaction rolled back?"
- "`REQUIRED` vs `REQUIRES_NEW`: the outer transaction crashes. What happens to the inner one?"

## Tech Stack

- **Java 25**, **Spring Boot 4** (Spring Framework 7), Spring Data JPA (Hibernate 7)
- **PostgreSQL 17** in Docker
- JUnit 5, AssertJ, Testcontainers

## What you need

- JDK 25
- Docker, running

## Getting Started

Work in `start/`. Commands are shown for macOS and Linux; on Windows type `.\demo` where you see
`./demo` (same for `inspect` and `check`).

```bash
cd start
docker compose up -d      # Postgres on port 5442
./demo                    # watch 50 disappear
./check                   # 22 of 32 pass: that is your starting point
```

The first run downloads Maven and the dependencies, so it takes a minute or two.

When you are finished: `docker compose down`.

## The three tools

### `./demo` — see what happens

Starts the real application, puts the bank back to its starting state, calls one service method
the way another class would, and prints the accounts before and after. It ends with a `RESULT:`
line.

```
./demo                                  Alice sends 50 to Carol (scenario: transfer)
./demo --scenario atm                   one scenario per pitfall, see the list below
./demo --sql                            print every SQL statement as it is sent
./demo --help
```

| Scenario | Pitfall | Tasks |
|---|---|---|
| `transfer` | self-invocation: `this.method()` skips the proxy | 1, 2, 3 |
| `bill` | a checked exception does not roll back | 4, 5 |
| `standing-order` | an exception caught inside the method | 4, 5 |
| `payroll` | "rollback-only" and `UnexpectedRollbackException` | 5 |
| `atm` | propagation: the audit row that must survive | 6, 7 |
| `requires-new-trap` | `REQUIRES_NEW` in the wrong place | 7 |
| `fee` | a `final` method | 8, 9 |
| `rename` | `readOnly = true` | 8, 9 |
| `visibility` | which methods can be transactional at all | 9 |

### `./inspect` — see the transactions

You cannot see a transaction in the code. `./inspect` makes it visible. It starts the application
and prints:

1. **BEANS**: for every bean, whether other classes get your object or a proxy Spring generated,
   and the class of that proxy.
2. **TIMELINE**: it runs one scenario and prints every database transaction that was really
   opened, committed or rolled back, and for each service method whether a transaction was active
   when it was entered (this comes from Spring's `TransactionSynchronizationManager`).

It takes the same flags as `./demo`: `./inspect --scenario atm`, `./inspect --sql`.

### `./check` — are the acceptance criteria met?

`./check 2` runs the tests for Task 2 and prints one line per criterion. `./check` runs everything.
`./mvnw verify` runs the same tests the usual Maven way.

The tests are the acceptance criteria. **Never edit, delete or disable a test.**

## The code

```
start/src/main/java/com/amigoscode/transactional/
├── account/         Account, AccountRepository, AccountService (Task 8: readOnly)
├── transfer/        TransferService. THE FIRST BUG IS HERE (Task 2: self-invocation)
├── bill/            BillService (Task 4: a checked exception)
├── standingorder/   StandingOrderService and the nightly StandingOrderJob (Task 4: a swallowed exception)
├── audit/           AuditService (Task 6: propagation)
├── atm/             AtmService: writes an audit row, then takes the money (Task 6)
├── fee/             FeeService (Task 8: a final method)
├── payroll/         exhibit for Task 5. Do not fix
├── trap/            exhibit for Task 7. Do not fix
├── exhibit/         exhibit for Task 9. Do not fix
├── trace/           TransactionTrace: records what ./inspect prints. Nothing to change here
└── demo/            the demo and inspect tools. Nothing to change here
```

Every service method starts with `TransactionTrace.record("Class.method")`. That line is a
teaching aid: it asks Spring whether a transaction is open right now. Leave those lines in.

The three exhibits are the same in `start/` and `solution/`. They show behaviour that surprises
people and that has no single right fix. You look at them, you do not change them.

## Two ways to work

- **[`INTERVIEW_TASKS.md`](INTERVIEW_TASKS.md)**: challenge mode. A story, acceptance criteria and
  hints for each task. Use this if you want to work it out yourself.
- **[`TASKS_GUIDED.md`](TASKS_GUIDED.md)**: the same ten tasks, step by step, with the output you
  should see and an explanation of why. Use this to learn the concept, or when you are stuck.

Both files use the same task numbers.

| Task | What you do | Checked by |
|---|---|---|
| 1 | See the money disappear | you answer questions |
| 2 | Fix the self-invocation | `./check 2` |
| 3 | Inspect the proxy | you answer questions (`./check 3` passes: it documents the behaviour) |
| 4 | Fix the rollback rules: a checked exception, a swallowed exception | `./check 4` |
| 5 | Inspect commit and rollback, and meet "rollback-only" | you answer questions (`./check 5` passes) |
| 6 | Fix the propagation: the audit row must survive | `./check 6` |
| 7 | Inspect `REQUIRED` and `REQUIRES_NEW` | you answer questions (`./check 7` passes) |
| 8 | Fix the annotations that do nothing: a `final` method, `readOnly` | `./check 8` |
| 9 | Inspect what a proxy cannot do | you answer questions (`./check 9` passes) |
| 10 | Name the pitfall | you answer questions |

## Solution

`solution/` is the same project with every task done: `cd solution && ./check` prints 32 of 32.

To start again from scratch: `git checkout -- start` (this throws away your changes in `start/`).

<details>
<summary>Spoiler: the six pitfalls and their fixes</summary>

**How it works.** Spring does not change your class. It generates a subclass of it, the proxy, and
gives the proxy to every bean that asks for yours. The proxy's version of a `@Transactional`
method opens a transaction, calls your real method, then commits if the method returned and rolls
back if it threw a `RuntimeException` or an `Error`. Every pitfall below is a case where the call
does not go through the proxy, or where the proxy gets the wrong signal at the end.

| # | Pitfall | What you see | Fix |
|---|---|---|---|
| 1 | Self-invocation: `transfer()` calls `this.moveMoney()` | No transaction at all. Each repository call commits on its own, so the debit stays when the credit fails | Put `@Transactional` on the method that is called from outside (`transfer`) |
| 2 | Checked exception | The transaction **commits** | `@Transactional(rollbackFor = BillRejectedException.class)`, or make the exception unchecked |
| 3 | Exception caught inside the method | The method returns normally, so the transaction **commits** | Let the exception leave the transactional method; catch it in the caller, outside the transaction |
| 3b | Inner `REQUIRED` call fails, outer catches it | `UnexpectedRollbackException` at commit, nothing is saved | Decide what you want: all or nothing (do not catch), or one transaction per item |
| 4 | Audit row written with `REQUIRED` | It is rolled back together with the business change | `@Transactional(propagation = Propagation.REQUIRES_NEW)` on a method of **another bean** |
| 4b | `REQUIRES_NEW` on the debit | The inner transaction commits. When the outer fails later, the debit stays | Only use `REQUIRES_NEW` for work that should survive a failure of the caller |
| 5 | `final` method | The proxy cannot override it. No transaction, and injected fields are `null` | Remove `final` |
| 6 | `readOnly = true` on a method that writes | Entity changes are silently not written | A plain `@Transactional` on the method that writes |

On Spring Framework 7, `protected` and package-private `@Transactional` methods **do** work.
"Only public methods" was true before Spring 6. `private` and `final` still do not.

</details>
