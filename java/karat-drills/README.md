# Karat Drills — Live Coding Practice

Nineteen Karat-style coding questions, one package each, rebuilt from a question bank of real Karat
screens for the Java (+ React) skillset. Karat runs the first technical interview for a long list of
companies, and the coding half always has the same shape: you get a page of plain Java that models a
small back-office system (a gym, a clinic, a license pool, a stock ledger), one provided test is red
because of a bug, and once it is green you add two functions to a written spec with their tests
provided. Sometimes there is a third part. The interviewer watches whether you run the test and read
the failure before you type, whether you handle the empty case and the unknown id without being told,
and whether you can finish Part 2 in the time left. Time box: **35 minutes per drill**, in the real
screen you get one drill. The theory half of the screen is in
[`../KARAT.md`](../KARAT.md), with every question from the bank answered.

## Tech Stack

- **Java 21**, **JUnit 5, AssertJ 3**, Maven wrapper. No Spring, no database.

## Getting Started

- JDK 21+
- `./mvnw test` is green on `main`: every drill's test class is `@Disabled` until you start it.
- Pick a drill from [`INTERVIEW_TASKS.md`](INTERVIEW_TASKS.md). Open its test class under
  `src/test/java/com/amigoscode/karat/<drill>/`, delete the class-level `@Disabled` line, run
  `./mvnw test -Dtest='**/karat/<drill>/*Test'`. The Part 1 test is red: that is the exercise.
- When Part 1 is green, delete the `@Disabled("part 2: ...")` lines, which reveals the Part 2 tests,
  exactly as Karat reveals them. The Part 2 methods exist as stubs that throw
  `UnsupportedOperationException`; the Javadoc on each stub is the spec.

## How the Interview Runs

One drill, one timer, parts in order. Open [`INTERVIEW_TASKS.md`](INTERVIEW_TASKS.md), cover
everything below the current part, and read the code and the failing test out loud before you touch
anything. Practise without an AI assistant; you will not have one in the screen.

## Solution

`git checkout solutions/karat-drills` then `./mvnw test` (all green, every `@Disabled` removed, plus
extra edge-case tests the Karat tests miss).

Reset to the skeleton: `git checkout main -- java/karat-drills`.

<details>
<summary>Spoiler: the planted bug in every drill</summary>

| Drill | Part 1 bug |
|---|---|
| `gym` | Conversion rate divides two ints before multiplying by 100.0, so 4 of 6 is 0.0 |
| `stocks` | `max()`/`min()` call `getAsInt()` on an empty stream and the average divides by zero; the spec says -1 |
| `payments` | `getBalance` only adds CREDIT transactions and never subtracts DEBIT |
| `clinic` | The no-show loop counts every appointment that is not COMPLETED |
| `fooddelivery` | The "active" check leaves out OUT_FOR_DELIVERY |
| `games` | The wins loop counts LOSS |
| `music` | Total plays and unique songs are computed over completed plays only |
| `equipment` | Cancelled reservations still block the slot, and `cancelReservation` returns the opposite boolean |
| `access` | `isAuthorized` compares levels with `==` instead of by rank |
| `stockledger` | `applyInventoryUpdate` never compares `updatedAt`, so a stale update overwrites the record |
| `devices` | Only the status name is validated, never the transition table |
| `notifications` | `getOrderNotifications` collects the order id instead of the notification id |
| `alerts` | `hasOpenAlert` returns on the first alert of the service, open or not |
| `licenses` | `isLicenseAvailable` checks the status and ignores the assigned user |
| `compute` | Every reservation consumes capacity, not only ACTIVE ones |
| `syncqueue` | `countPendingUpdates` counts every FAILED update, retryable or not |
| `crm` | `findByEmail` compares case-sensitively |
| `bank` | TRANSFER is treated as a credit |
| `snippets` | Five separate ones: a null `Boolean`, a leaked `Date`, an in-place sort, a mutated `HashSet` key, `Optional.get()` |

</details>
