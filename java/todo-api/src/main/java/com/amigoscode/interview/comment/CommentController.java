package com.amigoscode.interview.comment;

import com.amigoscode.interview.todo.Todo;
import com.amigoscode.interview.todo.TodoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

// No service layer — repository injected directly (intentional issue)
@RestController
@RequestMapping("/api/comments")
public class CommentController {

    private final CommentRepository commentRepository;
    private final TodoRepository todoRepository;

    public CommentController(CommentRepository commentRepository,
                             TodoRepository todoRepository) {
        this.commentRepository = commentRepository;
        this.todoRepository = todoRepository;
    }

    @GetMapping("/todo/{todoId}")
    public ResponseEntity<List<Comment>> getCommentsByTodoId(@PathVariable Long todoId) {
        List<Comment> comments = commentRepository.findByTodoId(todoId);
        return ResponseEntity.ok(comments);
    }

    @PostMapping("/todo/{todoId}")
    public ResponseEntity<Comment> createComment(
            @PathVariable Long todoId,
            @RequestBody Comment comment) {
        Todo todo = todoRepository.findById(todoId)
                .orElseThrow(() -> new RuntimeException("Todo not found with id: " + todoId));
        comment.setTodo(todo);
        comment.setCreatedAt(LocalDateTime.now());
        Comment saved = commentRepository.save(comment);
        return new ResponseEntity<>(saved, HttpStatus.CREATED);
    }

}
