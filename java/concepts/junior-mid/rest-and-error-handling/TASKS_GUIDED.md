# Guided Tasks — REST Semantics and Error Handling

The same eleven tasks as [`INTERVIEW_TASKS.md`](INTERVIEW_TASKS.md), step by step: the command to
run, the output you should see, why it happens, and the change to make.

Commands are for macOS and Linux. On Windows type `.\demo`, `.\inspect` and `.\check`.

The output below is copied from real runs. Line numbers in stack frames, the port in a `Location`
header and the order of the `[PASS]` lines may differ on your machine. Status codes, bodies and the
`RESULT` line should match. Long lines are cut with `...` where the rest does not matter.

**Before you start**

```bash
cd start
docker compose up -d
```

Every run of `./demo` and `./inspect` puts the database back to the same six orders: 1, 2 and 3 are
PENDING, 4 is PAID, 5 is SHIPPED, 6 is CANCELLED.

---

## Task 1: See a GET Cancel Three Orders

No code in this task. You are collecting evidence.

### Run it

```bash
./demo
```

### What you should see

```
Scenario: prefetch | the database was reset to six orders: 1-3 PENDING, 4 PAID, 5 SHIPPED, 6 CANCELLED
A member of staff opens the "pending orders" page. A link checker then requests every
link on that page, to see if any is broken. A browser that prefetches links, a chat app
that builds a preview and a search crawler do the same. They only ever send GET.

  -> GET /admin/pending-orders
  <- 200 OK
     Content-Type: text/html;charset=UTF-8
     Links on the page (<a href>):    3
       /api/orders/1/cancel
       /api/orders/2/cancel
       /api/orders/3/cancel
     Buttons on the page (<form>):    0

  The link checker requests each link:

  -> GET /api/orders/1/cancel
  <- 200 OK
     Content-Type: application/json
     {"customer":"Maria Jones","product":"MONITOR","quantity":1,"shippingAddress":"12 Mill Lane, Leeds","contactPho...

  -> GET /api/orders/2/cancel
  <- 200 OK
     Content-Type: application/json
     {"customer":"Ahmed Khan","product":"KEYBOARD","quantity":2,"shippingAddress":"4 Park Row, Bristol","contactPho...

  -> GET /api/orders/3/cancel
  <- 200 OK
     Content-Type: application/json
     {"customer":"Sofia Rossi","product":"MOUSE","quantity":3,"shippingAddress":"77 High Street, York","contactPhon...

  In the database:
     order 1  status: PENDING -> CANCELLED
     order 2  status: PENDING -> CANCELLED
     order 3  status: PENDING -> CANCELLED

GET requests sent by the link checker:  3
Orders cancelled:                       3

RESULT: 3 GET requests. 3 orders cancelled. Nobody clicked anything.
```

Three orders are gone and nothing went wrong anywhere. Every response is a `200 OK`. No exception,
no error in a log. The link checker reported "3 links, all fine".

### What just happened

The link checker did exactly what HTTP allows. The rule it relies on:

> **GET is a safe method.** A GET asks to *read* something. The client does not ask for, and is not
> responsible for, any change on the server.

Because of that rule, a lot of software sends GET requests without a person deciding to:

| Who | Why it sends a GET you did not ask for |
|---|---|
| Link checkers, search crawlers | They follow every `<a href>` they find |
| Browsers | Prefetch links they think you will click next, and reload pages when you press Back or restore a tab |
| Chat and mail apps | Fetch a URL you paste to build a preview |
| Virus scanners in mail gateways | Open every link in an email before you see it |
| Caches and proxies | Answer a GET from a stored copy, or repeat it when the first try timed out |

All of them are right to do so. The server broke the rule, by putting a change behind a GET.

### Let the tool find it

```bash
./inspect --endpoints
```

```
ENDPOINTS (from Spring's RequestMappingHandlerMapping. Every GET was called once,
and the orders table was compared before and after)

  METHOD  PATH                     HANDLER                            FINDINGS
  ------  -----------------------  ---------------------------------  ----------------------------------------------------------------
  GET     /admin/pending-orders    AdminPageController.pendingOrders  safe: the GET changed nothing
  GET     /api/orders              OrderController.list               safe: the GET changed nothing
                                                                      RETURNS THE ENTITY Order: every column goes to the client
  POST    /api/orders              OrderController.create             NO @Valid: the rules on OrderRequest are never checked
                                                                      RETURNS THE ENTITY Order: every column goes to the client
  GET     /api/orders/{id}         OrderController.get                safe: the GET changed nothing
                                                                      RETURNS THE ENTITY Order: every column goes to the client
  PUT     /api/orders/{id}         OrderController.update             BODY IS THE ENTITY Order: the client can set every column
                                                                      RETURNS THE ENTITY Order: every column goes to the client
  DELETE  /api/orders/{id}         OrderController.delete             ok
  GET     /api/orders/{id}/cancel  OrderController.cancel             GET CHANGES DATA, NOT SAFE: order 1 status: PENDING -> CANCELLED
                                                                      RETURNS THE ENTITY Order: every column goes to the client

  7 endpoints, 8 findings.
```

The tool asks Spring for every endpoint it has registered. For each GET it takes a copy of the
`orders` table, sends the request, and compares. Three GETs changed nothing. The fourth changed a
row. That is how you test "safe": not by reading the code, by measuring.

(The other findings are the later tasks. Ignore them for now.)

### Find it in the code

`OrderController`:

```java
@GetMapping("/{id}/cancel")
public Order cancel(@PathVariable long id) {
    return service.cancel(id);
}
```

`AdminPageController` builds the page with a link for every pending order:

```java
.append("<a href=\"/api/orders/").append(order.getId()).append("/cancel\">Cancel</a></li>\n");
```

One annotation and one HTML tag. An `<a href>` can only send a GET.

### "But the admin page needs a login"

A login does not fix it. The person who is logged in has a browser, and the browser prefetches. A
virus scanner that opens a link from an email does it from inside the company network. And if the
login is a cookie, any other website can put `<img src="https://shop/api/orders/1/cancel">` on a
page: the browser of a logged-in member of staff requests it and sends the cookie along. That attack
is called CSRF, and a state-changing GET is the easiest way into it.

---

## Task 2: Cancel With a POST

**The idea:** a request that changes something must use a method that is allowed to change
something. Then the page must stop offering it as a link.

### Before

```bash
./check 2
```

0 of 5 pass.

### Step 1: the endpoint

In `OrderController`, change one word:

```java
@PostMapping("/{id}/cancel")
public Order cancel(@PathVariable long id) {
    return service.cancel(id);
}
```

`PostMapping` is already imported. Delete the `TODO Task 2` comment.

### Step 2: the page

In `AdminPageController`, replace the line that builds the link with a form:

```java
html.append("<li>#").append(order.getId()).append(' ').append(HtmlUtils.htmlEscape(order.getCustomer())).append(' ')
        .append("<form method=\"post\" action=\"/api/orders/").append(order.getId()).append("/cancel\">")
        .append("<button>Cancel</button></form></li>\n");
```

A form with `method="post"` and a button sends a POST when a person presses the button. Nothing
presses buttons by itself.

### After

```bash
./demo
```

```
  -> GET /admin/pending-orders
  <- 200 OK
     Content-Type: text/html;charset=UTF-8
     Links on the page (<a href>):    0
     Buttons on the page (<form>):    3

  Nothing to follow: a link checker requests links, it does not press buttons.
  It still has the old cancel URLs from its last visit, so it requests those:

  -> GET /api/orders/1/cancel
  <- 500 Internal Server Error
     Content-Type: application/json
     {"error":"org.springframework.web.HttpRequestMethodNotSupportedException","message":"Request method 'GET' is n...

  -> GET /api/orders/2/cancel
  <- 500 Internal Server Error
     Content-Type: application/json
     {"error":"org.springframework.web.HttpRequestMethodNotSupportedException","message":"Request method 'GET' is n...

  -> GET /api/orders/3/cancel
  <- 500 Internal Server Error
     Content-Type: application/json
     {"error":"org.springframework.web.HttpRequestMethodNotSupportedException","message":"Request method 'GET' is n...

  In the database: nothing changed

GET requests sent by the link checker:  3
Orders cancelled:                       0

RESULT: OK. 3 GET requests. 0 orders changed.
```

The orders are safe. Look at the status code though: `500 Internal Server Error` for a request that
used the wrong method. The server is not broken, the client is wrong. That is Task 4.

```bash
./check 2
```

```
Task 2: Cancel with a POST, not a GET
  [PASS] Cancel: requesting every link on the admin page changes no order
  [PASS] Cancel: POST /api/orders/1/cancel returns 200 and the order is CANCELLED
  [PASS] Cancel: the same POST sent twice is safe: 200 both times, still CANCELLED, nothing else changed
  [PASS] Cancel: the admin page cancels with a form that POSTs, not with a link
  [PASS] Cancel: GET /api/orders/1/cancel is refused and the order stays PENDING
  5 of 5 pass
```

---

## Task 3: Inspect — Safe and Idempotent

### Step 1: what "safe" was tested

```bash
./inspect --endpoints
```

The last row now reads:

```
  POST    /api/orders/{id}/cancel  OrderController.cancel             RETURNS THE ENTITY Order: every column goes to the client

  7 endpoints, 7 findings.
```

"GET CHANGES DATA" is gone. The three GET rows say `safe: the GET changed nothing`: each was called
and the table was the same afterwards.

Safe does not mean "the server does nothing at all". A GET may write a log line, count a page view
or fill a cache. Safe means the client did not ask for a change and cannot be blamed for one. A
view counter is fine. A cancelled order is not.

### Step 2: the link checker now

You ran `./demo` in Task 2: the page has 0 links and 3 buttons. A link checker has nothing to
follow. The old URLs are refused.

### Step 3 and 4: the same cancel, twice

```bash
./demo --scenario cancel
```

```
This application cancels with: POST /api/orders/{id}/cancel
A client cancels order 1. The answer gets lost, so the client sends the same request again.
Then somebody tries to cancel order 5, which has already been shipped.

  -> POST /api/orders/1/cancel
  <- 200 OK
     Content-Type: application/json
     {"customer":"Maria Jones","product":"MONITOR","quantity":1,"shippingAddress":"12 Mill Lane, Leeds","contactPho...

  In the database:
     order 1  status: PENDING -> CANCELLED

  -> POST /api/orders/1/cancel
  <- 200 OK
     Content-Type: application/json
     {"customer":"Maria Jones","product":"MONITOR","quantity":1,"shippingAddress":"12 Mill Lane, Leeds","contactPho...

  In the database: nothing changed
```

(The third request, the shipped order, answers 500 for now. Task 4.)

The second cancel got `200 OK` and changed nothing. Sending it once or twice leaves the server in
the same state. That property is called **idempotent**.

HTTP does not promise it for POST. The code does. `Order.cancel()`:

```java
public void cancel() {
    if (status == OrderStatus.SHIPPED) {
        throw new IllegalStateException("Order " + id + " is SHIPPED and cannot be cancelled");
    }
    status = OrderStatus.CANCELLED;
}
```

It sets the status to a fixed value. Setting CANCELLED on a CANCELLED order is not an error, so a
client that lost the first answer can simply send the request again. A different design ("409, it
is already cancelled") would also be correct, and harder on a client that retries.

### Step 5: the table

| Method | Safe? | Idempotent? | Meaning |
|---|---|---|---|
| GET | yes | yes | read |
| HEAD | yes | yes | read, headers only |
| OPTIONS | yes | yes | ask what is allowed |
| PUT | no | yes | store this at exactly this URL, replacing what is there |
| DELETE | no | yes | remove what is at this URL |
| POST | no | no | create something, or run an action |
| PATCH | no | no | change part of what is at this URL |

Two definitions to learn by heart:

- **Safe**: the request does not change the state of the server. Read-only.
- **Idempotent**: sending the request N times leaves the server in the same state as sending it
  once.

Every safe method is idempotent. Not the other way round: DELETE changes things, but deleting twice
leaves the same result as deleting once.

Idempotent is about the **state**, not about the **response**. The second DELETE of the same order
may answer 404. The state is still the same: the order is gone.

"POST is not idempotent" means HTTP makes no promise, so nothing may assume it. You are free to
build a POST that is idempotent, as this cancel is.

### Step 6: why POST, and not DELETE, PATCH or PUT

| Option | Why not |
|---|---|
| `DELETE /api/orders/1` | DELETE means "remove the resource at this URL". A cancelled order is not removed: it still exists, with a status, and can be read. This API also needs a real delete. |
| `PATCH /api/orders/1` with `{"status": "CANCELLED"}` | It makes `status` a field the client writes. Then you need rules for every value: may a client send `PAID`? `SHIPPED`? The business rule "a shipped order cannot be cancelled" ends up hidden inside a generic field update. |
| `PUT /api/orders/1` with the whole order | The same problem, and the client has to send every other field just to change one. |
| `POST /api/orders/1/cancel` | Cancel is an action with its own rules. POST is the method for "process this according to the resource's own meaning". The URL says what happens, the server stays in charge of the status, and there is one place for the rule. |

There is a second reasonable design: treat the cancellation as a thing that is created, with
`POST /api/orders/1/cancellation`. Same method, same reasoning. What matters in an interview is
that you say why GET is wrong and can argue for the one you picked.

### Step 7: who may retry

A proxy, an HTTP client library or a browser may send a request again on its own when the method is
idempotent: GET, HEAD, PUT, DELETE. It must not do that with a POST, because a second POST might
create a second order. That is why a browser asks "submit the form again?" when you reload a page
that came from a POST, and never asks for a GET.

### What to take away

- GET must not change data. Everything on the web assumes it does not.
- Safe = read-only. Idempotent = repeating it changes nothing more.
- GET, HEAD: safe and idempotent. PUT, DELETE: idempotent. POST, PATCH: no promise.
- An action with its own business rule is a POST to a URL that names the action.

---

## Task 4: The Right Status Code for Every Error

**The idea:** a status code tells the client who has to fix the problem. 4xx: you, the client. 5xx:
us, the server. This API says 5xx for everything.

### Before

```bash
./demo --scenario errors
```

Five requests, each with a mistake made by the client. The first and the third:

```
1. An order that does not exist
  -> GET /api/orders/999
  <- 500 Internal Server Error
     Content-Type: application/json
     {
       "error": "java.util.NoSuchElementException",
       "message": "No value present",
       "trace": [
         "java.base/java.util.Optional.orElseThrow(Optional.java:377)",
         "com.amigoscode.restapi.order.OrderService.get(OrderService.java:23)",
         "java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(DirectMethodHandleAccessor.java:104)"
       ]
     }

3. A product the shop does not sell
  -> POST /api/orders
     {"customer": "Nina Patel", "product": "PLAYSTATION", "quantity": 1, "shippingAddress": "8 Queen Square, Bath"}
  <- 500 Internal Server Error
     Content-Type: application/json
     {
       "error": "org.springframework.http.converter.HttpMessageNotReadableException",
       "message": "JSON parse error: Cannot deserialize value of type `com.amigoscode.restapi.order.Product` from String \"PLAYSTATION\": not one of the values accepted for Enum class: [MONITOR, MOUSE, KEYBOARD]",
       "trace": [
         "org.springframework.http.converter.AbstractJacksonHttpMessageConverter.readJavaType(AbstractJacksonHttpMessageConverter.java:363)",
         ...
       ]
     }
```

And the summary at the end:

```
  1. An order that does not exist                  got 500   should be 404   WRONG  LEAKS INTERNALS
  2. A body that is not JSON (it stops halfway)    got 500   should be 400   WRONG  LEAKS INTERNALS
  3. A product the shop does not sell              got 500   should be 400   WRONG  LEAKS INTERNALS
  4. A status that does not exist, in the filter   got 500   should be 400   WRONG  LEAKS INTERNALS
  5. Cancelling an order that was already shipped  got 500   should be 409   WRONG  LEAKS INTERNALS

RESULT: 5 client mistakes. 5 got the wrong status code. 5 bodies show class names, stack traces or SQL. 5 are not application/problem+json.
```

Two separate problems.

**The wrong code.** A 500 tells the client "we are broken, try again later". The mobile app shows
"the server is down" for a typo. A client library retries a request that can never work. The
on-call engineer is paged for a customer's mistake.

**The leak.** The body tells a stranger the package names, the class names, the framework, which
line of which file failed, and the Java version. That is a map for an attacker and noise for
everybody else.

### Where it comes from

Open `ApiExceptionHandler`:

```java
@ExceptionHandler(Exception.class)
public ResponseEntity<Map<String, Object>> handle(Exception e) {
    ...
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
}
```

It catches **every** exception and answers 500. Three kinds of exception arrive here:

1. **Spring's own**, thrown before your code even runs: `HttpMessageNotReadableException` (the body
   cannot be read), `HttpRequestMethodNotSupportedException` (wrong method),
   `NoResourceFoundException` (no such URL). Spring knows the right status code for every one of
   them. This handler takes them away before Spring can answer.
2. **Java's**, thrown by code that did not think about the unhappy path:
   `NoSuchElementException` from `orElseThrow()`, `IllegalArgumentException` from
   `OrderStatus.valueOf("DONE")`.
3. **The domain's**: `IllegalStateException` from `Order.cancel()`.

### What Spring Boot does when you do nothing

It is worth knowing the default, because interviewers ask. If you deleted `ApiExceptionHandler`
completely, this is what Spring Boot 4 would answer (measured with `./inspect --probes --bodies`
on this project without the class):

| The client... | Status | Body |
|---|---|---|
| uses an id that is not a number | 400 | Spring's default |
| asks for a URL that does not exist | 404 | Spring's default |
| uses a method the URL does not have | 405 | Spring's default |
| sends a body that is not JSON | 400 | Spring's default |
| sends JSON as `text/plain` | 415 | Spring's default |
| orders a product that does not exist | 400 | Spring's default |
| reads an order that does not exist | **500** | Spring's default |
| filters by a status that does not exist | **500** | Spring's default |
| cancels a shipped order | **500** | Spring's default |

Spring's default body, sent as `application/json`:

```
{
  "timestamp": "2026-11-22T10:04:30.004Z",
  "status": 400,
  "error": "Bad Request",
  "path": "/api/orders/abc"
}
```

So out of the box Spring gets its own six right, shows no message and no stack trace, and answers
500 only for the three exceptions that come from this application. The catch-all handler made six
answers worse. Those three are yours to map.

### Step 1: two exceptions that say what happened

Create `order/OrderNotFoundException.java`:

```java
package com.amigoscode.restapi.order;

public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(long id) {
        super("Order " + id + " does not exist");
    }
}
```

Create `order/OrderStateException.java`:

```java
package com.amigoscode.restapi.order;

public class OrderStateException extends RuntimeException {

    public OrderStateException(String message) {
        super(message);
    }
}
```

Why your own classes? Because you are about to say "this exception means 404". If you said that
about `NoSuchElementException`, every empty `Optional` and every `iterator.next()` anywhere in the
application, in any library, would become "404 Not Found". Same for `IllegalStateException` and
409. An exception you map to a status code must mean exactly one thing.

### Step 2: throw them

In `OrderService`:

```java
public Order get(long id) {
    return orders.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
}
```

Still in `OrderService`, make `update` check that the order exists (add the first line):

```java
public Order update(long id, Order order) {
    get(id);
    order.setId(id);
    ...
```

In `Order.cancel()`:

```java
if (status == OrderStatus.SHIPPED) {
    throw new OrderStateException("Order " + id + " is SHIPPED and cannot be cancelled");
}
```

### Step 3: let Spring convert the status filter

In `OrderController`:

```java
@GetMapping
public List<Order> list(@RequestParam(required = false) OrderStatus status) {
    return service.list(status);
}
```

The parameter is an `OrderStatus` now, not a `String`. Spring converts `?status=PAID` for you. When
it cannot (`?status=DONE`) it throws `MethodArgumentTypeMismatchException`, and it knows that is a
400. No `valueOf`, no `try/catch`.

### Step 4: the handler

Replace the whole of `ApiExceptionHandler`:

```java
package com.amigoscode.restapi;

import com.amigoscode.restapi.order.OrderNotFoundException;
import com.amigoscode.restapi.order.OrderStateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(OrderNotFoundException.class)
    public ProblemDetail notFound(OrderNotFoundException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        problem.setTitle("Order not found");
        return problem;
    }

    @ExceptionHandler(OrderStateException.class)
    public ProblemDetail conflict(OrderStateException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        problem.setTitle("Order state conflict");
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail unexpected(Exception e) {
        log.error("Unexpected error", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Something went wrong on our side. It was not your request.");
    }
}
```

Four things to notice:

- **`extends ResponseEntityExceptionHandler`.** That base class has a handler for every exception
  Spring MVC throws itself, with the right status code, and it answers in `ProblemDetail` format.
  By extending it you get 400, 404, 405 and 415 without writing them. A handler for a specific
  exception always wins over the handler for `Exception`, so the catch-all no longer swallows them.
- **`ProblemDetail`** is Spring's class for RFC 9457, "Problem Details for HTTP APIs": one standard
  JSON shape for errors, sent with the content type `application/problem+json`. When an
  `@ExceptionHandler` method returns a `ProblemDetail`, Spring takes the HTTP status from it and
  sets the content type.
- **Two handlers for your own exceptions.** This is the only part Spring could not have known.
- **The catch-all stays, last.** A real bug must still become a 500. But the body says nothing
  about the inside of the server, and the stack trace goes to the log, where your team can read it
  and nobody else can. (The demo and the tests switch this logger off to keep their output short.)

### After

```bash
./demo --scenario errors
```

```
1. An order that does not exist
  -> GET /api/orders/999
  <- 404 Not Found
     Content-Type: application/problem+json
     {
       "detail": "Order 999 does not exist",
       "instance": "/api/orders/999",
       "status": 404,
       "title": "Order not found"
     }

2. A body that is not JSON (it stops halfway)
  -> POST /api/orders
     {"customer": "Nina Patel", "product":
  <- 400 Bad Request
     Content-Type: application/problem+json
     {
       "detail": "Failed to read request",
       "instance": "/api/orders",
       "status": 400,
       "title": "Bad Request"
     }

...

5. Cancelling an order that was already shipped
  -> POST /api/orders/5/cancel
  <- 409 Conflict
     Content-Type: application/problem+json
     {
       "detail": "Order 5 is SHIPPED and cannot be cancelled",
       "instance": "/api/orders/5/cancel",
       "status": 409,
       "title": "Order state conflict"
     }

  In the database: nothing changed

  1. An order that does not exist                  got 404   should be 404   ok     no leak
  2. A body that is not JSON (it stops halfway)    got 400   should be 400   ok     no leak
  3. A product the shop does not sell              got 400   should be 400   ok     no leak
  4. A status that does not exist, in the filter   got 400   should be 400   ok     no leak
  5. Cancelling an order that was already shipped  got 409   should be 409   ok     no leak

RESULT: OK. 5 client mistakes, 5 answers in the 4xx range, all application/problem+json, no internals.
```

Run `./demo` again as well. The link checker from Task 2 now gets the honest answer:

```
  -> GET /api/orders/1/cancel
  <- 405 Method Not Allowed
     Content-Type: application/problem+json
     Allow: POST
     {"detail":"Method 'GET' is not supported.","instance":"/api/orders/1/cancel","status":405,"title":"Method Not ...
```

`405 Method Not Allowed`, and the `Allow` header tells the client which method would work. You did
not write that.

```bash
./check 4
```

12 of 12 pass. Run `./check` too: Task 2 must still be green.

---

## Task 5: Validation That Actually Runs

### Before

```bash
./demo --scenario validation
```

```
A client creates an order with no customer name and a quantity of -5.
OrderRequest says: @NotBlank customer, @Positive quantity.

  -> POST /api/orders
     {
       "customer": "",
       "product": "KEYBOARD",
       "quantity": -5,
       "shippingAddress": "4 Park Row, Bristol"
     }
  <- 200 OK
     Content-Type: application/json
     {
       "customer": "",
       "product": "KEYBOARD",
       "quantity": -5,
       "shippingAddress": "4 Park Row, Bristol",
       "contactPhone": null,
       "id": 7,
       "internalNote": null,
       "notes": null,
       "status": "PENDING",
       "total": -250.00
     }

  In the database:
     order 7  NEW ROW: customer='' product=KEYBOARD quantity=-5 total=-250.00 status=PENDING

RESULT: INVALID ORDER SAVED. Status 200. The shop now has an order for -5 keyboards, for nobody, with a total of -250.00.
```

Now open `OrderRequest`:

```java
public record OrderRequest(
        @NotBlank String customer,
        @NotNull Product product,
        @Positive int quantity,
        @NotBlank String shippingAddress,
        @Size(max = 200) String notes,
        @Size(max = 30) String contactPhone) {
}
```

The rules are there. And `OrderController`:

```java
public Order create(@RequestBody OrderRequest request) {
```

`@RequestBody` means "turn the JSON into an `OrderRequest`". It does not mean "and check it". The
constraint annotations are only metadata. Somebody has to ask the validator to read them, and
nobody does. `./inspect --endpoints` has been telling you since Task 1:
`NO @Valid: the rules on OrderRequest are never checked`.

This bug is common because the code looks right. The annotations are in the DTO, a reviewer sees
them, and everybody assumes they work.

### Step 1: one word

In `OrderController`:

```java
@PostMapping
public Order create(@Valid @RequestBody OrderRequest request) {
    return service.create(request);
}
```

Import `jakarta.validation.Valid`.

### Look before you go on

```bash
./demo --scenario validation
```

```
  <- 400 Bad Request
     Content-Type: application/problem+json
     {
       "detail": "Invalid request content.",
       "instance": "/api/orders",
       "status": 400,
       "title": "Bad Request"
     }

  In the database: nothing changed

RESULT: NOT THERE YET. Nothing was saved, but the answer is 400 and the body does not say which fields are invalid.
```

Nothing is saved any more: Spring validated the body, threw `MethodArgumentNotValidException`, and
the base class from Task 4 turned it into a 400. The data is safe.

But "Invalid request content." Which content? The client has a form with six fields and no idea
which one to mark in red. Half a fix.

### Step 2: name the fields

`ResponseEntityExceptionHandler` has one `protected` method per Spring exception, so that you can
change a single answer. Add this to `ApiExceptionHandler`, above the catch-all:

```java
public record InvalidField(String field, String message) {
}

@Override
protected ResponseEntity<Object> handleMethodArgumentNotValid(
        MethodArgumentNotValidException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    List<InvalidField> errors = e.getBindingResult().getFieldErrors().stream()
            .map(error -> new InvalidField(error.getField(), error.getDefaultMessage()))
            .sorted(Comparator.comparing(InvalidField::field))
            .toList();
    ProblemDetail problem = e.getBody();
    problem.setTitle("Validation failed");
    problem.setDetail(errors.size() + (errors.size() == 1 ? " field is invalid" : " fields are invalid"));
    problem.setProperty("errors", errors);
    return handleExceptionInternal(e, problem, headers, status, request);
}
```

New imports:

```java
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.WebRequest;
```

- `e.getBody()` is the `ProblemDetail` Spring already prepared (status 400). You add to it.
- `setProperty("errors", ...)` adds a field of your own. RFC 9457 allows extra fields, and calls
  them extension members.
- The list is sorted by field name. The validator reports errors in no fixed order, and an API
  should give the same answer to the same request.
- Only the field name and the message go out. Not the rejected value, not the class name.

### After

```bash
./demo --scenario validation
```

```
  <- 400 Bad Request
     Content-Type: application/problem+json
     {
       "detail": "2 fields are invalid",
       "instance": "/api/orders",
       "status": 400,
       "title": "Validation failed",
       "errors": [
         {
           "field": "customer",
           "message": "must not be blank"
         },
         {
           "field": "quantity",
           "message": "must be greater than 0"
         }
       ]
     }

  In the database: nothing changed

RESULT: OK. 400, nothing saved, and the body names both invalid fields: customer and quantity.
```

```bash
./check 5
```

```
Task 5: Validation that actually runs
  [PASS] Validation (unit): a missing product is a 400 that names the field "product"
  [PASS] Validation (unit): quantity 0 and quantity -5 are both a 400 that names the field "quantity"
  [PASS] Validation (unit): a blank customer is a 400 that names the field "customer"
  [PASS] Validation (unit): a valid order is still accepted
  [PASS] Validation: the 400 does not show class names or the rejected Java object
  [PASS] Validation: the 400 is application/problem+json and "errors" lists every invalid field with a message
  [PASS] Validation: an order for -5 keyboards returns 400 and nothing is saved
  7 of 7 pass
```

> **A side effect worth seeing.** Before this task, a POST without a `product` crashed with a
> `NullPointerException` in `Order.recalculateTotal()`, because `product.price()` was called on
> null. After Task 4 that was a clean 500. Now it is a 400 that says `product: must not be null`.
> The NPE was never the bug. The bug was that an invalid request got as far as the code that
> assumed a valid one. When you are asked to "fix the NPE", ask first who let the null in.

---

## Task 6: Inspect — The Error Responses

### Step 1: the shape

You saw the five bodies in Task 4. Every one has the same four fields:

| Field | What it is | Example |
|---|---|---|
| `status` | the HTTP status code, repeated in the body | `404` |
| `title` | a short name for this kind of problem. The same for every occurrence | `"Order not found"` |
| `detail` | what happened this time, for a person to read | `"Order 999 does not exist"` |
| `instance` | which request it happened on | `"/api/orders/999"` |

RFC 9457 defines a fifth, `type`: a URI that identifies the kind of problem, for programs to
compare. Spring leaves it out of the JSON while it has the default value `about:blank`, which means
"no more specific type than the status code". In a larger API you would set it, for example
`https://api.shop.example/problems/order-not-found`, and clients would switch on it.

The `Content-Type` is `application/problem+json`. A client can tell from the header alone that
this body is an error in the standard shape, before parsing anything.

### Step 2: the answers you did not write

```bash
./inspect --probes
```

```
STATUS CODES (18 probe requests. The database is reset before each one)

  #   THE CLIENT...                             REQUEST                      GOT  SHOULD BE  BODY TYPE     VERDICT
  --  ----------------------------------------  ---------------------------  ---  ---------  ------------  ------------------------------------------
  1   reads an order that does not exist        GET /api/orders/999          404  404        problem+json  ok
  2   uses an id that is not a number           GET /api/orders/abc          400  400        problem+json  ok
  3   asks for a URL that does not exist        GET /api/nope                404  404        problem+json  ok
  4   uses a method the URL does not have       DELETE /api/orders           405  405        problem+json  ok
  5   sends a body that is not JSON             POST /api/orders             400  400        problem+json  ok
  6   sends JSON as text/plain                  POST /api/orders             415  415        problem+json  ok
  7   orders a product that does not exist      POST /api/orders             400  400        problem+json  ok
  8   orders a quantity of -5                   POST /api/orders             400  400        problem+json  ok
  9   filters by a status that does not exist   GET /api/orders?status=DONE  400  400        problem+json  ok
  10  cancels with a GET                        GET /api/orders/2/cancel     405  405        problem+json  ok
  11  cancels a shipped order                   POST /api/orders/5/cancel    409  409        problem+json  ok
  12  creates an order                          POST /api/orders             200  201        json          WRONG: wrong status; no Location header
  13  replaces an order (PUT)                   PUT /api/orders/2            200  200        json          ok
  14  sets status and total in a PUT            PUT /api/orders/2            200  200        json          WRONG: the client changed status and total
  15  reads an order that has an internal note  GET /api/orders/4            200  200        json          WRONG: shows internalNote
  16  changes one field (PATCH)                 PATCH /api/orders/4          405  200        problem+json  WRONG: wrong status
  17  deletes an order                          DELETE /api/orders/6         200  204        json          WRONG: wrong status; has a body
  18  deletes the same order again              DELETE /api/orders/6         404  404        problem+json  ok

  13 of 18 answers are right.
```

It was 1 of 18 when you started. Rows 2, 3, 4, 5, 6, 7, 9 and 10 are answered by
`ResponseEntityExceptionHandler`. You wrote code for rows 1, 8, 11 and 18 only. The five that are
still wrong are Tasks 7, 8 and 10.

### Step 3: how much to tell

```bash
./inspect --probes --bodies
```

Body 7, the product that does not exist:

```
  7. POST /api/orders  ->  400 Bad Request
     {
       "detail": "Failed to read request",
       "instance": "/api/orders",
       "status": 400,
       "title": "Bad Request"
     }
```

Before Task 4 the same request answered with
`not one of the values accepted for Enum class: [MONITOR, MOUSE, KEYBOARD]`, which was more useful
to the client, next to a Java class name, which was not for the client at all. Spring's default
chooses to say little: it cannot know which parts of an exception message are safe to show.

This is a trade-off, and both sides have a point:

- For a **public** API, say little by default. Then add back, on purpose, what helps: you could
  override `handleHttpMessageNotReadable` and write your own `detail`, such as
  `"product must be one of KEYBOARD, MOUSE, MONITOR"`. Text you wrote, not text copied from an
  exception.
- **Never** forward `e.getMessage()` of an exception you did not write. You do not control what is
  in it: today a class name, tomorrow a SQL statement with a table name.

### Step 4: all the fields at once

The validation answer listed both invalid fields. If it reported only the first, a client with
three wrong fields would need three round trips to find them: send, fix one, send, fix one, send.
Report everything you know in one answer.

### Step 5: what the base class does

Try it. In `ApiExceptionHandler` delete ` extends ResponseEntityExceptionHandler` and the whole
`handleMethodArgumentNotValid` method (it no longer compiles without the base class). Then:

```bash
./inspect --probes
```

```
  1   reads an order that does not exist        GET /api/orders/999          404  404        problem+json  ok
  2   uses an id that is not a number           GET /api/orders/abc          500  400        problem+json  WRONG: a 500 blames the server
  3   asks for a URL that does not exist        GET /api/nope                500  404        problem+json  WRONG: a 500 blames the server
  4   uses a method the URL does not have       DELETE /api/orders           500  405        problem+json  WRONG: a 500 blames the server; no Allow header
  5   sends a body that is not JSON             POST /api/orders             500  400        problem+json  WRONG: a 500 blames the server
  6   sends JSON as text/plain                  POST /api/orders             500  415        problem+json  WRONG: a 500 blames the server
  7   orders a product that does not exist      POST /api/orders             500  400        problem+json  WRONG: a 500 blames the server
  8   orders a quantity of -5                   POST /api/orders             500  400        problem+json  WRONG: a 500 blames the server; does not name the field
  9   filters by a status that does not exist   GET /api/orders?status=DONE  500  400        problem+json  WRONG: a 500 blames the server
  10  cancels with a GET                        GET /api/orders/2/cancel     500  405        problem+json  WRONG: a 500 blames the server
  11  cancels a shipped order                   POST /api/orders/5/cancel    409  409        problem+json  ok
  ...
  4 of 18 answers are right.
```

Rows 2 to 10 are 500 again. Your `@ExceptionHandler(Exception.class)` is now the only handler that
matches Spring's exceptions, so it takes all of them. Rows 1 and 11 survive because they have a
handler of their own.

That is the whole lesson about catch-all handlers: `@ExceptionHandler(Exception.class)` is
dangerous **alone** and useful **last**.

**Put the code back** (undo, or copy it again from Task 5) and run `./check 5`.

### Step 6: which code for which mistake

| Situation | Code | Why |
|---|---|---|
| The body is not JSON, or a value has the wrong type | 400 Bad Request | The server cannot even read the request |
| The JSON is fine, the quantity is -5 | 400 Bad Request | The request breaks a rule about its content. Some APIs use 422 Unprocessable Content here, to separate "cannot read it" from "read it, do not accept it". Both are correct. Pick one for the whole API |
| The order does not exist | 404 Not Found | The URL names nothing |
| The order exists, but has been shipped | 409 Conflict | The request is fine. The current state of the resource does not allow it. The same request would have worked yesterday |

The test for 409: would the identical request succeed if the resource were in another state? Then
it is not the client's request that is wrong, it is the timing.

### Step 7: who has to act

- **4xx**: the client. Sending the same request again will fail again. Fix the request.
- **5xx**: the server's team. The client may try again later.

Alerts belong on 5xx. If client mistakes come back as 500, the team is woken up for typos, learns
to ignore the alert, and misses the real outage.

### What to take away

- One `@RestControllerAdvice` that extends `ResponseEntityExceptionHandler`.
- Spring's exceptions already have the right code. Do not take that away.
- One handler per exception of your own, and each of those exceptions means one thing.
- One shape for every error: `ProblemDetail`, `application/problem+json`.
- The client gets what it needs to fix the request. The log gets everything else.

---

## Task 7: Request and Response DTOs

**The idea:** the database row and the API are two different things. The row has columns the client
must not write (`status`, `total`) and a column the client must not read (`internal_note`). When
the JPA entity is the request body and the response body, the client gets all of them.

### Before

```bash
./demo --scenario mass-assignment
```

```
Order 1 is PENDING, total 200.00. Its customer sends a PUT with the usual fields, and adds
three that no customer should be able to set: status, total and internalNote.

  -> PUT /api/orders/1
     {
       "customer": "Maria Jones",
       "product": "MONITOR",
       "quantity": 1,
       "shippingAddress": "12 Mill Lane, Leeds",
       "notes": "Ring the bell twice",
       "contactPhone": "0113 496 0001",
       "status": "PAID",
       "total": 0.01,
       "internalNote": "VIP. Never check this customer"
     }
  <- 200 OK
     Content-Type: application/json
     {
       "customer": "Maria Jones",
       "product": "MONITOR",
       "quantity": 1,
       "shippingAddress": "12 Mill Lane, Leeds",
       "contactPhone": "0113 496 0001",
       "id": 1,
       "internalNote": "VIP. Never check this customer",
       "notes": "Ring the bell twice",
       "status": "PAID",
       "total": 0.01
     }

  In the database:
     order 1  total: 200.00 -> 0.01
     order 1  status: PENDING -> PAID
     order 1  internal_note: Card was declined once in May -> VIP. Never check this customer

Then anybody reads order 4:

  -> GET /api/orders/4
  <- 200 OK
     Content-Type: application/json
     {
       "customer": "Liam Brown",
       "product": "MONITOR",
       "quantity": 2,
       "shippingAddress": "9 Castle Road, Cardiff",
       "contactPhone": "029 2018 0002",
       "id": 4,
       "internalNote": "Buys for resale. Margin 12 percent",
       "notes": "Gift. No invoice in the box",
       "status": "PAID",
       "total": 400.00
     }

RESULT: MASS ASSIGNMENT. The client changed status, total, internal_note. Order 1 is PAID for 0.01 and nobody paid.
RESULT: LEAK. The response shows internalNote, a note written by staff for staff.
```

A 200.00 monitor, marked as paid, for one cent, by the customer. And the staff's private note about
Liam is sent to whoever asks for order 4.

### Where it comes from

`OrderController`:

```java
@PutMapping("/{id}")
public Order update(@PathVariable long id, @RequestBody Order order) {
    return service.update(id, order);
}
```

`@RequestBody Order` tells Jackson: build an `Order` from whatever JSON arrives. Jackson calls every
setter it finds a field for. The entity has `setStatus`, `setTotal` and `setInternalNote`, so the
client can call them by typing their names. This is called **mass assignment**. In other frameworks
the same hole lets a user send `"role": "ADMIN"`.

`OrderService.update` then saves that object as it is. Whatever the client did not send goes back to
the Java default. That is why a PUT without `status` turned a PAID order into a PENDING one, and
why it wiped the internal note.

And every method returns `Order`, so every column of the row goes out as JSON.

Why did POST not have this problem? Because `create` takes an `OrderRequest`. That record has no
`status` and no `total`, so there is nothing for Jackson to set. The fix is to do the same for PUT,
and to do the same thing on the way out.

### Step 1: what a client may see

Create `order/OrderResponse.java`:

```java
package com.amigoscode.restapi.order;

import java.math.BigDecimal;

public record OrderResponse(
        long id,
        String customer,
        Product product,
        int quantity,
        BigDecimal total,
        OrderStatus status,
        String shippingAddress,
        String notes,
        String contactPhone) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getCustomer(),
                order.getProduct(),
                order.getQuantity(),
                order.getTotal(),
                order.getStatus(),
                order.getShippingAddress(),
                order.getNotes(),
                order.getContactPhone());
    }
}
```

There is no `internalNote` in it. It cannot leak, because it is not there.

### Step 2: a PUT that copies only what the client may set

In `OrderService`, replace `update` with:

```java
public Order replace(long id, OrderRequest request) {
    Order order = get(id);
    order.setCustomer(request.customer());
    order.setProduct(request.product());
    order.setQuantity(request.quantity());
    order.setShippingAddress(request.shippingAddress());
    order.setNotes(request.notes());
    order.setContactPhone(request.contactPhone());
    order.recalculateTotal();
    return orders.save(order);
}
```

The order is **loaded**, then the six client fields are copied onto it. `status` and `internalNote`
are not touched, so they keep their values. The total is worked out by the server.

### Step 3: the controller

In `OrderController`, change these five methods:

```java
@GetMapping
public List<OrderResponse> list(@RequestParam(required = false) OrderStatus status) {
    return service.list(status).stream().map(OrderResponse::from).toList();
}

@GetMapping("/{id}")
public OrderResponse get(@PathVariable long id) {
    return OrderResponse.from(service.get(id));
}

@PostMapping
public OrderResponse create(@Valid @RequestBody OrderRequest request) {
    return OrderResponse.from(service.create(request));
}

@PutMapping("/{id}")
public OrderResponse replace(@PathVariable long id, @Valid @RequestBody OrderRequest request) {
    return OrderResponse.from(service.replace(id, request));
}

@PostMapping("/{id}/cancel")
public OrderResponse cancel(@PathVariable long id) {
    return OrderResponse.from(service.cancel(id));
}
```

Note `@Valid` on the PUT. It takes the same `OrderRequest` as POST, so it gets the same rules, as
long as you ask for them. You have seen what happens when you forget.

### After

```bash
./demo --scenario mass-assignment
```

```
  <- 200 OK
     Content-Type: application/json
     {
       "id": 1,
       "customer": "Maria Jones",
       "product": "MONITOR",
       "quantity": 1,
       "total": 200.00,
       "status": "PENDING",
       "shippingAddress": "12 Mill Lane, Leeds",
       "notes": "Ring the bell twice",
       "contactPhone": "0113 496 0001"
     }

  In the database: nothing changed

Then anybody reads order 4:

  -> GET /api/orders/4
  <- 200 OK
     Content-Type: application/json
     {
       "id": 4,
       "customer": "Liam Brown",
       "product": "MONITOR",
       "quantity": 2,
       "total": 400.00,
       "status": "PAID",
       "shippingAddress": "9 Castle Road, Cardiff",
       "notes": "Gift. No invoice in the box",
       "contactPhone": "029 2018 0002"
     }

RESULT: OK. The client sent status, total and internalNote. The server ignored all three,
        and no response shows internalNote.
```

```bash
./check 7
```

10 of 10 pass.

### Why not `@JsonIgnore`?

Putting `@JsonIgnore` on `internalNote` in the entity would make the leak test pass. Three reasons
not to:

- It is a **deny list**. Next month somebody adds a `costPrice` column. It leaks until somebody
  remembers the annotation. A DTO is an **allow list**: a new column stays private until somebody
  adds it to the record on purpose.
- The entity now carries rules about JSON. Change a column name and the API changes with it.
- One class cannot serve two audiences. Staff may see the internal note, customers may not. With
  DTOs that is two records.

---

## Task 8: PUT Replaces, PATCH Changes

### Before

```bash
./demo --scenario put
```

```
Order 4 has a note for the courier and a phone number. An older version of the mobile app
does not know those two fields. Its "change address" screen sends a PUT with the fields it knows.

  -> PUT /api/orders/4
     {
       "customer": "Liam Brown",
       "product": "MONITOR",
       "quantity": 2,
       "shippingAddress": "1 New Street, Cardiff"
     }
  <- 200 OK
     Content-Type: application/json
     {
       "id": 4,
       "customer": "Liam Brown",
       "product": "MONITOR",
       "quantity": 2,
       "total": 400.00,
       "status": "PAID",
       "shippingAddress": "1 New Street, Cardiff",
       "notes": null,
       "contactPhone": null
     }

  In the database:
     order 4  shipping_address: 9 Castle Road, Cardiff -> 1 New Street, Cardiff
     order 4  notes: Gift. No invoice in the box -> (empty)
     order 4  contact_phone: 029 2018 0002 -> (empty)

Fields the client wanted to change:   1  (shippingAddress)
Columns that changed in the database: 3

RESULT: DATA LOST. PUT replaced the whole order. Not in the body, changed anyway: notes, contact_phone.
```

The client wanted to change one thing. Three columns changed. The note "Gift. No invoice in the
box" is gone.

### Is the PUT broken?

No. This is what PUT means:

> **PUT**: store the representation in this body at this URL. It **replaces** what was there.

The body had no `notes`. So the order now has no notes. The server did what it was told. A client
that wants to keep the notes has to send them back.

(If you ran this scenario before Task 7 you saw five columns change: `status` and `internal_note`
went too. That part was a server bug, and you fixed it. What is left is the meaning of PUT.)

The problem is that the client had no other choice. There is no way to say "change the address and
leave the rest alone":

```bash
./demo --scenario patch
```

```
  -> PATCH /api/orders/4
     {
       "shippingAddress": "1 New Street, Cardiff"
     }
  <- 405 Method Not Allowed
     Content-Type: application/problem+json
     Allow: GET, DELETE, PUT
     {
       "detail": "Method 'PATCH' is not supported.",
       "instance": "/api/orders/4",
       "status": 405,
       "title": "Method Not Allowed"
     }

  In the database: nothing changed

RESULT: NOT THERE YET. PATCH /api/orders/4 answered 405 and the address did not change.
```

> **PATCH**: apply these changes to what is at this URL. What is not mentioned stays as it is.

### Step 1: a body in which everything is optional

Create `order/OrderPatch.java`:

```java
package com.amigoscode.restapi.order;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record OrderPatch(
        @Pattern(regexp = ".*\\S.*", message = "must not be blank") String customer,
        Product product,
        @Positive Integer quantity,
        @Pattern(regexp = ".*\\S.*", message = "must not be blank") String shippingAddress,
        @Size(max = 200) String notes,
        @Size(max = 30) String contactPhone) {
}
```

Compare it with `OrderRequest`:

- `quantity` is an `Integer`, not an `int`. An `int` cannot be "missing": it would be 0, and you
  could not tell "the client sent 0" from "the client sent nothing".
- No `@NotBlank` and no `@NotNull`: those reject null, and here null is normal. It means "not
  sent".
- A field that **is** sent must still be valid. Most constraints, such as `@Positive`, `@Size` and
  `@Pattern`, skip null and check everything else. `@Pattern(regexp = ".*\\S.*")` means "at least
  one character that is not a space": may be missing, may not be blank.
- Still no `status`, `total` or `internalNote`. PATCH needs the same protection as PUT.

### Step 2: copy only what was sent

In `OrderService`, in place of the `TODO Task 8` comment:

```java
public Order patch(long id, OrderPatch patch) {
    Order order = get(id);
    if (patch.customer() != null) {
        order.setCustomer(patch.customer());
    }
    if (patch.product() != null) {
        order.setProduct(patch.product());
    }
    if (patch.quantity() != null) {
        order.setQuantity(patch.quantity());
    }
    if (patch.shippingAddress() != null) {
        order.setShippingAddress(patch.shippingAddress());
    }
    if (patch.notes() != null) {
        order.setNotes(patch.notes());
    }
    if (patch.contactPhone() != null) {
        order.setContactPhone(patch.contactPhone());
    }
    order.recalculateTotal();
    return orders.save(order);
}
```

Put it next to `replace` and compare the two. `replace` copies every field without asking. `patch`
asks "was it sent?" for each one. That `if` is the whole difference between PUT and PATCH.

### Step 3: the endpoint

In `OrderController`, in place of the `TODO Task 8` comment:

```java
@PatchMapping("/{id}")
public OrderResponse patch(@PathVariable long id, @Valid @RequestBody OrderPatch patch) {
    return OrderResponse.from(service.patch(id, patch));
}
```

Import `org.springframework.web.bind.annotation.PatchMapping`.

### After

```bash
./demo --scenario patch
```

```
  -> PATCH /api/orders/4
     {
       "shippingAddress": "1 New Street, Cardiff"
     }
  <- 200 OK
     Content-Type: application/json
     {
       "id": 4,
       "customer": "Liam Brown",
       "product": "MONITOR",
       "quantity": 2,
       "total": 400.00,
       "status": "PAID",
       "shippingAddress": "1 New Street, Cardiff",
       "notes": "Gift. No invoice in the box",
       "contactPhone": "029 2018 0002"
     }

  In the database:
     order 4  shipping_address: 9 Castle Road, Cardiff -> 1 New Street, Cardiff

Fields the client wanted to change:   1  (shippingAddress)
Columns that changed in the database: 1

RESULT: OK. PATCH changed the address and nothing else.
```

```bash
./check 8
```

```
Task 8: PUT replaces, PATCH changes
  [PASS] PATCH (unit): only the field that was sent changes
  [PASS] PATCH (unit): a new quantity changes the total, because the server works the total out
  [PASS] PATCH (unit): a field that is sent must be valid: quantity 0 is a 400 and nothing changes
  [PASS] PATCH: an order that does not exist returns 404
  [PASS] PUT: a field left out of the body is cleared, because PUT replaces the whole order (it always did)
  [PASS] PATCH: a blank customer returns 400 that names the field, and nothing changes
  [PASS] PATCH: sending only shippingAddress returns 200, changes the address and nothing else
  [PASS] PATCH: status, total and internalNote cannot be changed
  8 of 8 pass
```

`./demo --scenario put` still prints `RESULT: DATA LOST`. It always will: that is PUT doing its job.
The fix was to give clients the right tool, not to change what PUT means.

---

## Task 9: Inspect — What Goes In and What Comes Out

### Step 1: the endpoint table

```bash
./inspect --endpoints
```

```
  METHOD  PATH                     HANDLER                            FINDINGS
  ------  -----------------------  ---------------------------------  -----------------------------
  GET     /admin/pending-orders    AdminPageController.pendingOrders  safe: the GET changed nothing
  GET     /api/orders              OrderController.list               safe: the GET changed nothing
  POST    /api/orders              OrderController.create             ok
  GET     /api/orders/{id}         OrderController.get                safe: the GET changed nothing
  PUT     /api/orders/{id}         OrderController.replace            ok
  PATCH   /api/orders/{id}         OrderController.patch              ok
  DELETE  /api/orders/{id}         OrderController.delete             ok
  POST    /api/orders/{id}/cancel  OrderController.cancel             ok

  8 endpoints, no findings.
```

8 findings when you started, none now. The tool looks at the Java types: no `@RequestBody` is an
`@Entity`, every body with constraints has `@Valid`, and no handler returns an `@Entity`.

### Step 2: ignored, not refused

In `./demo --scenario mass-assignment` (Task 7) the client sent `status`, `total` and
`internalNote`, and got a `200 OK`. The three fields were dropped without a word.

That is Spring Boot's default: Jackson ignores JSON fields it has no place for. You can turn that
around with one line in `application.properties`:

```
spring.jackson.deserialization.fail-on-unknown-properties=true
```

Then the same request answers `400 Bad Request` with "Failed to read request".

| | Ignore unknown fields (the default) | Reject unknown fields |
|---|---|---|
| A client sends a field from a newer version of the API | Works | Breaks |
| A client makes a typo, `"adress"` | Silently ignored. The address does not change and nobody says why | 400, found at once |
| A client tries `"status": "PAID"` | Ignored | 400 |

Most public APIs ignore, so that old and new clients keep working. The security comes from the DTO
either way: the field cannot be set because it does not exist in `OrderRequest`. Know that the
choice exists, and say which one you would make and why.

### Step 3: whose mistake

In `./demo --scenario put` three columns changed and the client wanted one. The server is correct.
The client used the wrong method for a small change. If you own the client: read the order, change
one field, send the **whole** order back, or use PATCH. If you own the server: offer PATCH, and
document that PUT replaces.

### Step 4

`./demo --scenario patch`: one column changed.

### Step 5: PATCH cannot clear a field

```bash
./demo --scenario patch-null
```

```
Order 4 has the note "Gift. No invoice in the box". The client wants to remove it,
and sends a PATCH with "notes": null.

  -> PATCH /api/orders/4
     {
       "notes": null
     }
  <- 200 OK
     Content-Type: application/json
     {
       "id": 4,
       "customer": "Liam Brown",
       "product": "MONITOR",
       "quantity": 2,
       "total": 400.00,
       "status": "PAID",
       "shippingAddress": "9 Castle Road, Cardiff",
       "notes": "Gift. No invoice in the box",
       "contactPhone": "029 2018 0002"
     }

  In the database: nothing changed

RESULT: NOTHING CHANGED. 200 OK, and the note is still there. In this API a null in a PATCH
        means "not sent", so a PATCH cannot clear a field. A PUT can.
```

Once Jackson has built the `OrderPatch` record, `{"notes": null}` and `{}` look the same: `notes`
is null in both. The code reads null as "not sent". So this PATCH can set a field and cannot empty
one.

This is the known weak spot of the simple approach, and a good thing to raise yourself in an
interview. The ways around it:

- **Use PUT to clear.** Send the whole order without the note. That is what this API offers.
- **JSON Merge Patch** (RFC 7396): a standard that says null means "remove this field" and a missing
  field means "leave it". To implement it you must be able to tell the two apart, for example by
  reading the body as a `JsonNode` or a `Map` and checking which keys are present.
- **A wrapper type per field** with three states: not sent, sent as null, sent with a value. Some
  teams use `Optional<String>` fields or a library type for this.

For most APIs "PATCH sets, PUT clears" is enough. Say that you know the limit.

### Step 6: idempotent or not

Send the PUT from step 3 twice. After the first, the order is exactly what the body says. After the
second, it is still exactly what the body says. **PUT is idempotent.**

This PATCH, `{"shippingAddress": "1 New Street, Cardiff"}`, also gives the same result twice. But
PATCH as a method makes no promise, because a patch may be relative: "add 1 to the quantity" gives
a different result every time it is applied. **PATCH is not idempotent by definition**, even though
a PATCH that sets fixed values behaves that way.

### Step 7: three shapes

| Field | `Order` (the row) | `OrderRequest` (POST, PUT) | `OrderPatch` (PATCH) | `OrderResponse` |
|---|---|---|---|---|
| `id` | yes | no: it comes from the URL, or the database | no | yes |
| `customer`, `product`, `quantity`, `shippingAddress` | yes | required | optional | yes |
| `notes`, `contactPhone` | yes | optional | optional | yes |
| `total` | yes | no: the server works it out | no | yes |
| `status` | yes | no: only paying, shipping and cancelling change it | no | yes |
| `internalNote` | yes | no | no | **no** |

`id`, `total` and `status` are read-only: in the response, in no request. `internalNote` is in
nothing a client ever touches.

### What to take away

- The entity is not the API. One record for what comes in, one for what goes out.
- A request DTO is an allow list of what the client may set. That is the fix for mass assignment.
- PUT replaces everything. PATCH changes what is sent.
- PUT is idempotent. PATCH is not promised to be.

---

## Task 10: 201, Location and 204

### Before

```bash
./demo --scenario status-codes
```

```
1. Create an order
  -> POST /api/orders
     {
       "customer": "Nina Patel",
       "product": "MOUSE",
       "quantity": 2,
       "shippingAddress": "8 Queen Square, Bath"
     }
  <- 200 OK
     Content-Type: application/json
     {
       "id": 7,
       ...
     }

2. Delete order 6
  -> DELETE /api/orders/6
  <- 200 OK
     Content-Type: application/json
     {
       "message": "Order 6 deleted"
     }

3. Delete order 6 again
  -> DELETE /api/orders/6
  <- 404 Not Found
     Content-Type: application/problem+json
     {
       "detail": "Order 6 does not exist",
       "instance": "/api/orders/6",
       "status": 404,
       "title": "Order not found"
     }

  In the database:
     order 6  row DELETED
     order 7  NEW ROW: customer='Nina Patel' product=MOUSE quantity=2 total=40.00 status=PENDING

  Create:        got 200, no Location header       should be 201 with a Location header   WRONG
  Delete:        got 200, with a body              should be 204 with no body             WRONG
  Delete again:  got 404                           should be 404: it is already gone      ok

RESULT: WRONG STATUS CODES. 2 of 3 answers tell the client the wrong thing.
```

Everything worked. The row was created, the row was deleted. The status lines are the problem:

- **Create answers `200 OK`.** 200 says "your request was fine". It does not say "something new
  exists now". And the client has to read the body, find `id` and build the URL of the new order
  from what it knows about your URL layout.
- **Delete answers `200 OK` with `{"message": "Order 6 deleted"}`.** A sentence somebody made up, in
  a shape no other endpoint uses. The status code already said it worked.

(The second delete was a 500 when you started. It has been a 404 since Task 4, because `delete`
calls `get`, and `get` now throws `OrderNotFoundException`.)

### Step 1: 201 and Location

In `OrderController`:

```java
@PostMapping
public ResponseEntity<OrderResponse> create(@Valid @RequestBody OrderRequest request) {
    Order order = service.create(request);
    URI location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(order.getId())
            .toUri();
    return ResponseEntity.created(location).body(OrderResponse.from(order));
}
```

Imports: `java.net.URI`, `org.springframework.http.ResponseEntity`,
`org.springframework.web.servlet.support.ServletUriComponentsBuilder`.

- `ResponseEntity.created(location)` sets the status to **201 Created** and the `Location` header.
- `fromCurrentRequest()` starts from the URL of the request that is being handled,
  `http://host/api/orders`, and `.path("/{id}")` adds the new id. You never type the host name.
- The body is still the new order, so the client does not need a second request to see the total
  the server worked out.

### Step 2: 204

```java
@DeleteMapping("/{id}")
@ResponseStatus(HttpStatus.NO_CONTENT)
public void delete(@PathVariable long id) {
    service.delete(id);
}
```

Imports: `org.springframework.http.HttpStatus`,
`org.springframework.web.bind.annotation.ResponseStatus`. Remove the import of `java.util.Map`.

**204 No Content**: it worked, and there is nothing to send. A `void` method with that status sends
no body at all.

### Step 3: decide about the second delete

The first DELETE answers 204. The second answers 404, because the order no longer exists. Both
answers below are defensible, and an interviewer wants to hear the reasoning more than the choice.

| | Second DELETE answers 404 (this project) | Second DELETE answers 204 |
|---|---|---|
| The argument | It is the truth: there is nothing at this URL. A typo in the id is not hidden | The client wanted the order gone, and it is gone. A client that retries after a timeout gets a success |
| The cost | A client that retries after a lost answer sees a 404 and must understand that "already gone" is fine | A DELETE of an id that never existed also looks like a success |

Either way **DELETE is idempotent**: after one call or five, the order is gone and nothing else has
changed. Idempotent describes the state on the server. It does not promise the same status code.

Compare with cancel, which answers 200 twice. A cancelled order still exists and can be shown, so
the second cancel has something to return. A deleted order does not.

### After

```bash
./demo --scenario status-codes
```

```
1. Create an order
  ...
  <- 201 Created
     Content-Type: application/json
     Location: http://localhost:64107/api/orders/7
     {
       "id": 7,
       "customer": "Nina Patel",
       ...
     }

2. Delete order 6
  -> DELETE /api/orders/6
  <- 204 No Content
     (no body)

3. Delete order 6 again
  -> DELETE /api/orders/6
  <- 404 Not Found
  ...

  Create:        got 201, with a Location header   should be 201 with a Location header   ok
  Delete:        got 204, no body                  should be 204 with no body             ok
  Delete again:  got 404                           should be 404: it is already gone      ok

RESULT: OK. 201 with a Location, 204 with no body, 404 for what is already gone.
```

(The port in `Location` is the random port the demo started the application on.)

```bash
./check 10
./check
```

`./check 10` prints 5 of 5. `./check` should now print 56 passes and no failures. Its seven blocks
end in `9 of 9 pass`, `5 of 5 pass`, `12 of 12 pass`, `7 of 7 pass`, `10 of 10 pass`,
`8 of 8 pass` and `5 of 5 pass`.

---

## Task 11: The Status-Code Map

### Step 1: everything at once

```bash
./inspect
```

The endpoint table has no findings, and:

```
  #   THE CLIENT...                             REQUEST                      GOT  SHOULD BE  BODY TYPE     VERDICT
  --  ----------------------------------------  ---------------------------  ---  ---------  ------------  -------
  1   reads an order that does not exist        GET /api/orders/999          404  404        problem+json  ok
  2   uses an id that is not a number           GET /api/orders/abc          400  400        problem+json  ok
  3   asks for a URL that does not exist        GET /api/nope                404  404        problem+json  ok
  4   uses a method the URL does not have       DELETE /api/orders           405  405        problem+json  ok
  5   sends a body that is not JSON             POST /api/orders             400  400        problem+json  ok
  6   sends JSON as text/plain                  POST /api/orders             415  415        problem+json  ok
  7   orders a product that does not exist      POST /api/orders             400  400        problem+json  ok
  8   orders a quantity of -5                   POST /api/orders             400  400        problem+json  ok
  9   filters by a status that does not exist   GET /api/orders?status=DONE  400  400        problem+json  ok
  10  cancels with a GET                        GET /api/orders/2/cancel     405  405        problem+json  ok
  11  cancels a shipped order                   POST /api/orders/5/cancel    409  409        problem+json  ok
  12  creates an order                          POST /api/orders             201  201        json          ok
  13  replaces an order (PUT)                   PUT /api/orders/2            200  200        json          ok
  14  sets status and total in a PUT            PUT /api/orders/2            200  200        json          ok
  15  reads an order that has an internal note  GET /api/orders/4            200  200        json          ok
  16  changes one field (PATCH)                 PATCH /api/orders/4          200  200        json          ok
  17  deletes an order                          DELETE /api/orders/6         204  204        (no body)     ok
  18  deletes the same order again              DELETE /api/orders/6         404  404        problem+json  ok

  18 of 18 answers are right.
```

1 of 18 when you started.

### Step 2: the map

Nine status codes. This is the whole vocabulary of the API.

| Code | Name | This API returns it when | Who wrote it |
|---|---|---|---|
| 200 | OK | A read, a PUT, a PATCH or a cancel worked, and the order is in the body | you |
| 201 | Created | A POST created an order. `Location` has its URL | you |
| 204 | No Content | A DELETE worked. No body | you |
| 400 | Bad Request | The body cannot be read, a value has the wrong type, or validation failed (then `errors` lists the fields) | Spring, and you for `errors` |
| 404 | Not Found | No order with that id, or no such URL | you, and Spring for unknown URLs |
| 405 | Method Not Allowed | The URL exists, the method does not. `Allow` lists what works | Spring |
| 409 | Conflict | The request is fine, the state of the order does not allow it: cancelling a shipped order | you |
| 415 | Unsupported Media Type | The body is not `application/json` | Spring |
| 500 | Internal Server Error | A bug. No details in the body, the stack trace in the log | you |

Codes this API would need next, in a real system: 401 (who are you?), 403 (you may not do this),
429 (too many requests).

### Step 3: per endpoint

| Endpoint | Success | Can also answer |
|---|---|---|
| `GET /api/orders` | 200 | 400 (unknown `status`) |
| `GET /api/orders/{id}` | 200 | 400 (id not a number), 404 |
| `POST /api/orders` | 201 + `Location` | 400, 415 |
| `PUT /api/orders/{id}` | 200 | 400, 404, 415 |
| `PATCH /api/orders/{id}` | 200 | 400, 404, 415 |
| `DELETE /api/orders/{id}` | 204 | 404 |
| `POST /api/orders/{id}/cancel` | 200 | 404, 409 |

Any of them can answer 405 for the wrong method and 500 for a bug.

### Step 4: what one error format buys the client

When you started, a client of this API had to know three shapes:

```
{"error": "...", "message": "...", "trace": [...]}          every failure, always with 500
{"timestamp": "...", "status": 400, "error": "...", ...}    what Spring sends when nothing is configured
{"message": "Order 6 deleted"}                              a success that looks like an error format
```

And since every failure was a 500, it could not tell them apart anyway. The only safe code to write
was "something failed, show a generic message".

Now there is one shape, and the status code means something:

```
if the Content-Type is application/problem+json:
    read status, title, detail
    if there is an "errors" list: mark those fields in the form
4xx: show the detail to the user, do not retry
5xx: say "try again later", retry with a pause
```

Written once, it works for every endpoint, including the ones added next year. That is the gain:

- **The client can react correctly.** Fix the input, or wait and retry. It knows which.
- **One piece of error-handling code** in each client, not one per endpoint.
- **Forms can show errors next to fields**, because `errors` names them.
- **Monitoring works.** 4xx is the clients' problem, 5xx is yours. An alert on 5xx means something.
- **Nothing internal leaves the server.** The shape has no place for a stack trace.
- **It is a standard.** RFC 9457 is not something this team made up, so tools and libraries know it.

### Step 5: the second DELETE

See the table in Task 10. 404 is the truth about the URL. 204 is kinder to a client that retries.
This project ships 404, because hiding "that id never existed" behind a success is the worse of the
two surprises. If your clients retry deletes automatically, 204 is a fair choice.

### Step 6: what is safe to send again

| Endpoint | Safe to repeat after a timeout? |
|---|---|
| every GET | yes: safe |
| `PUT /api/orders/{id}` | yes: idempotent |
| `DELETE /api/orders/{id}` | yes: idempotent (the repeat may answer 404) |
| `POST /api/orders/{id}/cancel` | yes: this code made it idempotent |
| `PATCH /api/orders/{id}` | here yes, because it only sets values. Not promised in general |
| `POST /api/orders` | **no**: a repeat creates a second order |

To make `POST /api/orders` safe to repeat, the client sends an `Idempotency-Key` header and the
server remembers which keys it has already handled. That is its own project:
[`../idempotency`](../idempotency/).

### Step 7: changing the API later

Renaming `shippingAddress` to `address` breaks every client that sends or reads the old name. The
order of preference:

1. **Do not break.** Add `address`, keep `shippingAddress`, accept both for a while. Adding a field
   is safe as long as clients ignore fields they do not know.
2. **If you must break, version.** The usual ways: in the path (`/api/v2/orders`), which is easy to
   see, route and cache; or in a header or media type, which keeps the URLs stable and is harder to
   try out in a browser.
3. **Keep few versions alive**: the current one and the previous one, with a date on which the old
   one is switched off, announced early. Every version you keep is code you must test, fix and
   secure.

### How to say it in an interview

"Cancelling an order is a GET. Is that a problem?"

> "Yes. GET is a safe method: it is only allowed to read. The whole web relies on that. Browsers
> prefetch links, crawlers and link checkers follow them, mail scanners open them, caches and
> proxies repeat them. Each of those would cancel an order without anyone clicking. A login does not
> help, because the logged-in user's own browser sends the request. Cancelling changes state, so it
> needs a method that is allowed to: I would make it `POST /orders/{id}/cancel`, because it is an
> action with its own business rule. DELETE would mean the order is removed, and it is not. I would
> also make it idempotent, so that a client that lost the answer can send it again."

"How do you handle errors in a Spring REST API?"

> "With one `@RestControllerAdvice`. It extends `ResponseEntityExceptionHandler`, so the exceptions
> Spring throws itself keep their correct codes: 400 for a body it cannot read, 405 for a wrong
> method, 404, 415. I add one `@ExceptionHandler` per exception of my own domain: not found is 404,
> a state conflict is 409. For validation I put `@Valid` on the request body and return a 400 that
> lists every invalid field. All errors have the same shape, RFC 9457 `ProblemDetail`, sent as
> `application/problem+json`. A last handler for `Exception` returns a 500 with no details and logs
> the stack trace. The rule behind it: 4xx when the client has to fix something, 5xx when we do,
> and never a class name, SQL or a stack trace in a response."

Then be ready for the follow-ups in "Questions to Ask Afterwards" at the end of
[`INTERVIEW_TASKS.md`](INTERVIEW_TASKS.md).

---

## Clean up

```bash
docker compose down
```
