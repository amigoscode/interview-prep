# Interview Tasks — Constraints and Races

> **In `start/`, `./check` FAILS on purpose: 19 of 35 checks pass.** Tasks 2, 4, 5, 6 and 7 turn the
> other 16 green. Tasks 1, 3 and 8 are inspection tasks: you run things, look, and answer questions.

Do the tasks in order. Every fix is followed by an inspection: Tasks 3 and 8 are nothing else, and
Tasks 4 to 7 each end with an **Inspect** part. A green test tells you the fix works, the inspection
tells you **why**, and the why is what an interviewer asks for.

Stuck, or want the explanation? The same task number in [`TASKS_GUIDED.md`](TASKS_GUIDED.md) walks
you through it with the expected output.

Commands are for macOS and Linux. On Windows type `.\demo`, `.\inspect` and `.\check`.

**Rules:** work in `start/`. Never edit, delete or disable a test. Constraints go into
`src/main/resources/schema.sql`, which is applied on every start. Do not fix
`SettingsStore.checkThenInsert` or `SettingsStore.insertAndCatchInside`: they stay broken so you can
compare against them. Do not add validation to the controllers: Task 7 needs them as they are.

---

## Task 1: See the Duplicate Signup (10 min)

**Story:** *Support has a customer who cannot log in: there are two accounts with her email. The
signup code checks `existsByEmail` before it saves, so "this cannot happen". Before you change
anything, reproduce it.*

**Steps:**
1. `docker compose up -d`, then `./demo`. How many accounts were created? How many rows are there?
2. `./demo --sql`. Each line shows the thread that ran the statement. Write down the order of the
   statements across the two threads.
3. Open `SignupService.signUp`. Match each SQL statement to the line of Java that produced it.
4. `./inspect`. Which constraints does `users` have? Find the duplicate in the last section.
5. `./demo --requests 20`. How many rows now?
6. `./demo --work-ms 0`, a few times. Does the bug still happen?

**Acceptance Criteria** (`./check 1`, already passing: this test documents the race)
- [ ] You can name the pattern that causes the bug
- [ ] You can point to the two SQL statements the bug happens between
- [ ] You can explain why the second request's check did not see the first request's row
- [ ] You can explain why a fast machine, or `@Transactional`, does not make the bug go away
- [ ] `SignupRaceIT.bothRequestsPassTheCheck` passes

**Hints:**
- Both requests ran the same `select` before either ran an `insert`
- Who is the only party that sees both inserts?

---

## Task 2: Fix It With a UNIQUE Constraint and a 409 (20 min)

**Story:** *Make the database refuse the second row. Then make sure the person who lost the race
gets a clear "this email is taken", not an error page. Edit `schema.sql`, then `SignupService`.*

**Acceptance Criteria** (`./check 2`)
- [ ] The database refuses a second row with the same email, even from code that skips the service
      — `UniqueEmailIT.theDatabaseRefusesTheSameEmailTwice`
- [ ] Two signups with the same email at the same moment: one account, the other is told the email
      is taken — `UniqueEmailIT.onlyOneOfTwoSignupsCreatesAnAccount`
- [ ] 20 signups with the same email: 1 account, 19 told the email is taken, 1 row
      — `UniqueEmailIT.onlyOneOfTwentySignupsCreatesAnAccount`
- [ ] When the insert is refused by `users_email_key`, the signup fails with `EmailTakenException`
      — `SignupServiceTest.aViolationOfTheEmailConstraintMeansEmailTaken`
- [ ] A violation of any other constraint is not reported as "email taken"
      — `SignupServiceTest.aViolationOfAnotherConstraintIsNotEmailTaken` (already passing: keep it
      that way)
- [ ] A signup that arrives after the first one finished is still stopped by the application's
      check — `UniqueEmailIT.aLaterSignupIsStoppedByTheFastPath` (already passing: keep it that way)
- [ ] `./demo` prints `RESULT: OK`
- [ ] The baseline tests still pass (`./check`)

**Hints:**
- Name the constraint `users_email_key`. Your code will need that name
- Add the constraint first and run `./demo` before you touch the Java. Look at what the loser gets
- Spring throws `DataIntegrityViolationException`. Its cause is Hibernate's
  `ConstraintViolationException`, which has `getConstraintName()`
- Do not delete the `existsByEmail` check. What is it still good for?

---

## Task 3: Inspect the Constraint (15 min)

**Story:** *Your fix passes the tests. Now prove you know why.*

**Steps:**
1. `./demo --sql`. Both threads still run the `select` and both still run the `insert`. Which line
   is new compared with Task 1? Who wrote it: your code, Hibernate or Postgres?
2. In the same output: the loser's `insert` was sent at the same moment as the winner's. What did
   the database do with it until the winner committed?
3. `./inspect`. `users` now has one more line under CONSTRAINTS and one more under INDEXES. Why
   both?
4. `./demo --requests 20`. Look at the `id` of the one row that exists. What happened to the other
   numbers?
5. Comment out the `existsByEmail` check in `SignupService` and run `./check 2`. Which check fails?
   Is the data still correct? Put the check back.
6. `./demo --scenario case`. What is the `RESULT`? Why did the constraint not help?

**Acceptance Criteria** (no tests: you answer)
- [ ] You can quote the error Postgres sends, including the constraint name and the SQLSTATE
- [ ] You can say what the losing `insert` waits for
- [ ] You can explain the relationship between a UNIQUE constraint and a unique index
- [ ] You can give two reasons to keep the application's check, and one reason it is not enough
- [ ] You can explain what happens when you add a UNIQUE constraint to a table that already holds
      duplicates

---

## Task 4: The Same Email in Different Capital Letters (15 min)

**Story:** *`Ana@Example.com` and `ana@example.com` are the same mailbox, and both just signed up.
The constraint compares text exactly, so it let them through. Marketing wants the email kept the
way the user typed it, so lower-casing it before saving is not allowed.*

**Acceptance Criteria** (`./check 4`)
- [ ] The database refuses `ana@example.com` when `Ana@Example.com` is already there
      — `CaseInsensitiveEmailIT.theDatabaseRefusesTheSameEmailInAnotherCase`
- [ ] Both spellings sign up at the same moment: one account, the other is told the email is taken
      — `CaseInsensitiveEmailIT.onlyOneOfTwoSpellingsCreatesAnAccount`
- [ ] `ANA@EXAMPLE.COM` arriving after `ana@example.com` is stopped by the application's check
      — `CaseInsensitiveEmailIT.aLaterSignupInAnotherCaseIsStoppedByTheFastPath`
- [ ] `./demo --scenario case` prints `RESULT: OK`
- [ ] `./check 2` still passes

**Hints:**
- A constraint cannot contain a function call. A unique **index** can: `create unique index ... on
  users (lower(email))`
- The new index has its own name. What does the loser get before you teach `SignupService` that
  name?
- The check has to compare the same way as the index

**Inspect:**
1. `./inspect`. Where does the new rule show up: under CONSTRAINTS or under INDEXES?
2. `./demo --scenario case --sql`. Which constraint name is in the error now?
3. Spring Data can derive `existsByEmailIgnoreCase` on its own. Try it and look at the SQL with
   `--sql`. Which function does it use, and can that query use your index?

---

## Task 5: One ACTIVE Subscription per User (15 min)

**Story:** *A customer was charged twice this month: two ACTIVE subscriptions, started in the same
second. The rule is "at most one ACTIVE subscription per user". Cancelled subscriptions are
history: a user may have any number of them. Edit `schema.sql`, then `SubscriptionService`.*

**Acceptance Criteria** (`./check 5`)
- [ ] The database refuses a second ACTIVE subscription for the same user
      — `OneActiveSubscriptionIT.theDatabaseRefusesASecondActiveRow`
- [ ] One user subscribes twice at the same moment: one ACTIVE subscription, the other request is
      told the user is already subscribed — `OneActiveSubscriptionIT.onlyOneOfTwoRequestsSubscribes`
- [ ] When the insert is refused by `subscriptions_one_active_per_user`, it fails with
      `AlreadySubscribedException`
      — `SubscriptionServiceTest.aViolationOfTheOneActiveIndexMeansAlreadySubscribed`
- [ ] Subscribe, cancel, subscribe, cancel, subscribe works: 2 CANCELLED rows and 1 ACTIVE row
      — `OneActiveSubscriptionIT.cancelledRowsMayRepeat` (already passing: keep it that way)
- [ ] Two different users are both ACTIVE — `OneActiveSubscriptionIT.twoUsersCanBothBeActive`
      (already passing: keep it that way)
- [ ] `./demo --scenario subscription` prints `RESULT: OK`

**Hints:**
- `unique (user_id)` is too strict and `unique (user_id, status)` is too: why?
- An index can have a `WHERE` clause. Rows that do not match it are not in the index at all
- Name it `subscriptions_one_active_per_user`

**Inspect:**
1. `./inspect`. Read the definition of the new index.
2. Remove the `where` from your index and run `./demo --scenario subscription`. What is the
   `RESULT`? Put it back.
3. `./demo --scenario subscription --requests 20`. Which id does the new subscription get after
   the cancel?

---

## Task 6: Create If Absent, and the Aborted Transaction (25 min)

**Story:** *The home screen asks for the user's settings. The first time, the row does not exist
yet, so the code creates it with the defaults. The app sends two requests when it opens, and one
of them gets a 500. `user_settings` already has a primary key on `user_id`, so the data is fine.
The application is not. Somebody already tried `try { insert } catch { select }`: it made things
worse. Fix `SettingsStore.insertOnConflictDoNothing` and `SettingsStore.saveTheme`.*

**Steps before you write code:**
1. `./demo --scenario settings --strategy check-first`. Who gets the 500, and what does it say?
2. `./demo --scenario settings --strategy catch-inside --sql`. The duplicate is caught. What fails
   now? Which statement was it?
3. Read `SettingsStore.insertAndCatchInside`, then the two strategies that work:
   `SettingsService.insertAndCatchOutside` and `SettingsStore.insertWithSavepoint`. Run both with
   `--sql`.

**Acceptance Criteria** (`./check 6`)
- [ ] on-conflict: two first requests at the same moment: both get the settings, one row, exactly
      one is told it created the row — `SettingsIT.onConflictDoNothingSurvivesARace`
- [ ] on-conflict: an existing row is left alone: a saved theme is not reset to the default
      — `SettingsIT.onConflictDoNothingDoesNotOverwrite` (already passing: keep it that way)
- [ ] save theme: two saves at the same moment for a user without a row: both succeed, one row,
      exactly one is told it created the row — `SettingsIT.saveThemeSurvivesARace`
- [ ] save theme: the first save creates the row, the second one updates it
      — `SettingsIT.saveThemeCreatesThenUpdates` (already passing: keep it that way)
- [ ] The five tests that document the bug, the trap and the two working shapes still pass
- [ ] `./demo --scenario settings` and `./demo --scenario theme` print `RESULT: OK`

**Hints:**
- `insert ... on conflict (user_id) do nothing`. `JdbcTemplate.update` returns the number of rows
  the statement inserted
- `insert ... on conflict (user_id) do update set theme = excluded.theme`. It reports 1 row whether
  it inserted or updated, so the row count is no use here. Look up `RETURNING` and `xmax`
- Neither statement can fail on the primary key. Why does that matter inside a transaction?

**Inspect:**
1. `./demo --scenario settings --sql`. How many statements does each request run? Is there an
   error anywhere in the output?
2. Read `SettingsIT.swallowingInsideTheTransactionLosesEarlierWrites`. The transaction returns
   normally. What happened to the `update` that ran before the refused `insert`?

---

## Task 7: Bad Data: NOT NULL, CHECK and FOREIGN KEY (15 min)

**Story:** *Finance found a subscription with -3 seats, one with a negative price, one for a user
that does not exist, and a user without an email. The controllers validate nothing. Somebody will
fix the validation, and somebody else will add a new endpoint next year and forget it again. Make
the database refuse these rows whoever sends them. Only `schema.sql` changes.*

**Acceptance Criteria** (`./check 7`)
- [ ] A user without an email is refused — `BadDataIT.aUserNeedsAnEmail`
- [ ] A subscription with -3 seats is refused — `BadDataIT.seatsMustBePositive`
- [ ] A negative price is refused — `BadDataIT.thePriceMustNotBeNegative`
- [ ] A subscription for a user that does not exist is refused — `BadDataIT.aSubscriptionNeedsAUser`
- [ ] `./demo --scenario bad-data` prints `RESULT: OK`
- [ ] `./check` prints 35 passes and no failures

**Hints:**
- Three kinds of rule: the value must be there, the value must make sense on its own, the value
  must point at a row that exists
- `subscriptions.status` already has a named `CHECK`: copy its shape
- A UNIQUE constraint accepts any number of NULLs. Which task does that weaken?

**Inspect:**
1. `./demo --scenario bad-data`. Every request now gets a 500. Is that better or worse than
   before? What would you add so that the caller gets a 400, and would you then remove the
   constraints?

---

## Task 8: Inspect Everything and Compare (15 min)

**Story:** *"How would you stop that at the database level?" is the interview question. Collect
the evidence, then answer it for each rule.*

**Steps:**
1. `./demo --scenario subscription`, then `./inspect`. Read the whole output. For every line under
   CONSTRAINTS and INDEXES, name the task that added it and the bug it stops.
2. `users` has `users_email_key` and `users_email_lower_key`. Is one of them redundant? Which one
   fires when the exact same email is inserted twice (`./demo --sql`)?
3. Run the four settings strategies that are not `on-conflict` with `--sql` and count the
   statements each one sends for the request that loses.
4. A new rule arrives: "a user may have at most 3 CANCELLED subscriptions per year". Can any of the
   tools in this project enforce it? What would you use instead?
5. Another one: "an email may be reused after the account is deleted, and deleted accounts are
   kept with `deleted_at` set". Write the index.

**Acceptance Criteria** (no tests: you answer)
- [ ] For each of UNIQUE, expression index, partial index, NOT NULL, CHECK and FOREIGN KEY you can
      give the rule it enforces in this project
- [ ] You can say which rules a constraint cannot express, and what you use then
- [ ] You can describe the three correct ways to handle a constraint violation, and the one that
      does not work
- [ ] You can explain why the application check and the constraint are both kept

---

## Questions to Ask Afterwards

Answer out loud, in full sentences, before reading the answer in brackets.

- You check `existsByEmail` before you save. How can there still be duplicates? (Check-then-insert
  is two statements. Two requests can both run the check before either runs the insert, and both
  are told "not there". Each sees only committed rows, and neither has committed yet. Nothing in
  the application ties the check to the insert.)
- Does `@Transactional` fix it? (No. A transaction makes your own statements succeed or fail
  together. Under the default isolation level it does not stop another transaction from running
  the same check at the same time. In this project `checkThenInsert` is `@Transactional` and still
  races.)
- So how do you fix it? (A UNIQUE constraint. The database is the only place that sees both
  inserts, so it is the only place that can refuse the second one. The second insert waits for the
  first transaction to finish and then fails with SQLSTATE 23505.)
- If the constraint does the job, why keep the application check? (It answers almost every
  duplicate with one cheap read and a precise message, without a failed insert, an error in the
  log and a burned sequence number. It is an optimisation and a courtesy. It is not the
  guarantee.)
- How do you turn the violation into a 409? (With JPA, Spring throws
  `DataIntegrityViolationException`; its cause is Hibernate's `ConstraintViolationException`, and
  `getConstraintName()` gives the name. Match the name: `users_email_key` means "email taken",
  anything else is rethrown. With JdbcTemplate a unique violation is a `DuplicateKeyException`,
  which is a subclass. Never map every `DataIntegrityViolationException` to 409.)
- Why name your constraints? (Because the name is the only stable way to tell violations apart,
  and it shows up in logs. Without a name Postgres generates one, such as `users_email_key`, and
  your code then depends on a name nobody chose.)
- How do you make email unique regardless of case? (A unique index on `lower(email)`. A constraint
  cannot hold an expression, an index can. The lookup must use `lower(email)` as well, or it cannot
  use the index. The alternatives are the `citext` column type, or normalising the value before it
  is stored.)
- How do you enforce "one ACTIVE subscription per user"? (A partial unique index:
  `unique (user_id) where status = 'ACTIVE'`. Only ACTIVE rows are in the index, so cancelled rows
  never collide.)
- What does `INSERT ... ON CONFLICT` do? (It makes "insert unless it is there" one atomic
  statement. `DO NOTHING` skips the insert, and the row count tells you whether a row was inserted.
  `DO UPDATE` turns it into an upsert; it reports one row either way, so use
  `RETURNING (xmax = 0)` to tell an insert from an update. It needs a unique constraint or index to
  detect the conflict.)
- You catch the duplicate-key exception and run a SELECT. It fails with "current transaction is
  aborted". Why? (In Postgres any failed statement puts the transaction into an aborted state, and
  every statement after it is rejected until a rollback. Catching the Java exception does not
  change the state of the database transaction.)
- And if you catch it and run nothing else? (Worse. The method returns normally, the commit is
  quietly turned into a rollback, and everything the transaction wrote before the failure is gone
  without any error. `SettingsIT` has a test that shows it.)
- What are the correct shapes? (Catch the exception outside the transaction, so the failed
  transaction is already over. Or set a savepoint and roll back to it. Running the insert in a
  `REQUIRES_NEW` transaction works too, but it takes a second connection. Or use `ON CONFLICT` so
  that nothing fails in the first place.)
- What are CHECK, NOT NULL and FOREIGN KEY for, if the application validates? (They hold whoever
  writes: a new endpoint without validation, a batch job, a script, a second service. Validation
  gives the caller a good error message. The constraint gives everyone else a guarantee.)
- Does a UNIQUE constraint treat NULLs as equal? (No. Any number of rows may have NULL in a unique
  column. Add NOT NULL, or since Postgres 15 declare it `UNIQUE NULLS NOT DISTINCT`.)
- What happens when you add a unique constraint to a table that already has duplicates? (The
  `ALTER TABLE` fails with "could not create unique index ... Key ... is duplicated". You have to
  find and merge the duplicates first. This is why the constraint belongs in the first migration,
  not the one after the incident.)
- What can a constraint not do? (Rules that span many rows or other tables, such as "at most 3
  per year" or "the total may not exceed the limit". For those you lock a parent row or use an
  atomic update, as in `../07-locking`.)
- How is this different from an idempotency key? (Same mechanism, different purpose. An
  idempotency key is a unique constraint on a value the client sends, so that a retried request is
  recognised. See `../08-idempotency`.)

## Tips for Interviewers

- The most common wrong answer is "I check if it exists first". Ask what happens when two requests
  run that check at the same time, and wait.
- The second most common is "I put `@Transactional` on it" or "I make the method `synchronized`".
  Ask how many instances of the service run in production.
- After Task 2, ask the candidate to predict the output of `./demo --requests 20` including the
  `id` of the surviving row. A strong candidate knows the sequence does not roll back.
- Watch what they do with the exception. Catching `DataIntegrityViolationException` and returning
  409 for all of them is the bug that separates levels: a NOT NULL violation is then reported as
  "email taken".
- In Task 6, ask "what if you just catch it?" before they run `catch-inside`. Few candidates have
  seen "current transaction is aborted" explained, most have seen it in a log.
- For senior candidates, Task 8 steps 4 and 5 are the real questions: knowing where constraints
  stop, and writing a partial index for soft deletes without help.
