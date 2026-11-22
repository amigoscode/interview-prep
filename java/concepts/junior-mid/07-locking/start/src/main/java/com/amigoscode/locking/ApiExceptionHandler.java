package com.amigoscode.locking;

import com.amigoscode.locking.order.OrderConflictException;
import com.amigoscode.locking.order.SoldOutException;
import com.amigoscode.locking.order.Strategy.UnknownStrategyException;
import com.amigoscode.locking.product.ProductNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Anything not listed here, including a raw optimistic locking failure, becomes a 500. */
@RestControllerAdvice
public class ApiExceptionHandler {

    public record ApiError(String code, String message) {
    }

    @ExceptionHandler(SoldOutException.class)
    public ResponseEntity<ApiError> soldOut(SoldOutException e) {
        return error(HttpStatus.CONFLICT, "SOLD_OUT", e);
    }

    @ExceptionHandler(OrderConflictException.class)
    public ResponseEntity<ApiError> conflict(OrderConflictException e) {
        return error(HttpStatus.CONFLICT, "ORDER_CONFLICT", e);
    }

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ApiError> notFound(ProductNotFoundException e) {
        return error(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", e);
    }

    @ExceptionHandler({UnknownStrategyException.class, IllegalArgumentException.class})
    public ResponseEntity<ApiError> badRequest(RuntimeException e) {
        return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", e);
    }

    private static ResponseEntity<ApiError> error(HttpStatus status, String code, RuntimeException e) {
        return ResponseEntity.status(status).body(new ApiError(code, e.getMessage()));
    }
}
