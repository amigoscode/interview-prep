# Bookstore Management API — Interview Practice

A Spring Boot REST API designed as a **live coding interview practice repository**. Students clone the repo, familiarize themselves with the codebase (~10 min), and then the interviewer assigns tasks from [`INTERVIEW_TASKS.md`](INTERVIEW_TASKS.md) based on the candidate's level.

The codebase is intentionally designed with **working features alongside gaps, bugs, and areas for improvement** — giving interviewers realistic scenarios to test candidates.

## Tech Stack

- **Java 25**
- **Spring Boot 4.0.2**
- **Spring Data JPA**
- **H2 Database** (in-memory)
- **Maven**
- **Lombok**

## Getting Started

### Prerequisites

- Java 25+
- Maven 3.9+

### Run the Application

```bash
./mvnw spring-boot:run
```

The app starts on **http://localhost:8080**.

### Run Tests

```bash
./mvnw clean test
```

### H2 Console

Available at **http://localhost:8080/h2-console**

- JDBC URL: `jdbc:h2:mem:interviewdb`
- Username: `sa`
- Password: *(empty)*

## Domain

### Author (fully working CRUD — reference code)
- `GET    /api/authors`        — List all authors
- `GET    /api/authors/{id}`   — Get author by ID
- `POST   /api/authors`        — Create author
- `PUT    /api/authors/{id}`   — Update author
- `DELETE /api/authors/{id}`   — Delete author

### Book (working but with intentional issues)
- `GET    /api/books`          — List all books
- `POST   /api/books`          — Create book
- `DELETE /api/books/{id}`     — Delete book

### Review (partially implemented)
- `GET    /api/reviews/book/{bookId}` — Get reviews for a book
- `POST   /api/reviews/book/{bookId}` — Create a review for a book

## Project Structure

```
src/main/java/com/amigoscode/interview/
├── InterviewPracticeApplication.java
├── author/          ← Fully working CRUD (reference implementation)
│   ├── Author.java
│   ├── AuthorRepository.java
│   ├── AuthorService.java
│   └── AuthorController.java
├── book/            ← Working but with intentional issues
│   ├── Book.java
│   ├── BookRepository.java
│   ├── BookService.java
│   └── BookController.java
└── review/          ← Partially implemented (intentional gaps)
    ├── Review.java
    ├── ReviewRepository.java
    └── ReviewController.java
```

## Seed Data

The application comes pre-loaded with:
- **5 Authors** — Orwell, Rowling, Lee, Herbert, Austen
- **10 Books** — 2 per author across various genres
- **15 Reviews** — Distributed across the books

## Sample Requests

### Create an Author
```bash
curl -X POST http://localhost:8080/api/authors \
  -H "Content-Type: application/json" \
  -d '{"name": "Stephen King", "bio": "American author of horror and suspense."}'
```

### Create a Book
```bash
curl -X POST http://localhost:8080/api/books \
  -H "Content-Type: application/json" \
  -d '{"title": "The Shining", "isbn": "978-0307743657", "genre": "Horror", "price": 9.99, "publishedDate": "1977-01-28", "author": {"id": 1}}'
```

### Create a Review
```bash
curl -X POST http://localhost:8080/api/reviews/book/1 \
  -H "Content-Type: application/json" \
  -d '{"reviewerName": "Test User", "content": "Great book!", "rating": 5}'
```

## Interview Tasks

See [`INTERVIEW_TASKS.md`](INTERVIEW_TASKS.md) for the full task list organized by level (Junior / Mid / Senior).
