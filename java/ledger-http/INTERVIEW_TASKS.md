# Interview Tasks — Ledger over HTTP

Sequential user stories. Assumes a working `Ledger` (see README).

---

## Task 1: Accounts Endpoint

**Story:** *`POST /accounts {"id":"A","openingBalance":"100.00"}` returns 201.
`GET /accounts/A` returns `{"id":"A","balance":"100.00"}` or 404.*

**Acceptance Criteria:**
- Money is sent and returned as a **string**, never a JSON number
- Duplicate id is 400, unknown id is 404

---

## Task 2: Transfers Endpoint

**Story:** *`POST /transfers {"from":"A","to":"B","amount":"30.00"}` returns 200; 404 for an
unknown account, 422 for insufficient funds, 400 for a bad amount.*

**Acceptance Criteria:**
- Exceptions map to status codes in **one** place (`app.exception(...)`), not in every handler
- No money rule is duplicated in the handler; the ledger owns them

---

## Task 3: Handler Tests With Mockito

**Story:** *Unit-test the handlers with `Ledger` mocked, then one end-to-end test that starts the
app on a random port and uses `java.net.http.HttpClient`.*

**Acceptance Criteria:**
- `@ExtendWith(MockitoExtension.class)`, `@Mock Ledger`
- Stubs what the ledger returns, verifies what the handler passed to it
- The ledger's rules are not tested a second time through HTTP

**Hints:**
- `Javalin.start(0)` picks a free port; `app.port()` tells you which

---

## Task 4: Idempotent Transfers Over HTTP

**Story:** *Honour an `Idempotency-Key` header: the same key replayed returns the first response
and does not move money twice.*

**Acceptance Criteria:**
- Three identical requests with the same key move money once
- A keyed transfer that failed replays its failure, even after balances change
- The candidate can say where the key map lives in production (Postgres or Redis, with a TTL)

---

## Questions to Ask Afterwards

- Why is the ledger's own locking enough here, and when would it stop being enough? (Two
  instances behind a load balancer)
- Where does validation belong: handler, service or domain?
- What would you log, and what would you put on a dashboard for this service?
- "A user says transfers are slow." Where do you look first?

## Tips for Interviewers

- The trap is business rules leaking into handlers. Ask where "amount must be positive" lives.
