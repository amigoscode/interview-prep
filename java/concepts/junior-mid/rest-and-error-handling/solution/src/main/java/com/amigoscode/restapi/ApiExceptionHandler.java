package com.amigoscode.restapi;

import com.amigoscode.restapi.order.OrderNotFoundException;
import com.amigoscode.restapi.order.OrderStateException;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Every error leaves the API in one shape: RFC 9457 ProblemDetail, sent as application/problem+json.
 *
 * Extending ResponseEntityExceptionHandler gives the right answer for Spring's own exceptions for
 * free: a body that cannot be read is a 400, the wrong method is a 405, an unknown URL is a 404.
 * This class only adds what Spring cannot know: what the exceptions of this application mean.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** One entry of the "errors" list in a validation problem. */
    public record InvalidField(String field, String message) {
    }

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

    /** Validation failed: a 400 that names every invalid field, so the client can fix them all at once. */
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

    /**
     * Anything nobody planned for is a real 500. The details go to the log, where the team can read
     * them. The client gets a message with no class names, no SQL and no stack trace.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail unexpected(Exception e) {
        log.error("Unexpected error", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Something went wrong on our side. It was not your request.");
    }
}
