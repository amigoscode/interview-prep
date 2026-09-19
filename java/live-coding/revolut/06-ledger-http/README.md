# 06 Ledger over HTTP: the Spring-free take-home

Revolut's historical take-home, still reported on Polish forums: "a money transfer app with a
REST API, in memory, **no Spring**, must handle concurrency". Their stack is SparkJava and
jOOQ; here we use Javalin, which is the same idea in 2026. This module also gives you a
natural place to show Mockito: the HTTP handlers are tested against a mocked `Ledger`.

**Finish `01-ledger` first.** This module depends on it; with the skeleton in place every
endpoint returns 500. Optional, one evening.

```bash
./mvnw -pl 06-ledger-http -am test          # -am also builds 01-ledger
./mvnw -pl 06-ledger-http -am exec:java     # not wired; run LedgerApp.main from the IDE
```

## User stories

### Story 1: accounts endpoint

> `POST /accounts {"id":"A","openingBalance":"100.00"}` returns 201.
> `GET /accounts/A` returns `{"id":"A","balance":"100.00"}` or 404.

### Story 2: transfers endpoint

> `POST /transfers {"from":"A","to":"B","amount":"30.00"}` returns 200, 404 for an unknown
> account, 422 for insufficient funds, 400 for a bad amount.

Map exceptions to status codes in one place (`app.exception(...)`), not in every handler.

### Story 3: handler tests with Mockito

> Unit-test the handlers with `Ledger` mocked, then one end-to-end test that starts the app on
> a random port and uses `java.net.http.HttpClient`.

### Story 4: idempotent transfers over HTTP

> Honour an `Idempotency-Key` header: the same key replayed returns the first response and does
> not move money twice. Explain where that map lives in production.

## Questions the interviewer asks afterwards

- Why is the ledger's own locking enough here, and when would it stop being enough?
- Where does validation belong: handler, service or domain?
- What would you log, and what would you put on a dashboard for this service?

## Solution

`git checkout solutions/revolut/06-ledger-http`, then `./mvnw -pl 06-ledger-http -am test`.
