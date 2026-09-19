# Interview Tasks — Delivery Food System (Code Review & Refactoring)

This is a **code review and refactoring** challenge. The candidate reviews existing code, identifies code smells, proposes improvements, and implements the most important ones. Focus on the `com/foodtakeway/` package.

Pick **1-2 tasks** matching the candidate's level. Let them explore the codebase for **~10 minutes** before starting.

---

## Junior Level (15-20 min each)

### Task 1: Fix Encapsulation Issues

**Context:** Look at `Order.java` and `OrderRequest.java`. The fields have default (package-private) access and there are no getters/setters.

**What to do:**
1. Make fields `private` in `Order` and `OrderRequest`
2. Add proper getters and setters (or use Lombok)
3. Add the missing `@Id` annotation on `Order` for MongoDB compatibility

**Acceptance Criteria:**
- All fields are `private`
- Getters/setters are available (via Lombok or manually)
- `Order` has an `@Id`-annotated field
- The app still compiles and works

**Hints:**
- The project already has Lombok as a dependency — use `@Data` or `@Getter`/`@Setter`
- Look at `Event.java` in the `examples` package for a clean Lombok example
- `OrderService` accesses fields directly — it will need to use getters/setters after your change

---

### Task 2: Replace System.out.println with Proper Logging

**Context:** The codebase uses `System.out.println()` everywhere for logging. This is a common code smell.

**What to do:**
1. Add SLF4J logger to `OrderService` and `OrderApi`
2. Replace all `System.out.println()` calls with appropriate log levels
3. Use parameterized messages (e.g., `log.info("Order placed: {}", orderId)`)

**Acceptance Criteria:**
- No `System.out.println()` in the main package
- Uses `LoggerFactory.getLogger()` or Lombok's `@Slf4j`
- Log levels are appropriate (`info` for business events, `error` for failures)

**Hints:**
- SLF4J is included via Spring Boot — no extra dependency needed
- Lombok provides `@Slf4j` annotation which creates a `log` field automatically
- Use `log.info()`, `log.warn()`, `log.error()` instead of `System.out.println()`

---

### Task 3: Add Input Validation

**Context:** `POST /order` accepts any body — even empty or negative amounts. There's no validation.

**What to do:**
1. Add validation annotations to `OrderRequest` (`@NotBlank`, `@NotNull`, `@Positive`)
2. Add `@Valid` to the controller method parameter
3. Return `400` with a meaningful error message for invalid input

**Acceptance Criteria:**
- `userEmail` is required and not blank
- `amount` must be positive
- Invalid requests return `400` with field-level error details

**Hints:**
- The `jakarta.validation-api` dependency is already in the POM
- You may need `spring-boot-starter-validation` for runtime support
- Look into `@ExceptionHandler` for clean error responses

---

### Task 4: Improve HTTP Responses

**Context:** The `OrderApi` returns HTTP `200` for order creation and a plain string `"Order Placed!"`. This doesn't follow REST conventions.

**What to do:**
1. Return `201 Created` for successful order placement
2. Return a proper JSON response (not a plain string) — include order ID and status
3. Remove the `/hello` test endpoint

**Acceptance Criteria:**
- `POST /order` returns `201` with a JSON body containing order details
- Response includes at least: `orderId`, `amount`, `status`
- No more plain string responses

**Hints:**
- Use `HttpStatus.CREATED` or `ResponseEntity.status(201).body(...)`
- Create a response DTO class (e.g., `OrderResponse`)
- The `OrderService.placeOrder()` currently returns `void` — you'll need to change it to return the order

---

## Mid-Level (20-25 min each)

### Task 5: Separate OrderRequest DTO into Its Own File

**Context:** `OrderRequest` is a nested class inside `OrderApi.java`. The code organization is poor — DTOs and controllers are mixed together.

**What to do:**
1. Extract `OrderRequest` into its own file
2. Create a proper package structure (e.g., `dto/` or `request/`)
3. Add proper access modifiers, constructors, and consider using Lombok
4. Discuss: What other DTOs might be needed?

**Acceptance Criteria:**
- `OrderRequest` lives in its own file with proper package
- Has private fields with getters/setters or Lombok annotations
- Controller still works with the extracted class

**Hints:**
- Follow Java conventions — one public class per file
- Think about `OrderResponse` too if you have time
- The `examples/event/Event.java` shows good DTO structure

---

### Task 6: Add Proper Persistence with MongoDB

**Context:** Orders are stored in an `ArrayList<Order>` in the service. MongoDB is configured and available (see `examples/ExampleMongoDB.java`), but it's not used for orders.

**What to do:**
1. Create an `OrderRepository` extending `MongoRepository`
2. Update `OrderService` to use the repository instead of `ArrayList`
3. Add the missing `@Id` annotation to `Order` if not already done
4. Verify orders persist across requests

**Acceptance Criteria:**
- Orders are saved to MongoDB
- `OrderRepository` follows Spring Data conventions
- The in-memory `ArrayList` is removed
- Orders survive application restart (when using Docker MongoDB)

**Hints:**
- Look at `ExampleMongoDB.java` for a working MongoDB example
- `MongoRepository<Order, String>` provides `save()`, `findAll()`, `findById()` out of the box
- Make sure Docker Compose is running (`docker-compose up -d`)

---

### Task 7: Fix Order ID Generation

**Context:** Order IDs are generated with `"ORD" + random.nextInt(1000)`. This has a high collision risk and is not production-ready.

**What to do:**
1. Replace the random ID generation with a proper strategy
2. Ensure uniqueness of order IDs
3. Discuss trade-offs of different ID strategies

**Acceptance Criteria:**
- Order IDs are guaranteed unique
- ID format is meaningful and readable
- No `Random` used for ID generation

**Hints:**
- `UUID.randomUUID()` is the simplest approach
- If using MongoDB, you could use its auto-generated `ObjectId`
- Discuss: sequential IDs vs UUIDs vs custom formats — what are the trade-offs?

---

### Task 8: Extract Business Logic — Discount Rules

**Context:** Discount logic is hardcoded inline in `processOrder()`: `if (order.amount > 100) order.amount *= 0.9`. This is brittle and hard to test.

**What to do:**
1. Extract discount logic into a dedicated class (e.g., `DiscountService` or `DiscountCalculator`)
2. Make the threshold and percentage configurable
3. Write unit tests for the discount rules

**Acceptance Criteria:**
- Discount logic is in a separate, testable class
- Threshold (100) and percentage (10%) are not hardcoded
- Unit tests cover: amount > threshold, amount = threshold, amount < threshold

**Hints:**
- Use `@Value` with `application.yaml` properties for configuration
- Consider the Strategy pattern if you want to support multiple discount types
- Focus on single responsibility — one class, one reason to change

---

### Task 9: Write Unit Tests for OrderService

**Context:** The only test is `contextLoads()`. There's no business logic test coverage.

**What to do:**
1. Write unit tests for `OrderService.placeOrder()`
2. Test the discount logic (orders above and below 100 EUR)
3. Mock dependencies if needed

**Acceptance Criteria:**
- Tests run with `./mvnw test`
- At least 3 test cases covering different scenarios
- Tests verify: order creation, discount applied, discount not applied

**Hints:**
- Use `@ExtendWith(MockitoExtension.class)` for mocking
- Be careful — `placeOrder()` calls `Thread.sleep(10000)` which will slow tests
- Consider refactoring the `longRunningOrderProcess()` out of the Order or making it testable

---

## Senior Level (25-30 min each)

### Task 10: Decouple Order Processing with Async/Event-Driven Architecture

**Context:** `placeOrder()` synchronously calls `processOrder()` which includes a 10-second blocking operation (`Thread.sleep`). This blocks the HTTP request thread and doesn't scale.

**What to do:**
1. Decouple order placement from order processing
2. Use an asynchronous approach — Spring `@Async`, Kafka events, or `CompletableFuture`
3. The API should return immediately after placing the order

**Acceptance Criteria:**
- `POST /order` returns instantly (< 1 second)
- Order processing happens asynchronously in the background
- The order is initially saved as "unprocessed" and later marked as "processed"
- Discuss: What happens if processing fails? How would you add retry/dead-letter?

**Hints:**
- The `examples/ExampleKafka.java` shows a working Kafka producer/consumer pattern
- Spring's `@Async` with `@EnableAsync` is the simplest approach
- For Kafka: produce an "OrderPlaced" event, consume it in a separate listener that calls `processOrder()`
- Think about idempotency — what if the same order is processed twice?

---

### Task 11: Design a Proper Layered Architecture

**Context:** The entire application is flat — entity, service, controller, and DTO all in one package with poor separation of concerns.

**What to do:**
1. Propose and implement a proper package/layer structure
2. Separate concerns: API layer, service layer, domain layer, persistence layer
3. Add interfaces where appropriate (e.g., `OrderService` interface + implementation)
4. Discuss: How would this architecture support adding new features (e.g., restaurants, menus, delivery tracking)?

**Acceptance Criteria:**
- Clear package structure with separation of concerns
- Controller only handles HTTP, service handles business logic, repository handles persistence
- Domain objects are separate from API DTOs
- Discuss extensibility and where new features would fit

**Hints:**
- Common structures: by-layer (`controller/`, `service/`, `repository/`) or by-feature (`order/`, `restaurant/`)
- Discuss trade-offs of each approach
- Consider: Where does the discount logic go? Where does event dispatching go?
- Think about the Dependency Inversion Principle — inner layers shouldn't depend on outer layers

---

### Task 12: Integrate Kafka for Event-Driven Processing

**Context:** The project has Kafka (Redpanda) infrastructure and example code, but the Order flow doesn't use it. Events are just printed to console with `System.out.println`.

**What to do:**
1. Publish an `OrderPlacedEvent` to Kafka when an order is placed
2. Create a consumer that processes the order asynchronously
3. Publish an `OrderProcessedEvent` when processing completes
4. Discuss: How would you handle failures, retries, and dead-letter topics?

**Acceptance Criteria:**
- Order placement publishes to a Kafka topic
- A listener consumes and processes orders asynchronously
- Events are proper DTOs (not strings) serialized as JSON
- Processing completion triggers a second event
- `POST /order` returns instantly

**Hints:**
- Reference `ExampleKafka.java` for producer/consumer patterns
- Use the existing `Event.java` as a base or create domain-specific event classes
- Consider event schema: what fields should `OrderPlacedEvent` contain?
- Docker Compose must be running for Kafka to work
