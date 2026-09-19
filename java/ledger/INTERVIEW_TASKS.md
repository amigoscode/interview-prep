# Interview Tasks — Ledger

Tasks are **sequential user stories**, given one at a time, each finished with tests before the
next is revealed. The candidate does not see the list in advance. Budget **35 minutes** for tasks
1 to 3, then task 4 if time remains, then 20 minutes of questions.

Grade on: clarifying before coding, a working MVP per task, tests with edge cases, SOLID and
naming, thread safety, and talking through the reasoning throughout.

---

## Task 1: Accounts (8 min)

**Story:** *As a customer I can open an account with an id and an opening balance, deposit,
withdraw and check my balance.*

**What to do:**
1. Implement `open`, `balance`, `deposit`, `withdraw` on `Ledger`
2. Model an `Account`; keep money as `BigDecimal`
3. Write the tests first where it is cheap

**Acceptance Criteria:**
- Duplicate id is rejected
- Unknown id is rejected on every operation
- Zero or negative amounts are rejected; a negative opening balance is rejected
- Withdrawal larger than the balance is rejected and changes nothing
- Withdrawing exactly the balance works

**Hints:**
- Two clarifying questions worth asking: "Can the opening balance be zero?" and "What should an
  unknown account do: exception or empty result?"
- `ConcurrentHashMap.putIfAbsent` gives you duplicate detection in one step

---

## Task 2: Transfer (8 min)

**Story:** *As a customer I can transfer an amount from my account to another. Either both
balances change or neither does.*

**What to do:**
1. Implement `transfer(fromId, toId, amount)`
2. Reuse the validation from task 1

**Acceptance Criteria:**
- Transfer to the same account is rejected
- Insufficient funds leaves **both** accounts untouched
- Unknown source or destination is rejected before anything changes
- Non-positive amounts are rejected

**Hints:**
- Check everything you can before touching either balance

---

## Task 3: Many Threads (15 min, the one that decides the interview)

**Story:** *The ledger will be called by many threads at once. Transfers between the same two
accounts in both directions must never deadlock, never lose money and never overdraw.*

**What to do:**
1. Make `Ledger` thread-safe without serialising the whole bank
2. Prove it with a test: 16 threads, half transferring A to B and half B to A, thousands of times

**Acceptance Criteria:**
- Total money across A and B is unchanged after the run
- No balance is ever negative
- The test completes (a deadlock shows up as a timeout)
- The candidate can explain why `synchronized` on the transfer method is not the answer

**Hints:**
- A lock per account, not per ledger
- Two accounts, two locks: acquire them in a **global order** (by id), or two threads moving money
  in opposite directions each hold one and wait for the other
- The balance check must be inside the locked section, or two threads both pass it
- `ReentrantLock` is reentrant: reading the balance under the lock you already hold is fine, and
  saying so is worth a point
- A `ReadWriteLock` is a trap here; a transfer is a write on both sides
- Before fixing the order, run the naive version once and watch it hang. That is the story to tell

---

## Task 4: One Extension (remaining time)

Pick one:

- **Idempotency key.** `transfer(key, from, to, amount)`: replaying the same key returns the first
  result and does not move money twice. `computeIfAbsent` makes "check the key, then transfer" one
  atomic step. In production the key map lives in Postgres or Redis with a TTL.
- **Transaction history.** `history(id)` returns an immutable list, appended inside the same lock as
  the balance change so history and balance never disagree.
- **Daily withdrawal limit.** No more than 1000 per account per calendar day. Inject a `Clock`.
- **Result type instead of exceptions.** A sealed `TransferResult` with `Success`,
  `InsufficientFunds`, `AccountNotFound`, matched with a `switch`.

---

## Questions to Ask Afterwards (20 min)

- Why not `synchronized` on the transfer method? What does it cost with ten million accounts?
- Why acquire locks in id order? What else prevents the deadlock? (`tryLock` with timeout)
- Where does reentrancy matter in your code?
- What does `ConcurrentHashMap.computeIfAbsent` guarantee that `get` then `put` does not?
- How would this look in Postgres? Which isolation level fixes the lost update? Pessimistic
  (`SELECT ... FOR UPDATE`) or optimistic (version column), and when each? (See
  `sql-transfer`.)
- What is a lost update, a dirty read, a phantom read?
- What is DDD? Which of your classes is the aggregate? What is CQRS?

## Tips for Interviewers

- Reveal tasks one at a time, in the chat, as text. Do not answer "how should I do it"; answer
  "what should happen when".
- If the candidate starts typing before asking anything, note it. Clarifying first is graded.
- In task 3, if they reach for `synchronized` on the method, accept it as an MVP and ask about
  scale; the per-account lock is the expected refinement.
- The deadlock is the trap. If they lock in caller order and the test hangs, let them find it.
- Rejection feedback in real reports was specific: a deadlock left in, "vague" database answers,
  and not knowing terms (DDD, CQRS, "buckets" for HashMap). Probe those three.
