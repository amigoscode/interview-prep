# Interview Tasks

Tasks for interviewers to assign during live coding sessions. Pick 1-2 tasks based on the candidate's level.

---

## Junior Level (pick 1-2, ~20 min each)

### Task 1: Add Missing Endpoint

**Description:** Implement `GET /api/books/{id}` that returns a single book by its ID.

**Acceptance Criteria:**
- Endpoint returns 200 with the book when found
- Endpoint returns 404 (or appropriate error) when the book doesn't exist
- Method is added to both `BookService` and `BookController`

**Hints:**
- Look at how `AuthorService.getAuthorById()` is implemented
- Follow the same pattern for the book

**Estimated Time:** 15 min

---

### Task 2: Add Input Validation

**Description:** Add input validation to the create book endpoint (`POST /api/books`).

**Acceptance Criteria:**
- Title is required and not blank
- Price must be greater than 0
- ISBN must not be blank
- Validation errors return 400 with meaningful messages

**Hints:**
- Use `jakarta.validation` annotations: `@NotBlank`, `@Positive`, etc.
- Add `@Valid` to the controller method parameter
- Spring Boot's starter-web includes the validation dependency

**Estimated Time:** 20 min

---

### Task 3: Fix the Delete Bug

**Description:** Deleting an author who has books throws a 500 Internal Server Error due to a foreign key constraint violation. Handle this gracefully.

**Acceptance Criteria:**
- Deleting an author with books returns a 409 Conflict (or 400 Bad Request) with a clear error message
- Alternatively: implement cascade delete or reassign books
- No 500 error is thrown

**Hints:**
- Try `DELETE /api/authors/1` and observe the error
- Options: catch the exception, add cascade, or check for books before deleting
- Look at the `Author` entity's relationship with `Book`

**Estimated Time:** 15 min

---

### Task 4: Write Unit Tests

**Description:** Write unit tests for `BookService` using Mockito.

**Acceptance Criteria:**
- Test `getAllBooks()` — verify it returns books from the repository
- Test `createBook()` — verify it saves and returns the book
- Test `deleteBook()` — verify it calls `deleteById`
- Use `@Mock` and `@InjectMocks`

**Hints:**
- Look at how `@ExtendWith(MockitoExtension.class)` works
- Use `when(...).thenReturn(...)` to stub repository methods
- Use `verify(...)` to check interactions

**Estimated Time:** 20 min

---

## Mid-Level (pick 1-2, ~25 min each)

### Task 5: Search and Filter

**Description:** Add `GET /api/books/search?title=&genre=&authorName=` with flexible filtering.

**Acceptance Criteria:**
- All query parameters are optional
- Results match on partial title (case-insensitive)
- Results filter by exact genre
- Results filter by author name (partial, case-insensitive)
- Returns empty list if no matches

**Hints:**
- Options: custom `@Query`, derived query methods, or JPA Specifications
- Consider using `LIKE` with `%` for partial matching
- Spring Data supports `@Query` with JPQL

**Estimated Time:** 25 min

---

### Task 6: Pagination and Sorting

**Description:** Add pagination support to `GET /api/books` and `GET /api/reviews/book/{bookId}`.

**Acceptance Criteria:**
- Endpoints accept `page`, `size`, and `sort` query parameters
- Response includes pagination metadata (total pages, total elements, current page)
- Default page size is 10
- Return type uses Spring's `Page` wrapper

**Hints:**
- Use `Pageable` parameter in controller methods
- Spring Data repositories already support `findAll(Pageable)`
- `PageRequest.of(page, size, Sort.by(...))` creates a `Pageable`

**Estimated Time:** 20 min

---

### Task 7: Global Error Handling

**Description:** Create a `@ControllerAdvice` class that handles exceptions globally with consistent error responses.

**Acceptance Criteria:**
- Create an `ErrorResponse` DTO with: timestamp, status, message, path
- Handle `RuntimeException` (404 cases) with proper status codes
- Handle `MethodArgumentNotValidException` for validation errors
- Handle generic `Exception` as 500 fallback
- No stack traces exposed to clients

**Hints:**
- Use `@ExceptionHandler` inside `@ControllerAdvice`
- Access the request path via `HttpServletRequest`
- Return `ResponseEntity<ErrorResponse>`

**Estimated Time:** 25 min

---

### Task 8: Refactor Reviews

**Description:** Extract a `ReviewService`, create request/response DTOs, and add validation.

**Acceptance Criteria:**
- `ReviewService` handles business logic (currently in controller)
- `ReviewRequest` DTO for incoming data with validation (`@NotBlank` reviewerName, `@Min(1) @Max(5)` rating)
- `ReviewResponse` DTO for outgoing data (no circular references)
- Controller is thin — delegates to service
- Existing functionality still works

**Hints:**
- Look at the Author package for the service pattern
- DTOs prevent JPA entities from leaking into the API
- Map between entity and DTO in the service layer

**Estimated Time:** 25 min

---

### Task 9: Integration Tests

**Description:** Write integration tests for `BookController` using `@SpringBootTest` and MockMvc.

**Acceptance Criteria:**
- Test `GET /api/books` returns 200 and a list of books
- Test `POST /api/books` creates a book and returns 201
- Test `DELETE /api/books/{id}` returns 204
- Tests use real Spring context (not mocked)
- Verify JSON response structure

**Hints:**
- Use `@SpringBootTest` with `@AutoConfigureMockMvc`
- `mockMvc.perform(get("/api/books"))` to make requests
- Use `.andExpect(status().isOk())` and `.andExpect(jsonPath(...))`
- Seed data is loaded automatically via `data.sql`

**Estimated Time:** 25 min

---

## Senior Level (pick 1-2, ~30 min each)

### Task 10: Fix N+1 Query Problem

**Description:** Identify and fix the N+1 query problem in the books listing endpoint.

**Acceptance Criteria:**
- Explain what N+1 is and why it's happening (check `BookService.getAllBooks()`)
- Fix using `@EntityGraph` or `JOIN FETCH` query
- Verify fix by checking SQL logs (show-sql is enabled)
- Before: 1 + N queries. After: 1-2 queries

**Hints:**
- Enable `spring.jpa.show-sql=true` (already enabled)
- `@EntityGraph(attributePaths = {"author"})` on repository method
- Or use `@Query("SELECT b FROM Book b JOIN FETCH b.author")`
- Explain trade-offs of eager vs lazy loading

**Estimated Time:** 20 min

---

### Task 11: Add Caching

**Description:** Add Spring Cache to frequently accessed endpoints and explain cache invalidation strategy.

**Acceptance Criteria:**
- Enable caching with `@EnableCaching`
- Cache `getAllBooks()` and `getBookById()` results
- Invalidate cache on create, update, delete
- Explain: when would you use Redis vs in-memory cache?
- Discuss cache invalidation challenges

**Hints:**
- Use `@Cacheable`, `@CacheEvict`, `@CachePut`
- Spring Boot includes a simple in-memory cache provider
- Think about: what happens with pagination + caching?
- Consider: cache-aside pattern vs write-through

**Estimated Time:** 25 min

---

### Task 12: Analytics Endpoint

**Description:** Create `GET /api/books/stats` that returns bookstore analytics.

**Acceptance Criteria:**
- Top 5 highest-rated books (by average review rating)
- Most reviewed books (by review count)
- Average rating by genre
- Response uses a well-structured DTO

**Hints:**
- Use JPQL aggregate queries: `AVG()`, `COUNT()`, `GROUP BY`
- Or use native queries if JPQL is too complex
- Consider creating a dedicated `StatsService`
- Think about performance — should this be cached?

**Estimated Time:** 30 min

---

### Task 13: Add Categories (Many-to-Many)

**Description:** Design and implement a `Category` entity with a many-to-many relationship to `Book`.

**Acceptance Criteria:**
- `Category` entity with id, name, description
- Many-to-many relationship with `Book` (join table)
- CRUD endpoints for categories
- Endpoint to assign/remove categories from books
- Seed data with 5 categories

**Hints:**
- Use `@ManyToMany` with `@JoinTable`
- Think about which side owns the relationship
- Handle the JSON serialization (avoid infinite loops)
- Consider: should deleting a category remove it from books?

**Estimated Time:** 30 min

---

### Task 14: API Versioning or Rate Limiting

**Description:** Discuss and implement an API versioning or rate limiting strategy.

**Acceptance Criteria (pick one):**

**Option A — API Versioning:**
- Implement URL-based versioning (`/api/v1/books`, `/api/v2/books`)
- V2 returns DTOs while V1 returns entities (backward compatible)
- Explain trade-offs: URL vs header vs content-type versioning

**Option B — Rate Limiting:**
- Implement per-client rate limiting (e.g., 100 requests/minute)
- Use a filter or interceptor
- Return 429 Too Many Requests when exceeded
- Explain: how would this work in a distributed system?

**Hints:**
- Versioning: package structure, base path changes
- Rate limiting: `Bucket4j`, `Guava RateLimiter`, or custom filter with `ConcurrentHashMap`
- Discuss production considerations

**Estimated Time:** 30 min

---

## Tips for Interviewers

- Let candidates **explore the codebase for 10 minutes** before assigning tasks
- Point them to the `Author` package as the **reference implementation**
- The `BookService.getAllBooks()` method has intentional N+1 — check if they notice
- `show-sql: true` is enabled so candidates can observe queries in the console
- Encourage candidates to **talk through their approach** before coding
- For senior candidates, focus on **trade-off discussions** as much as implementation
