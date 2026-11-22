# Interview Tasks — REST Semantics and Error Handling

> **In `start/`, `./check` FAILS on purpose: 13 of 56 checks pass.** Tasks 2, 4, 5, 7, 8 and 10 turn
> the other 43 green. Tasks 1, 3, 6, 9 and 11 are inspection tasks: you run things, look, and answer
> questions.

Do the tasks in order: later tasks build on earlier ones. Every fix is followed by an inspection
task (two small fixes sometimes share one): a green test tells you the fix works, the inspection
tells you **why**, and the why is what an interviewer asks for.

Stuck, or want the explanation? The same task number in [`TASKS_GUIDED.md`](TASKS_GUIDED.md) walks
you through it with the expected output.

Commands are for macOS and Linux. On Windows type `.\demo`, `.\inspect` and `.\check`.

**Rules:** work in `start/`. Never edit, delete or disable a test. Do not change `OrderRepository`,
the constructors of `OrderService` and `OrderController`, or the class name `ApiExceptionHandler`:
the unit tests build the application by hand from exactly those.

---

## Task 1: See a GET Cancel Three Orders (10 min)

**Story:** *Monday morning. Three pending orders were cancelled over the weekend. The customers did
not cancel them and no member of staff did either. The only thing that ran was the weekly link
checker, which looks for broken links on the admin pages. Before you change anything, reproduce it.*

**Steps:**
1. `docker compose up -d`, then `./demo`. How many requests did the link checker send? Which HTTP
   method? How many orders changed?
2. Read the three responses. What status code did the link checker get? From its point of view, did
   anything go wrong?
3. `./inspect --endpoints`. Find the row for the cancel endpoint. How does the tool know that this
   GET changes data?
4. Open `OrderController` and `AdminPageController`. Find the one annotation and the one HTML tag
   that together caused this.
5. Name three other programs that request a URL without a person clicking it.

**Acceptance Criteria** (no tests: you answer)
- [ ] You can say what a *safe* HTTP method is, and which methods are safe
- [ ] You can explain why the link checker did nothing wrong
- [ ] You can name three kinds of software that send GET requests on their own
- [ ] You can say what "it needs a login" does and does not fix here

**Hints:**
- The link checker follows the HTTP rules. Which side broke them?
- A logged-in member of staff has a browser. Browsers prefetch links too

---

## Task 2: Cancel With a POST (10 min)

**Story:** *Reading must never change anything. Make cancelling an order something only a deliberate
request can do, and make sure the admin page no longer offers it as a link.*

**Acceptance Criteria** (`./check 2`)
- [ ] `GET /api/orders/1/cancel` is refused and the order stays PENDING
      — `CancelIT.getDoesNotCancel`
- [ ] `POST /api/orders/1/cancel` returns 200 and the order is CANCELLED
      — `CancelIT.postCancelsTheOrder`
- [ ] The same POST sent twice returns 200 both times and changes nothing the second time
      — `CancelIT.cancellingTwiceIsSafe`
- [ ] Requesting every link on the admin page changes no order
      — `CancelIT.followingEveryLinkChangesNothing`
- [ ] The admin page cancels with a form that POSTs, not with a link
      — `CancelIT.adminPageUsesAPostForm`
- [ ] `./demo` prints `RESULT: OK`
- [ ] The baseline tests still pass (`./check`)

**Hints:**
- One annotation in `OrderController`
- In HTML, `<a href>` always sends a GET. What sends a POST?
- After your fix the old GET URL answers 500, not 405. That is the next problem, not this one

---

## Task 3: Inspect — Safe and Idempotent (15 min)

**Story:** *The fix is one word. The interview question is the reasoning behind it.*

**Steps:**
1. `./inspect --endpoints`. Which finding has gone? Three rows say "safe". What exactly was tested?
2. `./demo`. The page is requested again. What can the link checker follow now? What does it get
   for the old URLs?
3. `./demo --scenario cancel`. The same cancel is sent twice. What is the second status code? What
   changed in the database the second time?
4. POST is not idempotent by definition. Is *this* POST idempotent? Who made it so, HTTP or the
   code? Find the line.
5. Fill in this table from memory, then check it against the guided file:

   | Method | Safe? | Idempotent? |
   |---|---|---|
   | GET | | |
   | HEAD | | |
   | PUT | | |
   | DELETE | | |
   | POST | | |
   | PATCH | | |

6. Cancel could also have been `DELETE /api/orders/1`, or `PATCH /api/orders/1` with
   `{"status": "CANCELLED"}`, or a `PUT`. Give one reason against each.
7. A proxy does not get an answer in time. Which requests may it send again without asking anyone?

**Acceptance Criteria** (no tests: you answer)
- [ ] You can define *safe* and *idempotent* in one sentence each
- [ ] You can fill in the table without looking
- [ ] You can explain why an idempotent request may still return a different status code the second
      time
- [ ] You can justify `POST /orders/{id}/cancel` against DELETE, PATCH and PUT

---

## Task 4: The Right Status Code for Every Error (25 min)

**Story:** *The mobile team is angry. Whatever they do wrong, the API answers 500 "Internal Server
Error", so their app shows "the server is down, try again later" for a typo in an order id. The
on-call engineer is angry too: every one of those 500s pages her. And the security team has found
class names and stack frames in the responses. Fix `ApiExceptionHandler` and what throws into it.*

**Acceptance Criteria** (`./check 4`)
- [ ] `GET /api/orders/999` returns 404 as `application/problem+json`, with `title`, `status` and a
      `detail` that names the order — `ErrorHandlingIT.missingOrderIsNotFound`,
      `ErrorMappingTest.unknownOrderIsNotFound`
- [ ] A body that is not JSON returns 400 and nothing is saved
      — `ErrorHandlingIT.brokenJsonIsBadRequest`, `ErrorMappingTest.brokenJsonIsBadRequest`
- [ ] A product that does not exist (`"PLAYSTATION"`) returns 400
      — `ErrorHandlingIT.unknownProductIsBadRequest`
- [ ] `GET /api/orders?status=DONE` returns 400 — `ErrorHandlingIT.unknownStatusFilterIsBadRequest`
- [ ] Cancelling a shipped order returns 409 and the order stays SHIPPED
      — `ErrorHandlingIT.cancellingShippedIsConflict`, `ErrorMappingTest.cancellingShippedIsConflict`
- [ ] `PUT /api/orders/999` returns 404 and creates nothing
      — `ErrorHandlingIT.putToMissingOrderIsNotFound`
- [ ] `DELETE /api/orders/999` returns 404 — `ErrorHandlingIT.deleteOfMissingOrderIsNotFound`
- [ ] `DELETE /api/orders` returns 405 with an `Allow` header
      — `ErrorHandlingIT.wrongMethodIsMethodNotAllowed`
- [ ] No error body shows a class name, a stack trace or SQL
      — `ErrorHandlingIT.errorBodiesDoNotLeak`
- [ ] `./demo --scenario errors` prints `RESULT: OK`

**Hints:**
- Spring already knows that a body it cannot read is a 400 and a wrong method is a 405. What is
  stopping it from saying so?
- `ResponseEntityExceptionHandler` is a base class made for exactly this
- `ProblemDetail.forStatusAndDetail(...)`. An `@ExceptionHandler` method may return a
  `ProblemDetail` directly
- `orElseThrow()` with no argument throws `NoSuchElementException`. Throw something that says what
  happened: an exception of your own
- Do not map `IllegalStateException` to 409 for the whole application. Why not?
- A `@RequestParam` can be an enum. Spring converts it, and knows what to answer when it cannot
- Keep one handler for `Exception`: a real bug must still become a 500. What should its body say,
  and where should the details go?

---

## Task 5: Validation That Actually Runs (15 min)

**Story:** *The warehouse has an order for -5 keyboards with a total of -250.00, for a customer with
no name. `OrderRequest` clearly says `@NotBlank` and `@Positive`. Find out why nobody checked, fix
it, and tell the client exactly which fields are wrong.*

**Acceptance Criteria** (`./check 5`)
- [ ] An order for -5 keyboards returns 400 and nothing is saved
      — `ValidationIT.negativeQuantityIsNotSaved`
- [ ] The 400 is `application/problem+json`, and `errors` lists **every** invalid field as
      `{"field": ..., "message": ...}` — `ValidationIT.everyInvalidFieldIsListed`
- [ ] The 400 does not show class names or the rejected object
      — `ValidationIT.validationErrorDoesNotLeak`
- [ ] A blank customer, a quantity of 0 or less and a missing product are each rejected, and the
      field is named — `ValidationTest.blankCustomerIsRejected`,
      `ValidationTest.quantityMustBePositive`, `ValidationTest.productIsRequired`
- [ ] A valid order is still accepted
      — `ValidationTest.validOrderIsAccepted` (already passing: keep it that way)
- [ ] `./demo --scenario validation` prints `RESULT: OK`

**Hints:**
- An annotation on a field is only a note. Something has to be told to read it
- After the one-word fix, run the demo before you write anything else. What is missing from the 400?
- `ResponseEntityExceptionHandler` has a method per Spring exception that you can override. The one
  you want is `handleMethodArgumentNotValid`
- `ProblemDetail.setProperty("errors", ...)` adds a field of your own to the body
- Sort the errors by field name, so the answer is the same every time

---

## Task 6: Inspect — The Error Responses (15 min)

**Story:** *Two fixes, one result: every mistake gets an honest answer in one format. Look at what
you built.*

**Steps:**
1. `./demo --scenario errors`. Compare the five bodies with what you saw before Task 4. Which fields
   does every body have? What is the `Content-Type`?
2. `./inspect --probes`. Rows 2, 3, 4 and 6 are right and you wrote no code for them. Who answered?
3. `./inspect --probes --bodies`. Read bodies 5 and 7. The `detail` does not say which products
   exist. Before Task 4 it did. Which is better for the client? Which is safer? What would you do?
4. `./demo --scenario validation`. Two fields are wrong and both are listed. How many requests would
   a client need to fix a form with three wrong fields if the API reported one at a time?
5. In `ApiExceptionHandler`, delete `extends ResponseEntityExceptionHandler` (and the `@Override`
   method from Task 5, which no longer compiles). Run `./inspect --probes`. What happened to rows 2
   to 10, and why? **Put it back.**
6. Pick the status code: (a) the body is not JSON, (b) the JSON is fine but the quantity is -5,
   (c) the order does not exist, (d) the order exists but has been shipped. Some teams answer (b)
   with 422. What is the argument?
7. Who has to do something about a 4xx? About a 5xx? Which of the two should wake somebody up?

**Acceptance Criteria** (no tests: you answer)
- [ ] You can name the five standard fields of a `ProblemDetail` and say which RFC defines it
- [ ] You can explain what `ResponseEntityExceptionHandler` does for you
- [ ] You can explain the danger of `@ExceptionHandler(Exception.class)` and why you still keep one
- [ ] You can say when to use 400, 404, 409 and 422
- [ ] You can say what belongs in an error body and what belongs only in the log

---

## Task 7: Request and Response DTOs (25 min)

**Story:** *A customer found out that sending `"status": "PAID"` and `"total": 0.01` in a PUT works.
Their order for a 200.00 monitor is now marked as paid for one cent. Separately, a partner who reads
orders through the API has been receiving our internal notes about customers. Both have the same
cause: the JPA entity is the API.*

**Acceptance Criteria** (`./check 7`)
- [ ] A PUT with `"status": "PAID"` and `"total": 0.01` returns 200 and leaves status and total
      alone — `MassAssignmentIT.putCannotSetStatusOrTotal`,
      `DtoTest.putIgnoresWhatTheClientMayNotSet`
- [ ] A PUT can neither write nor wipe the internal note
      — `MassAssignmentIT.putCannotTouchTheInternalNote`
- [ ] A PUT keeps the status the order had: a PAID order stays PAID
      — `MassAssignmentIT.putKeepsTheStatus`
- [ ] An `id` in the body is ignored: the URL says which order changes
      — `MassAssignmentIT.idInTheBodyIsIgnored` (already passing: keep it that way)
- [ ] No response shows `internalNote`: not GET one, not GET all, not the answer to POST or PUT
      — `MassAssignmentIT.responsesDoNotShowTheInternalNote`,
      `MassAssignmentIT.writeResponsesDoNotShowTheInternalNote`, `DtoTest.responseHasNoInternalNote`
- [ ] PUT is validated like POST — `MassAssignmentIT.putIsValidated`
- [ ] POST still ignores `status` and `total`
      — `MassAssignmentIT.postCannotSetStatusOrTotal` (already passing: keep it that way)
- [ ] `./demo --scenario mass-assignment` prints `RESULT: OK`

**Hints:**
- `OrderRequest` already lists exactly what a client may set. Why does POST not have this problem?
- For the way out, write an `OrderResponse` record with a static `from(Order)` method
- Do not fix this with `@JsonIgnore` on the entity. What happens when the next internal column is
  added?
- A PUT must change an order that exists: load it, copy the client's fields onto it, save it. What
  happens to the fields you do not copy?
- The total is the server's job. Work it out again after the copy

---

## Task 8: PUT Replaces, PATCH Changes (20 min)

**Story:** *Liam's order had a note for the courier, "Gift. No invoice in the box", and his phone
number. He changed his delivery address in the old version of the mobile app. The invoice went in
the box and the courier could not call him. The old app does not know about notes or phone numbers,
so its "change address" screen sends a PUT without them. Give clients a way to change one thing.*

**Acceptance Criteria** (`./check 8`)
- [ ] `PATCH /api/orders/4` with only `shippingAddress` returns 200, changes the address and nothing
      else — `PutPatchIT.patchChangesOnlyTheAddress`, `PatchTest.onlyWhatIsSentChanges`
- [ ] A new quantity changes the total — `PatchTest.quantityChangesTheTotal`
- [ ] `status`, `total` and `internalNote` cannot be changed with a PATCH
      — `PutPatchIT.patchCannotSetProtectedFields`
- [ ] A field that is sent must be valid: a blank customer or a quantity of 0 returns 400 that names
      the field, and nothing changes — `PutPatchIT.patchIsValidated`,
      `PatchTest.whatIsSentMustBeValid`
- [ ] A PATCH to an order that does not exist returns 404
      — `PutPatchIT.patchOfMissingOrderIsNotFound`
- [ ] PUT still replaces the whole order: a field left out is cleared
      — `PutPatchIT.putClearsWhatIsLeftOut` (already passing: keep it that way)
- [ ] `./demo --scenario patch` prints `RESULT: OK`

**Hints:**
- Run `./demo --scenario put` first. Is the PUT wrong?
- A new record for the body, in which every field may be missing. Which Java types can be "missing"?
  `int` cannot
- `@NotBlank` rejects null. You need "may be missing, but not blank when present"
- In the service: for each field, copy it only when it was sent

---

## Task 9: Inspect — What Goes In and What Comes Out (15 min)

**Story:** *Two fixes about the shape of the data. Check what a client can do now.*

**Steps:**
1. `./inspect --endpoints`. Which findings are gone? What is left?
2. `./demo --scenario mass-assignment`. The client sent three fields it may not set. What status
   code did it get? Was it told? What is the argument for a 400 here, and the argument against?
3. `./demo --scenario put`. Three columns changed and the client wanted one. Is that a bug in the
   server? Whose mistake is it?
4. `./demo --scenario patch`. Same request, different method. How many columns changed?
5. `./demo --scenario patch-null`. The client sends `{"notes": null}` to clear the note. What
   happened? Why? How could a client clear the note with this API?
6. Send the PUT of step 3 twice in your head. What is the state after the second one? Is PUT
   idempotent? Is this PATCH? Is a PATCH that says "add 1 to the quantity"?
7. Fill in: which fields are in `Order`, in `OrderRequest`, in `OrderResponse`? Which fields are in
   exactly one of them, and why?

**Acceptance Criteria** (no tests: you answer)
- [ ] You can explain mass assignment and how a request DTO prevents it
- [ ] You can explain why a response DTO is safer than `@JsonIgnore`
- [ ] You can say what PUT promises and what PATCH promises
- [ ] You can explain why this PATCH cannot clear a field, and name a way to allow it
- [ ] You can say which of PUT and PATCH is idempotent

---

## Task 10: 201, Location and 204 (15 min)

**Story:** *Everything works and the status lines still say the wrong thing. The client creates an
order and gets "200 OK", then has to dig the id out of the body to build the URL itself. It deletes
an order and gets "200 OK" with a body somebody made up.*

**Acceptance Criteria** (`./check 10`)
- [ ] `POST /api/orders` returns 201 Created, with the new order in the body
      — `StatusCodesIT.createReturnsCreated`, `CreatedTest.createAnswersCreatedWithLocation`
- [ ] The 201 has a `Location` header, and a GET on it returns the new order
      — `StatusCodesIT.locationPointsAtTheNewOrder`
- [ ] `DELETE /api/orders/6` returns 204 with no body, and the order is gone
      — `StatusCodesIT.deleteReturnsNoContent`
- [ ] Deleting the same order twice returns 204, then 404, and no other order is deleted
      — `StatusCodesIT.deletingTwice`
- [ ] `./demo --scenario status-codes` prints `RESULT: OK`
- [ ] `./check` prints 56 passes and no failures

**Hints:**
- `ResponseEntity.created(uri).body(...)`
- `ServletUriComponentsBuilder.fromCurrentRequest()` builds the URL of the request you are in
- A method that returns `void` with `@ResponseStatus(HttpStatus.NO_CONTENT)`
- The second delete already answers 404 since Task 4. Is that what you want? Decide, and be able to
  defend it

---

## Task 11: The Status-Code Map (15 min)

**Story:** *"Which status codes does your API return, and when?" Draw the map, then say what it buys
the people who call you.*

**Steps:**
1. `./inspect`. All 18 probes should be right and the endpoint table should have no findings.
2. From that output, write down one line per status code this API uses: the code, its name, and
   when this API returns it. There are nine.
3. For each endpoint, list every status code it can return.
4. Before Task 4, an error body was `{"error", "message", "trace"}`, a Spring default was
   `{"timestamp", "status", "error", "path"}`, and the delete answered `{"message"}`. Three shapes.
   Now there is one. Describe the error-handling code a client has to write in each world.
5. The second DELETE returns 404. Argue for 404, then argue for 204. Which would you ship?
6. A retry after a timeout: which of this API's endpoints are safe to send again? What would make
   `POST /api/orders` safe to send again?
7. You need to rename `shippingAddress` to `address`. Which clients break? How would you version the
   API, and how many versions would you keep alive?

**Acceptance Criteria** (no tests: you answer)
- [ ] You can draw the status-code map of this API from memory
- [ ] You can say what a client gains from one error format
- [ ] You can defend your answer for the second DELETE
- [ ] You can give the 90-second answer to "how do you handle errors in a Spring REST API?"

---

## Questions to Ask Afterwards

Answer out loud, in full sentences, before reading the answer in brackets.

- Cancelling an order is a GET. Is that a problem? (Yes. GET is a safe method: it must only read.
  Everything on the web relies on that. Browsers prefetch links, crawlers and link checkers follow
  them, chat apps fetch them to build a preview, caches and proxies repeat them. All of those would
  cancel orders. A login does not help: the logged-in user's own browser sends the request, cookie
  included, which also makes it an easy CSRF target. Changing state needs POST, PUT, PATCH or
  DELETE.)
- Which methods are safe, and which are idempotent? (Safe means the request does not change state:
  GET, HEAD, OPTIONS. Idempotent means sending it N times leaves the server in the same state as
  sending it once: all the safe ones, plus PUT and DELETE. POST and PATCH are neither by definition.
  Idempotent is about the state on the server, not about the response: a second DELETE may well
  answer 404.)
- PUT vs POST? (POST to a collection means "create something here, you choose the URL": two POSTs
  make two orders, so it is not idempotent. PUT means "store this representation at exactly this
  URL": the client names the resource, the body replaces it completely, and doing it twice gives the
  same result. So PUT is idempotent and POST is not. POST is also the catch-all for actions that fit
  no other method, such as `/orders/1/cancel`.)
- PUT vs PATCH? (PUT replaces the whole resource: a field you leave out is cleared. PATCH changes
  only what you send. Use PUT when the client holds the full representation, PATCH for "change this
  one thing". PATCH is not idempotent by definition, although a PATCH that sets fields to given
  values happens to be.)
- What does `@RequestBody` do, and with which methods do you use it? (It tells Spring to read the
  body of the request and convert it, with Jackson for JSON, into the parameter's type. You use it
  where a body carries a representation: POST, PUT, PATCH. GET and DELETE identify things by the
  URL: `@PathVariable` and `@RequestParam`. If the body cannot be converted, Spring throws
  `HttpMessageNotReadableException`, which is a 400. `@RequestBody` does not validate: that needs
  `@Valid` next to it.)
- How do you handle errors in a Spring REST API? (One `@RestControllerAdvice` class. It extends
  `ResponseEntityExceptionHandler`, so Spring's own exceptions keep their correct codes: 400, 404,
  405, 415. I add an `@ExceptionHandler` for each exception of my own domain: not found is 404, a
  state conflict is 409. Validation is a 400 that lists every invalid field. Every error has the
  same shape, RFC 9457 `ProblemDetail`, sent as `application/problem+json`. One last handler for
  `Exception` answers 500 with no details and logs the stack trace. Class names, SQL and stack
  traces never go to the client.)
- This endpoint returns a 500 with a NullPointerException. How do you find the bug? (Reproduce it
  with the exact request. Read the stack trace from the top down to the first line that is our own
  code. Ask which value was null and who was supposed to set it: a missing field in the request, an
  `Optional` that was empty, a row that does not exist. Then decide whose mistake it was. If the
  client sent something wrong, the fix is validation and a 4xx, not a null check that hides it. Then
  write the test that would have caught it.)
- Why not `@ExceptionHandler(Exception.class)` and return 500 for everything? (It also catches the
  exceptions Spring throws for bad requests, so a typo in a URL becomes "the server is broken". The
  client cannot tell its own mistake from an outage, retries what can never work, and your alerts
  fire for nothing. A catch-all is fine as the last line of defence, after the specific handlers.)
- 400, 404, 409 or 422? (400: the request itself is wrong, it cannot be read or a value is invalid.
  404: the URL names something that does not exist. 409: the request is fine, but the current state
  of the resource does not allow it, for example cancelling a shipped order. 422 is used by some
  APIs for "valid JSON, invalid content"; 400 is just as correct. Pick one and be consistent.)
- Why did validation not run? (The constraint annotations on a class do nothing by themselves. For a
  `@RequestBody` parameter Spring validates only when the parameter has `@Valid` or `@Validated`.
  Without it the annotations are comments.)
- What is mass assignment? (Binding the request body straight onto an object that has more fields
  than the client should control. The client sends `"status": "PAID"` or `"role": "ADMIN"` and it
  is stored. The fix is a request DTO that contains only what a client may set. The same idea on the
  way out: a response DTO, so a new internal column cannot leak by accident.)
- What should create return? (201 Created, a `Location` header with the URL of the new resource,
  and usually the resource in the body. 200 says "fine" but not "something new exists".)
- What should delete return, and what about deleting twice? (204 No Content: it worked and there is
  nothing to show. The second time the order does not exist, so 404 is honest; 204 both times is
  also defensible, because the client's goal, "it is gone", is met. Either way DELETE is idempotent,
  because the state is the same after one call or five.)
- Why is cancelling twice a 200 here, but deleting twice a 404? (After a cancel the order still
  exists and can be shown, so the second cancel can answer with it. After a delete there is nothing
  at that URL. Both are safe to retry.)
- How would you version this API, and how many versions would you support? (Avoid needing a version
  first: add fields, never rename or remove them, and have clients ignore fields they do not know.
  For a breaking change, the common choices are a version in the path, `/api/v2/orders`, which is
  easy to see, route and cache, or a version in a header or media type, which keeps URLs stable.
  Support as few as you can: the current one and the previous one, with a published date on which
  the old one is switched off. Every live version is code you must test and fix.)

## Tips for Interviewers

- Start with "cancelling an order is a GET, is that a problem?" and count how long it takes to hear
  the word *safe*. "It should be a POST because it changes something" is the junior answer. The
  better answer explains who relies on GET being safe: prefetch, crawlers, caches, retries.
- Many candidates swap *safe* and *idempotent*, or say "idempotent means the same response". Ask
  about the second DELETE: same state, different status code.
- In Task 4, watch whether the candidate deletes the catch-all or keeps it as a last resort. Ask
  what the body of a real 500 should contain and where the stack trace goes.
- A candidate who maps `IllegalStateException` or `NoSuchElementException` globally has fixed the
  test and created the next bug: any such exception from any library now becomes a 409 or a 404.
  Ask what else throws those.
- In Task 5 the one-word fix (`@Valid`) is found quickly. The follow-up that separates levels: "the
  client's form has three wrong fields. What does it get back?"
- In Task 7, `@JsonIgnore` on the entity makes the leak test pass. Ask what happens when somebody
  adds a `costPrice` column next month.
- In Task 8, ask "is the PUT broken?" before any code is written. It is not. The candidate who says
  so, and then adds PATCH, understands the difference. Then ask how a client clears a field with
  PATCH.
- For senior candidates, Task 11 steps 6 and 7 open the door to idempotency keys and API
  versioning. Both have their own projects or deserve their own conversation.
