package com.amigoscode.fetching;

import com.amigoscode.fetching.order.FetchPlan.UnknownFetchPlanException;
import com.amigoscode.fetching.order.OrderNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Anything not listed here, including a LazyInitializationException, becomes a 500. */
@RestControllerAdvice
public class ApiExceptionHandler {

    public record ApiError(String code, String message) {
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ApiError> notFound(OrderNotFoundException e) {
        return error(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", e);
    }

    @ExceptionHandler({UnknownFetchPlanException.class, IllegalArgumentException.class})
    public ResponseEntity<ApiError> badRequest(RuntimeException e) {
        return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", e);
    }

    private static ResponseEntity<ApiError> error(HttpStatus status, String code, RuntimeException e) {
        return ResponseEntity.status(status).body(new ApiError(code, e.getMessage()));
    }
}
