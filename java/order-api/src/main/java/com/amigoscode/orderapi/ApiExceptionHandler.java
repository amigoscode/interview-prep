package com.amigoscode.orderapi;

import com.amigoscode.orderapi.order.IdempotencyKeyReusedException;
import com.amigoscode.orderapi.order.OrderNotFoundException;
import com.amigoscode.orderapi.order.OrderNotModifiableException;
import com.amigoscode.orderapi.ratelimit.RateLimitExceededException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Map;
import java.util.TreeMap;

/**
 * Story 3. Every error is an RFC 9457 {@code application/problem+json} body. Extending
 * {@link ResponseEntityExceptionHandler} covers Spring's own 400s (malformed JSON, bad UUID in the
 * path, missing or invalid query parameters) the same way.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(OrderNotFoundException.class)
    ProblemDetail notFound(OrderNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Order not found", e.getMessage());
    }

    @ExceptionHandler(OrderNotModifiableException.class)
    ProblemDetail notModifiable(OrderNotModifiableException e) {
        return problem(HttpStatus.CONFLICT, "Order can no longer be changed", e.getMessage());
    }

    @ExceptionHandler(IdempotencyKeyReusedException.class)
    ProblemDetail idempotencyKeyReused(IdempotencyKeyReusedException e) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "Idempotency-Key reused", e.getMessage());
    }

    @ExceptionHandler(RateLimitExceededException.class)
    ResponseEntity<ProblemDetail> rateLimited(RateLimitExceededException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(e.retryAfterSeconds()))
                .body(problem(HttpStatus.TOO_MANY_REQUESTS, "Too many requests", e.getMessage()));
    }

    /** A {@code @Valid} request body failed: answer with a field-by-field {@code errors} map. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new TreeMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return invalid(e, errors, headers, status, request);
    }

    /**
     * Same thing when a handler also has constraints on plain parameters (the Idempotency-Key
     * header, page and size): Spring then validates the whole method and raises this instead.
     */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new TreeMap<>();
        for (ParameterValidationResult result : e.getParameterValidationResults()) {
            if (result instanceof ParameterErrors bodyErrors) {
                bodyErrors.getFieldErrors()
                        .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
            } else {
                String name = result.getMethodParameter().getParameterName();
                result.getResolvableErrors()
                        .forEach(error -> errors.putIfAbsent(name, error.getDefaultMessage()));
            }
        }
        return invalid(e, errors, headers, status, request);
    }

    private ResponseEntity<Object> invalid(Exception e, Map<String, String> errors,
                                           HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "Invalid request", "The request has invalid fields");
        body.setProperty("errors", errors);
        return handleExceptionInternal(e, body, headers, status, request);
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
