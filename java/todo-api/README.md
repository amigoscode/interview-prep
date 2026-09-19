# Todo Management API — Interview Practice

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

- JDBC URL: `jdbc:h2:mem:tododb`
- Username: `sa`
- Password: *(empty)*

## Domain

### User (fully working CRUD — reference code)
- `GET    /api/users`        — List all users
- `GET    /api/users/{id}`   — Get user by ID
- `POST   /api/users`        — Create user
- `PUT    /api/users/{id}`   — Update user
- `DELETE /api/users/{id}`   — Delete user

### Todo (working but with intentional issues)
- `GET    /api/todos`          — List all todos
- `POST   /api/todos`          — Create todo
- `DELETE /api/todos/{id}`     — Delete todo

### Comment (partially implemented)
- `GET    /api/comments/todo/{todoId}` — Get comments for a todo
- `POST   /api/comments/todo/{todoId}` — Create a comment for a todo

## Project Structure

```
src/main/java/com/amigoscode/interview/
├── InterviewPracticeApplication.java
├── user/            ← Fully working CRUD (reference implementation)
│   ├── User.java
│   ├── UserRepository.java
│   ├── UserService.java
│   └── UserController.java
├── todo/            ← Working but with intentional issues
│   ├── Todo.java
│   ├── TodoRepository.java
│   ├── TodoService.java
│   └── TodoController.java
└── comment/         ← Partially implemented (intentional gaps)
    ├── Comment.java
    ├── CommentRepository.java
    └── CommentController.java
```

## Seed Data

The application comes pre-loaded with:
- **4 Users** — johndoe, janedoe, bobsmith, alicewong
- **12 Todos** — 3 per user across various priorities
- **8 Comments** — Distributed across the todos

## Sample Requests

### Create a User
```bash
curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{"username": "newuser", "email": "newuser@example.com"}'
```

### Create a Todo
```bash
curl -X POST http://localhost:8080/api/todos \
  -H "Content-Type: application/json" \
  -d '{"title": "Learn Docker", "description": "Complete the Docker tutorial", "priority": "MEDIUM", "dueDate": "2024-08-01", "user": {"id": 1}}'
```

### Create a Comment
```bash
curl -X POST http://localhost:8080/api/comments/todo/1 \
  -H "Content-Type: application/json" \
  -d '{"content": "Great progress on this!", "authorName": "janedoe"}'
```

## Interview Tasks

See [`INTERVIEW_TASKS.md`](INTERVIEW_TASKS.md) for the full task list organized by level (Junior / Mid / Senior).
