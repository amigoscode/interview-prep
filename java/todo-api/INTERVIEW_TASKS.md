# Interview Tasks — Todo Management API

Tasks are organized by difficulty. Pick **1-2 tasks** matching the candidate's level. Let them explore the codebase for **~10 minutes** before starting.

---

## Junior Level (15-20 min each)

### Task 1: Add Missing Endpoint — Get Todo by ID

**Context:** The `TodoController` is missing `GET /api/todos/{id}`. The `UserController` has a working example.

**What to do:**
1. Add `getTodoById()` to `TodoService`
2. Add `GET /api/todos/{id}` to `TodoController`
3. Handle the case where the todo doesn't exist

**Acceptance Criteria:**
- `GET /api/todos/1` returns the todo with status `200`
- `GET /api/todos/999` returns an appropriate error

**Hints:**
- Look at `UserService.getUserById()` for reference
- Use `orElseThrow()` on the Optional returned by `findById()`

---

### Task 2: Add Missing Endpoint — Update Todo

**Context:** The `TodoController` is missing `PUT /api/todos/{id}`. The `UserController` has a working example.

**What to do:**
1. Add `updateTodo()` to `TodoService`
2. Add `PUT /api/todos/{id}` to `TodoController`

**Acceptance Criteria:**
- `PUT /api/todos/1` with a valid body updates and returns the todo
- Only `title`, `description`, `priority`, and `dueDate` should be updatable
- `PUT /api/todos/999` returns an appropriate error

**Hints:**
- Look at `UserService.updateUser()` for the pattern
- Don't allow updating `completed` here — that's a separate concern (Task 5)

---

### Task 3: Add Input Validation

**Context:** The `POST /api/todos` endpoint accepts any input — even empty bodies. There's no validation.

**What to do:**
1. Add validation annotations to `Todo` entity (e.g., `@NotBlank`, `@NotNull`)
2. Add `@Valid` to the controller method parameter
3. Add the `spring-boot-starter-validation` dependency if needed

**Acceptance Criteria:**
- `title` is required and cannot be blank
- `priority` is required
- `POST` with missing required fields returns `400` with meaningful error messages

**Hints:**
- Look into `jakarta.validation.constraints` annotations
- You may need to add `spring-boot-starter-validation` to `pom.xml`

---

### Task 4: Fix the Delete Bug

**Context:** Try `DELETE /api/users/1` — it returns a `500` error because the user has associated todos.

**What to do:**
1. Investigate why the delete fails (foreign key constraint)
2. Fix it so deleting a user also handles their todos

**Acceptance Criteria:**
- `DELETE /api/users/1` succeeds without a `500` error
- The user's todos are handled appropriately (cascaded delete or reassignment)

**Hints:**
- Look at the `@OneToMany` annotation on `User.todos`
- Consider `cascade` and `orphanRemoval` options
- Think about what should happen to a user's todos when they're deleted

---

### Task 5: Add "Mark Complete" Endpoint

**Context:** There's no way to mark a todo as completed through the API. The `completed` field exists but can't be toggled.

**What to do:**
1. Add a `PATCH /api/todos/{id}/complete` endpoint
2. It should toggle the `completed` field to `true`

**Acceptance Criteria:**
- `PATCH /api/todos/1/complete` sets `completed` to `true` and returns the updated todo
- Calling it on an already completed todo is idempotent (still returns `200`)
- `PATCH /api/todos/999/complete` returns an appropriate error

**Hints:**
- Use `@PatchMapping` in the controller
- This is a good use case for a dedicated service method

---

### Task 6: Write Unit Tests

**Context:** There are no unit tests for the service layer. Only a basic `contextLoads` test exists.

**What to do:**
1. Write unit tests for `TodoService`
2. Mock the `TodoRepository` using `@MockBean` or Mockito
3. Test at least: `getAllTodos()`, `createTodo()`, `deleteTodo()`

**Acceptance Criteria:**
- Tests run and pass with `./mvnw test`
- Repository is mocked (no database calls)
- At least 3 test methods covering the existing service methods

**Hints:**
- Use `@ExtendWith(MockitoExtension.class)` and `@Mock` / `@InjectMocks`
- Use `when(...).thenReturn(...)` to stub repository methods
- Use `verify(...)` to check interactions

---

## Mid-Level (20-25 min each)

### Task 7: Add Search and Filter

**Context:** `GET /api/todos` returns all todos with no way to filter or search.

**What to do:**
1. Add query parameters to filter todos by `completed`, `priority`, and/or `userId`
2. Add a search parameter for `title` (contains, case-insensitive)

**Acceptance Criteria:**
- `GET /api/todos?completed=false` returns only incomplete todos
- `GET /api/todos?priority=HIGH` returns only high-priority todos
- `GET /api/todos?search=groceries` returns todos with "groceries" in the title
- Filters can be combined

**Hints:**
- Consider using Spring Data JPA query methods or `@Query` annotations
- Alternatively, look into `JpaSpecificationExecutor` for dynamic queries
- Keep the controller clean — filtering logic belongs in the service/repository

---

### Task 8: Add Pagination

**Context:** `GET /api/todos` returns all 12 todos in a single response. This won't scale.

**What to do:**
1. Add pagination support to `GET /api/todos`
2. Accept `page` and `size` query parameters

**Acceptance Criteria:**
- `GET /api/todos?page=0&size=5` returns the first 5 todos
- Response includes pagination metadata (total elements, total pages, current page)
- Default behavior (no params) still works

**Hints:**
- Spring Data JPA has built-in pagination via `Pageable` and `Page<T>`
- Look at `PagingAndSortingRepository` or pass `Pageable` to `findAll()`
- Consider returning `Page<Todo>` instead of `List<Todo>`

---

### Task 9: Global Error Handling

**Context:** Errors return inconsistent responses. Some return stack traces, some return Spring's default error format.

**What to do:**
1. Create a `@ControllerAdvice` class for global exception handling
2. Handle common exceptions: `RuntimeException`, `MethodArgumentNotValidException`, etc.
3. Return a consistent error response format

**Acceptance Criteria:**
- All errors return a consistent JSON structure (e.g., `{ "status": 404, "message": "...", "timestamp": "..." }`)
- `GET /api/todos/999` returns a clean `404` instead of a `500` with stack trace
- Validation errors return `400` with field-level details

**Hints:**
- Use `@RestControllerAdvice` and `@ExceptionHandler`
- Create a custom `ErrorResponse` class
- Look at how `UserService` throws `RuntimeException` — consider a custom exception

---

### Task 10: Refactor Comments — Add Service Layer

**Context:** `CommentController` injects repositories directly. There's no service layer, unlike `User` and `Todo`.

**What to do:**
1. Create `CommentService` with the business logic currently in the controller
2. Refactor `CommentController` to use `CommentService`
3. Keep the same API behavior

**Acceptance Criteria:**
- All existing comment endpoints work exactly as before
- Controller only depends on `CommentService` (not repositories)
- Business logic (finding todo, setting timestamp) lives in the service

**Hints:**
- Follow the pattern in `UserService` and `TodoService`
- Move the `todoRepository` dependency to the service
- The controller should be thin — just HTTP concerns

---

### Task 11: Refactor Priority to Enum

**Context:** The `priority` field on `Todo` is a plain `String`. There's no validation — you could set it to `"BANANA"` and it would save.

**What to do:**
1. Create a `Priority` enum (e.g., `LOW`, `MEDIUM`, `HIGH`)
2. Update `Todo` to use the enum instead of `String`
3. Handle invalid priority values gracefully

**Acceptance Criteria:**
- `POST /api/todos` with `"priority": "HIGH"` works
- `POST /api/todos` with `"priority": "BANANA"` returns a `400` error
- Existing seed data still loads correctly

**Hints:**
- Use `@Enumerated(EnumType.STRING)` on the field
- Jackson automatically deserializes strings to enums
- Consider what error message the user sees for invalid values

---

### Task 12: Write Integration Tests

**Context:** There are no integration tests. The only test is `contextLoads()`.

**What to do:**
1. Write integration tests for `TodoController` using `@SpringBootTest` and `MockMvc`
2. Test the full request/response cycle against the H2 database
3. Cover at least: `GET /api/todos`, `POST /api/todos`, `DELETE /api/todos/{id}`

**Acceptance Criteria:**
- Tests run with `./mvnw test`
- Tests use the real Spring context and H2 database
- At least 3 integration tests covering different endpoints
- Tests verify HTTP status codes and response bodies

**Hints:**
- Use `@SpringBootTest` with `@AutoConfigureMockMvc`
- Use `MockMvc` to perform requests and assert responses
- The H2 database is already configured — seed data will be available

---

## Senior Level (25-30 min each)

### Task 13: Fix the N+1 Query Problem

**Context:** Call `GET /api/todos` and observe the SQL logs. You'll see multiple queries being fired — one for the todo list, then additional queries for each todo's user.

**What to do:**
1. Identify the N+1 query issue in `TodoService.getAllTodos()`
2. Fix it using a JPA-optimized approach
3. Verify the fix by checking SQL logs

**Acceptance Criteria:**
- `GET /api/todos` fires **at most 2 queries** (one for todos, one for users) instead of N+1
- The response data is unchanged
- The fix is done at the JPA/repository level (not by restructuring the service)

**Hints:**
- Look at `@EntityGraph` or `JOIN FETCH` queries
- The N+1 happens because lazy-loaded relationships are accessed in a loop
- Compare SQL output before and after your fix

---

### Task 14: Add Due Date Reminder Capability

**Context:** Todos have a `dueDate` field, but there's no way to find upcoming or overdue todos.

**What to do:**
1. Add `GET /api/todos/overdue` — returns todos past their due date that aren't completed
2. Add `GET /api/todos/upcoming?days=3` — returns todos due within the next N days
3. Consider how this could be extended to a scheduled notification system

**Acceptance Criteria:**
- `GET /api/todos/overdue` returns incomplete todos with `dueDate` before today
- `GET /api/todos/upcoming?days=7` returns todos due within the next 7 days
- Default `days` parameter is 3 if not specified
- Completed todos are excluded from both endpoints

**Hints:**
- Use Spring Data JPA query methods with `LocalDate.now()`
- `@Query` with JPQL or native SQL may be needed for date comparisons
- For the discussion part: talk about `@Scheduled`, event-driven architecture, or message queues
