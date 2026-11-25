# Guided Tasks — Constraints and Races

The same eight tasks as [`INTERVIEW_TASKS.md`](INTERVIEW_TASKS.md), step by step: the command to
run, the output you should see, why it happens, and the change to make.

Commands are for macOS and Linux. On Windows type `.\demo`, `.\inspect` and `.\check`.

Timings, thread names, ids and which request wins will differ on your machine. The counts and the
`RESULT` line should match.

**Before you start**

```bash
cd start
docker compose up -d
```

**How you add a constraint in this project.** Open `src/main/resources/schema.sql`. The application
runs that file every time it starts (`./demo`, `./check`, `./mvnw verify`): it drops the tables and
creates them again. So you edit the file, run the command again, and the database has exactly what
the file says. In a real project the same line would go into a Flyway or Liquibase migration.

---

## Task 1: See the Duplicate Signup

No code in this task. You are collecting evidence.

### Run it

`./demo` prints:

```
Scenario: signup | requests: 2 | work between check and insert: 100 ms
Every request signs up ana@example.com at the same moment.

  +    0 ms  Request 1  -> POST /api/signups  ana@example.com
  +    0 ms  Request 2  -> POST /api/signups  ana@example.com
  +  425 ms  Request 2  <- 201 user #2 created
  +  425 ms  Request 1  <- 201 user #1 created

ROWS IN users
  id  email            display_name
  --  ---------------  ------------
  1   ana@example.com  Ana
  2   ana@example.com  Ana

RESULT: DUPLICATES. 2 rows in users for ana@example.com.
```

One email, two accounts. Nothing crashed and no error was logged. Both people got a 201.

### See the SQL

```bash
./demo --sql
```

```
  [exec-2] select u1_0.id from users u1_0 where u1_0.email=? fetch first ? rows only
  [exec-3] select u1_0.id from users u1_0 where u1_0.email=? fetch first ? rows only
  [exec-2] insert into users (created_at,display_name,email) values (?,?,?)
  [exec-3] insert into users (created_at,display_name,email) values (?,?,?)
```

`exec-2` and `exec-3` are the two request threads.

### What just happened

| Time | Request 1 | Request 2 | Rows for ana@example.com |
|---|---|---|---|
| 1 | is the email taken? **no** | | 0 |
| 2 | | is the email taken? **no** | 0 |
| 3 | hash the password (100 ms) | hash the password (100 ms) | 0 |
| 4 | insert | | 1 |
| 5 | | insert | 2 |

Both requests asked before either inserted, so both were told "no". Each answer was true when it
was given. It was out of date by the time it was used. This pattern is called
**check-then-insert**, or check-then-act, and the bug is a **race condition**.

### Find it in the code

Open `SignupService.signUp`:

```java
if (users.existsByEmail(email)) {                         // CHECK
    throw new EmailTakenException(email);
}
work.run("hash the password");                            // ...time passes...
return users.save(new User(email, displayName));          // INSERT
```

The CHECK and the INSERT are two separate statements, and nothing ties them together.

### Look inside the database

```bash
./inspect
```

```
CONSTRAINTS (pg_constraint)
  name        kind         definition
  ----------  -----------  ----------------
  users_pkey  PRIMARY KEY  PRIMARY KEY (id)
...
SAME EMAIL, MORE THAN ONE ROW (compared without case)
  email_lowercase  rows  user_ids  stored_as
  ---------------  ----  --------  --------------------------------
  ana@example.com  2     1, 2      ana@example.com, ana@example.com
```

The only rule the database knows about `users` is the primary key. Nobody ever told it that an
email must be unique, so it stored both rows.

### Make it worse, then try to make it go away

`./demo --requests 20` prints `RESULT: DUPLICATES. 20 rows in users for ana@example.com.`

Now run `./demo --work-ms 0` a few times. It still creates duplicates. Without the 100 ms the gap
between the check and the insert is a millisecond or less, and two requests can still land inside
it. A smaller gap makes the bug rarer, not gone.

**Would `@Transactional` help?** No. A transaction makes your own statements succeed or fail
together. It does not stop a second transaction from running the same check at the same time, and
neither of them can see the other's row before it is committed. `synchronized` does not help
either: production runs more than one instance of the application. The database is the only place
that sees both inserts, so it is the only place that can refuse one.

### Check

`./check 1` prints 1 of 1: *"two requests with the same email are both told "this email is free" by
the application's check (this documents the race)"*. That test passes on purpose, now and after
every fix. The application's check will always be able to give two requests the same answer. The
fixes decide what happens next.

---

## Task 2: Fix It With a UNIQUE Constraint and a 409

**The idea:** tell the database the rule. Then the second insert fails, whatever the application
believed.

`./check 2` prints 2 of 6 before you start.

### Step 1: add the constraint

In `src/main/resources/schema.sql`, in `create table users`, replace the Task 2 TODO line. Note the
comma that has to go after the `created_at` line:

```sql
    created_at   timestamptz not null default now(),
    constraint users_email_key unique (email)
```

Give it a name. Your Java code is about to look for that name.

### Look before you go on

Do not touch the Java yet. Run `./demo`:

```
  +  411 ms  Request 1  <- 201 user #1 created
  +  414 ms  Request 2  <- 500 (ERROR: duplicate key value violates unique constraint "users_email_key" Detail: Key (email)=(ana@example.com) already exists.)

Accounts created (201):      1
Server errors (500):         1

RESULT: SERVER ERROR. No duplicates, but 1 request got a 500 instead of a clear answer.
```

The data is already correct: one row. One line of SQL fixed the bug. What is left is manners: the
loser got a 500 with a database error in it, for something that is not a server fault.

### Step 2: find out which constraint was violated

When the database refuses the row, `users.save` throws Spring's
`DataIntegrityViolationException`. That exception is thrown for **every** kind of constraint, so
"catch it and return 409" is wrong. You need the name. The cause of Spring's exception is
Hibernate's `ConstraintViolationException`, and Hibernate has already read the name out of the
error Postgres sent.

Create `src/main/java/com/amigoscode/constraints/db/Violations.java`:

```java
package com.amigoscode.constraints.db;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

public final class Violations {

    /** True when the constraint the database refused the row for is one of {@code names}. */
    public static boolean isOn(DataIntegrityViolationException e, String... names) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                for (String name : names) {
                    if (name.equals(violation.getConstraintName())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private Violations() {
    }
}
```

Careful with the import: it is `org.hibernate.exception.ConstraintViolationException`, not the one
from `jakarta.validation`.

### Step 3: translate it

In `SignupService.signUp`, replace the last line (`return users.save(...)`) with:

```java
try {
    return users.save(new User(email, displayName));      // INSERT
} catch (DataIntegrityViolationException e) {
    if (Violations.isOn(e, "users_email_key")) {
        throw new EmailTakenException(email);
    }
    throw e;
}
```

Imports: `com.amigoscode.constraints.db.Violations` and
`org.springframework.dao.DataIntegrityViolationException`.

**Leave the `existsByEmail` check where it is.** `ApiExceptionHandler` already maps
`EmailTakenException` to a 409.

### After

`./demo`:

```
  +  411 ms  Request 1  <- 201 user #1 created
  +  415 ms  Request 2  <- 409 EMAIL_TAKEN

RESULT: OK. 1 row for ana@example.com: 1 created, 1 told the email is taken.
```

`./check 2` now prints 6 of 6.

---

## Task 3: Inspect the Constraint

### Step 1 and 2: the SQL, and what the loser waited for

```bash
./demo --sql
```

```
  [exec-2] select u1_0.id from users u1_0 where u1_0.email=? fetch first ? rows only
  [exec-3] select u1_0.id from users u1_0 where u1_0.email=? fetch first ? rows only
  [exec-3] insert into users (created_at,display_name,email) values (?,?,?)
  [exec-2] insert into users (created_at,display_name,email) values (?,?,?)
  [exec-2] HHH000247: ErrorCode: 0, SQLState: 23505
  [exec-2] ERROR: duplicate key value violates unique constraint "users_email_key"
  Detail: Key (email)=(ana@example.com) already exists.
  +  456 ms  Request 2  <- 201 user #2 created
  +  460 ms  Request 1  <- 409 EMAIL_TAKEN
```

The race is still there: two SELECTs, then two INSERTs, exactly as in Task 1. The difference is the
last three lines. The text after `ERROR:` comes from Postgres. Hibernate logs it (`HHH000247`) and
then throws. **23505** is the SQLSTATE for `unique_violation`.

The loser's INSERT was sent at the same moment as the winner's. Postgres cannot answer it at once:
the winner's row is not committed yet, and the winner might still roll back. So the second INSERT
**waits** until the first transaction ends. If it commits, the second fails. If it rolls back, the
second goes through.

Hibernate logs that error for every violation, also for the ones your code handles. (The demo
hides it unless you pass `--sql`.)

### Step 3: constraint and index

`./inspect`, the `users` part:

```
CONSTRAINTS (pg_constraint)
  name             kind         definition
  ---------------  -----------  ----------------
  users_pkey       PRIMARY KEY  PRIMARY KEY (id)
  users_email_key  UNIQUE       UNIQUE (email)

INDEXES (pg_indexes)
  name             unique  definition
  ---------------  ------  -------------
  users_email_key  yes     btree (email)
  users_pkey       yes     btree (id)
```

One line of SQL, two entries. A UNIQUE constraint is **enforced by a unique index**: to know
whether a value is already there, Postgres has to be able to look it up quickly. So you got an
index on `email` for free, and the `exists` check uses it.

### Step 4: the missing ids

`./demo --requests 20`:

```
ROWS IN users
  id  email            display_name
  --  ---------------  ------------
  3   ana@example.com  Ana
```

One row, and its id is 3 (yours can be anything from 1 to 20). All 20 requests passed the check,
all 20 took a number from the id sequence, 19 inserts failed. **A sequence does not roll back**, so
the numbers of the failed inserts are gone. Gaps in ids are normal and never a sign of deleted
rows.

### Step 5: why keep the application check?

Comment out the `if (users.existsByEmail(email)) { ... }` block and run `./check 2`. It prints 5 of
6. The one that fails is *"a signup that arrives after the first one finished is stopped by the
application's check, before any insert is tried"*.

The data is still correct: the constraint catches everything. But now **every** duplicate signup,
including the ordinary one that arrives an hour later, hashes a password, tries an insert, fails,
logs an error and burns an id. The check answers that case with one indexed read.

- The **check** is the fast path: cheap, with a precise message, right almost every time.
- The **constraint** is the guarantee: right every time.

Put the check back.

### Step 6: the hole

`./demo --scenario case`:

```
  +    0 ms  Request 1  -> POST /api/signups  Ana@Example.com
  +    0 ms  Request 2  -> POST /api/signups  ana@example.com
  +  391 ms  Request 2  <- 201 user #1 created
  +  391 ms  Request 1  <- 201 user #2 created

RESULT: DUPLICATES. 2 rows in users for the same address.
```

`unique (email)` compares the text byte for byte. To the constraint these are two different values.
That is Task 4.

### What to take away

- The losing INSERT waits for the winning transaction, then fails with SQLSTATE 23505.
- Spring: `DataIntegrityViolationException`. Hibernate, underneath: `ConstraintViolationException`
  with `getConstraintName()`. Match the name, rethrow everything else.
- Adding a UNIQUE constraint to a table that **already** has duplicates fails ("could not create
  unique index ... Key ... is duplicated"). You have to clean up first. Add the constraint with the
  table, not after the incident.

---

## Task 4: The Same Email in Different Capital Letters

**The idea:** make the *lower-cased* email unique. A constraint cannot contain a function call. A
unique **index** can.

### Step 1: add the index

In `schema.sql`, replace the Task 4 TODO line:

```sql
create unique index users_email_lower_key on users (lower(email));
```

Run `./demo --scenario case`:

```
  +  447 ms  Request 1  <- 201 user #1 created
  +  449 ms  Request 2  <- 500 (ERROR: duplicate key value violates unique constraint "users_email_lower_key" Detail: Key (lower(email))=(ana@example.com) already exists.)

RESULT: SERVER ERROR. No duplicates, but 1 request got a 500 instead of a clear answer.
```

Again the data is right and the answer is not. This is the reason for matching by name: the
violation is a new one, `users_email_lower_key`, and `SignupService` rethrows what it does not
know.

### Step 2: teach the service the second name

In `SignupService`:

```java
if (Violations.isOn(e, "users_email_key", "users_email_lower_key")) {
```

`./demo --scenario case` now prints `RESULT: OK`, and `./check 4` prints 2 of 3. The one that fails
is *"ANA@EXAMPLE.COM arriving after ana@example.com is stopped by the application's check"*: the
fast path still compares exactly, so it never fires for a different spelling.

### Step 3: make the check compare the same way as the index

In `UserRepository`, replace `existsByEmail` with:

```java
@Query("select count(u) > 0 from User u where lower(u.email) = lower(:email)")
boolean existsByEmailIgnoreCase(@Param("email") String email);
```

Imports: `org.springframework.data.jpa.repository.Query` and
`org.springframework.data.repository.query.Param`. In `SignupService`, call
`users.existsByEmailIgnoreCase(email)`.

**Why write the query by hand?** Spring Data can derive `existsByEmailIgnoreCase` from the method
name. It passes the tests too. But look at the SQL it writes:

```
select u1_0.id from users u1_0 where upper(u1_0.email)=upper(?) fetch first ? rows only
```

`upper`, not `lower`. An index on `lower(email)` can only be used by a query that says
`lower(email)`. With `upper` Postgres reads the whole table on every signup. With the hand-written
query the SQL is:

```
select count(u1_0.id)>0 from users u1_0 where lower(u1_0.email)=lower(?)
```

`./check 4` now prints 3 of 3.

### Inspect

```bash
./inspect
```

```
INDEXES (pg_indexes)
  name                   unique  definition
  ---------------------  ------  --------------------
  users_email_key        yes     btree (email)
  users_email_lower_key  yes     btree (lower(email))
  users_pkey             yes     btree (id)
```

The new rule is under INDEXES only: CONSTRAINTS still lists just the primary key and
`users_email_key`. It is not a "constraint" in Postgres's catalogue, but it enforces uniqueness in
exactly the same way, and the error message even calls it a unique constraint.

`users_email_key` is now **redundant**: any two rows it rejects, the new index rejects too. Every
index costs a little on every insert, so in production you would drop it. This project keeps both
so that you can see them side by side.

Two other ways to get the same result: the **`citext`** column type (`create extension citext`),
which compares without case, so that a plain `unique (email)` is enough. Or lower-case the email
before saving, which only protects the rows that go through your code and loses the spelling the
user typed.

---

## Task 5: One ACTIVE Subscription per User

### Before

`./demo --scenario subscription`:

```
  +  128 ms  Request 1  <- 201 subscription #1 active
  +  130 ms  Request 2  <- 201 subscription #2 active
...
RESULT: DUPLICATES. User 1 had 2 ACTIVE subscriptions and pays for each of them.
```

`SubscriptionService.subscribe` is the same shape as the signup: check, slow work, insert.

**Why not a plain UNIQUE?** `unique (user_id)` allows one subscription per user for ever: nobody
could come back after cancelling. `unique (user_id, status)` allows one ACTIVE and one CANCELLED:
the second cancellation would fail. The rule is about **some of the rows only**.

### Step 1: a partial unique index

In `schema.sql`, replace the Task 5 TODO line:

```sql
create unique index subscriptions_one_active_per_user on subscriptions (user_id) where status = 'ACTIVE';
```

The `where` makes it a **partial index**: only rows with `status = 'ACTIVE'` are in it. Among those,
`user_id` must be unique. A cancelled row is not in the index, so it cannot collide with anything.

### Step 2: translate it

In `SubscriptionService.subscribe`, replace the last line with:

```java
try {
    return subscriptions.save(new Subscription(userId, plan, seats, priceCents));   // INSERT
} catch (DataIntegrityViolationException e) {
    if (Violations.isOn(e, "subscriptions_one_active_per_user")) {
        throw new AlreadySubscribedException(userId);
    }
    throw e;
}
```

Same two imports as in Task 2.

### After

`./demo --scenario subscription`:

```
  +  286 ms  Request 2  <- 201 subscription #1 active
  +  289 ms  Request 1  <- 409 ALREADY_SUBSCRIBED

Then, one after another: cancel what is active, and subscribe again.

  POST /api/subscriptions/1/cancel  <- 200 subscription #1 cancelled
  POST /api/subscriptions  <- 201 subscription #3 active

ROWS IN subscriptions
  id  user_id  plan   status
  --  -------  -----  ---------
  1   1        PRO    CANCELLED
  3   1        BASIC  ACTIVE

RESULT: OK. Never more than one ACTIVE subscription, and cancelled rows may repeat.
```

`./check 5` prints 6 of 6. (Id 2 is missing: it was used by the insert that failed.)

### Inspect

`./inspect`, the `subscriptions` part:

```
INDEXES (pg_indexes)
  name                               unique  definition
  ---------------------------------  ------  -----------------------------------------------
  subscriptions_one_active_per_user  yes     btree (user_id) WHERE (status = 'ACTIVE'::text)
  subscriptions_pkey                 yes     btree (id)
```

Now remove ` where status = 'ACTIVE'` from the index and run the demo again:

```
  POST /api/subscriptions/1/cancel  <- 200 subscription #1 cancelled
  POST /api/subscriptions  <- 409 ALREADY_SUBSCRIBED

RESULT: TOO STRICT. The user cancelled and could not subscribe again.
```

Put the `where` back. A constraint that is too strict is a bug too.

The same trick solves soft deletes: `unique (email) where deleted_at is null` lets a deleted
account's email be used again.

---

## Task 6: Create If Absent, and the Aborted Transaction

`user_settings` has `user_id` as its primary key, so the database already refuses a second row for
one user. This task is about the application's side of that refusal.

### Step 1: the bug

```bash
./demo --scenario settings --strategy check-first
```

```
  +  192 ms  Request 1  <- 500 (ERROR: duplicate key value violates unique constraint "user_settings_pkey" Detail: Key (user_id)=(1) already exists.)
  +  198 ms  Request 2  <- 200 theme=light created=true

RESULT: SERVER ERROR. 1 request got a 500. The primary key kept the data clean (1 row), the application did not cope with the refusal.
```

`SettingsStore.checkThenInsert` is `@Transactional`, and it races all the same.

### Step 2: the obvious fix, and the trap

"Just insert, and if it is already there, catch the exception and read the row." That is
`SettingsStore.insertAndCatchInside`: a `try` around the INSERT, and a SELECT in the
`catch (DuplicateKeyException e)` block, all in one `@Transactional` method. Read it, then run it:

```bash
./demo --scenario settings --strategy catch-inside --sql
```

```
  [exec-2] Executing prepared SQL statement [insert into user_settings (user_id) values (?)]
  [exec-3] Executing prepared SQL statement [insert into user_settings (user_id) values (?)]
  [exec-2] Executing prepared SQL statement [select user_id, theme from user_settings where user_id = ?]
  [exec-3] Executing prepared SQL statement [select user_id, theme from user_settings where user_id = ?]
  +  115 ms  Request 2  <- 500 (ERROR: current transaction is aborted, commands ignored until end of transaction block)
  +  117 ms  Request 1  <- 200 theme=light created=true

  PUT /api/users/1/settings   (again, later)  <- 500 (ERROR: current transaction is aborted, commands ignored until end of transaction block)

RESULT: SERVER ERROR. 2 requests got a 500. ...
```

The exception **was** caught. The SELECT in the catch block is what failed. And it is worse than
before: the request that arrives later, with no race at all, fails too. This endpoint now works
exactly once per user.

**Why.** In Postgres, when a statement fails inside a transaction, the whole transaction is
**aborted**. Every statement after that is refused with *"current transaction is aborted, commands
ignored until end of transaction block"* (SQLSTATE 25P02) until the transaction is rolled back.
Catching the Java exception changes nothing about the state of the transaction in the database.

**The quiet version.** If the catch block does *not* touch the database and the method simply
returns, there is no error at all. Spring sends COMMIT, Postgres rolls back instead, and everything
the transaction wrote before the failure is gone. `SettingsIT` has a test for it, and it passes:

```
  [PASS] swallow: a write, then a caught duplicate key, then a normal return: the commit reports nothing and the write is gone (this documents the trap)
```

(With JdbcTemplate nothing warns you, as here. When the failed call was a JPA repository method,
Spring notices and the commit throws `UnexpectedRollbackException`. Either way the work is lost:
never carry on in a transaction after a failed statement.)

### Step 3: the three shapes that work

**1. Catch it outside the transaction** (`--strategy catch-outside`). `SettingsService` is not
transactional. It calls `store.insertDefaults`, which is one short transaction. When that fails,
the transaction is rolled back and finished before the exception reaches the `catch`, and the read
that follows starts a new one. This is also what you built in Tasks 2 and 5: `SignupService` is
deliberately not `@Transactional`.

**2. A savepoint** (`--strategy savepoint --sql`):

```
  [exec-3] Executing SQL statement [savepoint before_insert]
  [exec-3] Executing prepared SQL statement [insert into user_settings (user_id) values (?)]
  [exec-3] Executing SQL statement [rollback to savepoint before_insert]
  +   42 ms  Request 1  <- 200 theme=light created=false
```

`rollback to savepoint` undoes the failed insert only and makes the transaction usable again.
Running the insert in its own `REQUIRES_NEW` transaction works as well, at the price of a second
database connection for the duration.

**3. Do not fail at all.** That is the one you write.

### Step 4: ON CONFLICT DO NOTHING

In `SettingsStore`, replace the body of `insertOnConflictDoNothing`:

```java
@Transactional
public Settings insertOnConflictDoNothing(long userId) {
    int inserted = jdbc.update(
            "insert into user_settings (user_id) values (?) on conflict (user_id) do nothing", userId);
    return read(userId, inserted == 1);
}
```

`on conflict (user_id) do nothing` means: if a row with this `user_id` is already there, skip the
insert and report success. The check and the insert are one statement, so there is no gap between
them. `jdbc.update` returns the number of rows inserted: **1** means this call created the row,
**0** means it was already there.

`./demo --scenario settings`:

```
  +  106 ms  Request 1  <- 200 theme=light created=false
  +  106 ms  Request 2  <- 200 theme=light created=true
  PUT /api/users/1/settings   (again, later)  <- 200 theme=light created=false

RESULT: OK. 1 row, every request was answered, exactly 1 was told it created the row.
```

Two things to know about `DO NOTHING`:

- It needs a unique constraint or index on the column in the brackets. It is built on the
  constraint, it does not replace it.
- `insert ... on conflict do nothing returning *` returns **no row** when the insert was skipped.
  That is why the method does a separate SELECT.

### Step 5: ON CONFLICT DO UPDATE

"Save the theme, whether or not the row exists" is the other half: an **upsert**. As shipped,
`saveTheme` runs an UPDATE and inserts when it changed 0 rows. Two requests can both change 0 rows
(`./demo --scenario theme`):

```
  +  211 ms  Request 1  <- 200 theme=dark created=true
  +  213 ms  Request 2  <- 500 (ERROR: duplicate key value violates unique constraint "user_settings_pkey" Detail: Key (user_id)=(1) already exists.)
```

Replace the body of `saveTheme`:

```java
@Transactional
public Settings saveTheme(long userId, String theme) {
    return jdbc.queryForObject(
            "insert into user_settings (user_id, theme) values (?, ?) "
                    + "on conflict (user_id) do update set theme = excluded.theme, updated_at = now() "
                    + "returning user_id, theme, (xmax = 0) as created",
            (row, n) -> new Settings(row.getLong("user_id"), row.getString("theme"), row.getBoolean("created")),
            userId, theme);
}
```

- `excluded` is the row you tried to insert. `set theme = excluded.theme` means "use the new value".
- An upsert affects 1 row whether it inserted or updated, so the row count cannot tell you which.
- `returning (xmax = 0)` can. `xmax` is a hidden column on every Postgres row: it is 0 on a row
  that was just inserted, and not 0 on a row this statement updated.

Now `./demo --scenario theme` answers both with 200, one `created=true` and one `created=false`,
and prints `RESULT: OK`. `./check 6` prints 9 of 9.

---

## Task 7: Bad Data: NOT NULL, CHECK and FOREIGN KEY

Not every bad row comes from a race. Some come from an application that simply forgot to look.

### Before

```bash
./demo --scenario bad-data
```

```
  signup without an email               <- 201 user #3 created
  subscribe with seats = -3             <- 201 subscription #1 active
  subscribe, coupon bigger than price   <- 201 subscription #2 active
  subscribe a user that does not exist  <- 201 subscription #3 active

BAD ROWS IN THE DATABASE
  table          id  problem
  -------------  --  --------------------------
  users          3   email is NULL
  subscriptions  1   seats = -3
  subscriptions  2   price_cents = -4100
  subscriptions  3   user 999999 does not exist

RESULT: BAD DATA. 4 of 4 bad requests are now rows in the database.
```

The negative price was not even sent by the caller: the service computed `price - coupon` and
stored whatever came out.

### The change

Only `schema.sql`. In `users`, make the email mandatory and delete the Task 7 TODO line:

```sql
    email        text        not null,
```

In `subscriptions`, change three lines and delete the Task 7 TODO line:

```sql
    user_id     bigint      not null constraint subscriptions_user_id_fkey references users (id),
    ...
    seats       int         not null constraint subscriptions_seats_check check (seats > 0),
    price_cents int         not null constraint subscriptions_price_cents_check check (price_cents >= 0),
```

| Rule | Tool | Here |
|---|---|---|
| The value must be there | `NOT NULL` | `users.email` |
| The value must make sense on its own | `CHECK` | `seats > 0`, `price_cents >= 0` |
| The value must point at a row that exists | `FOREIGN KEY` | `subscriptions.user_id` references `users (id)` |

`NOT NULL` on `email` also closes a hole in Task 2: a UNIQUE constraint accepts **any number of
NULLs**, because NULL is never equal to NULL.

### After

```bash
./demo --scenario bad-data
```

```
  signup without an email               <- 500 (ERROR: null value in column "email" of relation "users" violates not-null constraint)
  subscribe with seats = -3             <- 500 (ERROR: new row for relation "subscriptions" violates check constraint "subscriptions_seats_check")
  subscribe, coupon bigger than price   <- 500 (ERROR: new row for relation "subscriptions" violates check constraint "subscriptions_price_cents_check")
  subscribe a user that does not exist  <- 500 (ERROR: insert or update on table "subscriptions" violates foreign key constraint "subscriptions_user_id_fkey" Detail: Key (user_id)=(999999) is not present in table "users".)

BAD ROWS IN THE DATABASE
  none

RESULT: OK. The database refused all 4. Nothing bad was stored.
```

`./check` should now print 35 passes and no failures.

### Inspect: four 500s, and the result is OK?

Yes. A 500 and no row is better than a 201 and a subscription with a negative price. The 500 is
also honest: the application had a hole, and the database covered for it.

The proper finish is to **add** validation in the application, so that the caller gets a 400 with a
useful message, and to **keep** the constraints. Validation serves the caller in front of you. The
constraint also holds for the endpoint somebody adds next year, the batch job, and the script run
by hand at 2 a.m. The foreign key works in the other direction too: it refuses to delete a user who
still has subscriptions.

---

## Task 8: Inspect Everything and Compare

### Step 1: read the whole database

```bash
./demo --scenario subscription
./inspect
```

Read all of it. Every line under CONSTRAINTS and INDEXES is a sentence the database will enforce
whoever is writing: 2 on `users` that you added, 4 on `subscriptions`. Name the task and the bug
for each one. (NOT NULL is in the COLUMNS part.)

### Step 2: which of the two email rules fires?

`./demo --sql` (same spelling twice) reports `users_email_key`. `./demo --scenario case --sql`
reports `users_email_lower_key`. The service treats both as "email taken", which is why it does not
matter that one of them is redundant.

### Step 3: what the loser costs

| Strategy | Statements for a request that finds the row already there | Works? |
|---|---|---|
| check-first | 2 SELECTs (or a failed INSERT when it loses a race) | No: 500 under a race |
| catch-inside | failed INSERT, failed SELECT | No: 500 on every call after the first |
| catch-outside | failed INSERT, rollback, then a SELECT in a new transaction | Yes |
| savepoint | SAVEPOINT, failed INSERT, ROLLBACK TO SAVEPOINT, SELECT | Yes |
| on-conflict | INSERT ... ON CONFLICT DO NOTHING, SELECT | Yes, and nothing fails |

### Step 4 and 5: where constraints stop

"At most 3 CANCELLED subscriptions per year" is a rule about a **count over many rows**. No
UNIQUE, CHECK or foreign key can say that: a CHECK only sees the row being written. For rules like
that you serialise the writers yourself, for example by locking the user row first, which is what
[`../07-locking`](../07-locking) is about.

"An email may be reused after the account is deleted" is the partial index again:

```sql
create unique index users_email_active_key on users (lower(email)) where deleted_at is null;
```

### Which tool for which rule

| The rule | The tool | Violation (SQLSTATE) |
|---|---|---|
| No two rows with the same value | `UNIQUE` constraint | 23505 |
| The same, on a computed value | unique index on an expression | 23505 |
| The same, for some of the rows only | partial unique index (`WHERE`) | 23505 |
| A value must be present | `NOT NULL` | 23502 |
| A value must make sense on its own | `CHECK` | 23514 |
| A row must point at a row that exists | `FOREIGN KEY` | 23503 |
| Create it unless it is there | `INSERT ... ON CONFLICT DO NOTHING` | none: it does not fail |
| Create it or update it | `INSERT ... ON CONFLICT DO UPDATE` | none: it does not fail |
| A rule over many rows | not a constraint: lock, or one atomic statement | |

A unique key that the *client* sends, so that a retried request is recognised, is the same
mechanism with a different purpose: see [`../08-idempotency`](../08-idempotency).

### How to say it in an interview

> "Checking whether a row exists and then inserting it is two statements, so two requests can both
> pass the check. The application cannot close that gap, because the requests may run on different
> servers. So I put the rule in the database: a unique constraint, or a unique index on
> `lower(email)` if case should not matter, or a partial unique index if the rule only covers some
> rows, like one active subscription per user. I keep the application check as a fast path with a
> good error message, and I translate the constraint violation into a 409 by matching the
> constraint name, never by catching every data integrity exception. In Postgres a failed
> statement aborts the transaction, so I handle the violation outside the transaction, or I use
> `INSERT ... ON CONFLICT` so that nothing fails. And for plain bad data I add NOT NULL, CHECK and
> foreign keys, because validation in the application only protects the code paths that remember
> to call it."

Then be ready for the follow-ups in "Questions to Ask Afterwards" at the end of
[`INTERVIEW_TASKS.md`](INTERVIEW_TASKS.md).

---

## Clean up

```bash
docker compose down
```
