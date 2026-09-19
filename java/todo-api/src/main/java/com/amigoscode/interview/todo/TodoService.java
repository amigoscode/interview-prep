package com.amigoscode.interview.todo;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class TodoService {

    private final TodoRepository todoRepository;

    public TodoService(TodoRepository todoRepository) {
        this.todoRepository = todoRepository;
    }

    // N+1 problem: fetching all todos triggers separate queries
    // for each todo's user and comments due to lazy loading
    public List<Todo> getAllTodos() {
        List<Todo> todos = todoRepository.findAll();
        // Accessing user username forces lazy load — one query per todo
        todos.forEach(todo -> {
            if (todo.getUser() != null) {
                todo.getUser().getUsername();
            }
        });
        return todos;
    }

    public Todo createTodo(Todo todo) {
        todo.setCompleted(false);
        todo.setCreatedAt(LocalDateTime.now());
        return todoRepository.save(todo);
    }

    public void deleteTodo(Long id) {
        todoRepository.deleteById(id);
    }

    // NOTE: getTodoById() is intentionally missing — interview task #1
    // NOTE: updateTodo() is intentionally missing — interview task #2

}
