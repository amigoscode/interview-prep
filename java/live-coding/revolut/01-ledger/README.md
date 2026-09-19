# 01 Ledger: thread-safe money transfers

**The Revolut task.** Every report of the "Technical Conversation Interview" describes this
one: an in-memory bank, then "make it safe for many threads", then database questions about
the same problem. Time yourself: **35 minutes** for stories 1 to 3, then story 4 if time remains.

How the real interview runs: the interviewer pastes one story at a time into the chat. You
finish it, with tests, say "done", and only then get the next one. Practise the same way:
cover the stories below with a sheet of paper and reveal them one by one.

```bash
./mvnw -pl 01-ledger test
```

## Rules

- No Spring, no frameworks, no AI tools. JUnit 5, Mockito and AssertJ are on the classpath.
- Money is `BigDecimal`. Never `double`.
- Every story ends with green tests that cover its edge cases.
- Talk out loud as you go, even alone. Silence is marked down.

## User stories

### Story 1: accounts

> As a customer I can open an account with an id and an opening balance, deposit, withdraw and
> check my balance.

Edge cases to cover: duplicate id, unknown id, zero or negative amount, negative opening
balance, withdrawal larger than the balance.

### Story 2: transfer

> As a customer I can transfer an amount from my account to another account. Either both
> balances change or neither does.

Edge cases: transfer to the same account, insufficient funds leaves both accounts untouched,
unknown source or destination.

### Story 3: many threads

> The ledger will be called by many threads at once. Transfers between the same two accounts in
> both directions must never deadlock, never lose money and never overdraw.

Prove it with a test: 16 threads, half transferring A to B and half B to A, thousands of
times, then assert the total is unchanged and no balance is negative. Before you fix the
lock order, run the naive version once and watch it hang. That is the story you tell.

### Story 4: pick one extension

- **Idempotency key.** `transfer(key, from, to, amount)`: replaying the same key returns the
  first result and does not move money twice.
- **Transaction history.** `history(id)` returns an immutable list of what happened to an
  account, consistent with its balance.
- **Daily withdrawal limit.** No more than 1000 per account per calendar day. Inject a `Clock`.
- **Result type instead of exceptions.** A sealed `TransferResult` with `Success`,
  `InsufficientFunds`, `AccountNotFound`.

## Questions the interviewer asks afterwards

- Why not `synchronized` on the transfer method?
- Why acquire the locks in id order? What else would prevent the deadlock?
- Is `ReentrantLock` reentrant, and where in your code does that matter?
- What does `ConcurrentHashMap.computeIfAbsent` guarantee that `get` then `put` does not?
- How would this look in Postgres? (Go do `05-sql-transfer` next.)

## Solution

`git checkout solutions/revolut/01-ledger`, then `./mvnw -pl 01-ledger test`. Read it only after your
own attempt. The interesting part is `Ledger.transfer` and `LedgerConcurrencyTest`.
