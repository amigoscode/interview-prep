package com.amigoscode.idempotency;

import com.amigoscode.idempotency.customer.CustomerNotFoundException;
import com.amigoscode.idempotency.key.IdempotencyKeyReusedException;
import com.amigoscode.idempotency.key.InvalidIdempotencyKeyException;
import com.amigoscode.idempotency.payment.InsufficientFundsException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Anything not listed here, including a raw constraint violation, becomes a 500. */
@RestControllerAdvice
public class ApiExceptionHandler {

    public record ApiError(String code, String message) {
    }

    /** 422: the request is well formed, but this key already belongs to a different request. */
    @ExceptionHandler(IdempotencyKeyReusedException.class)
    public ResponseEntity<ApiError> keyReused(IdempotencyKeyReusedException e) {
        return error(422, "IDEMPOTENCY_KEY_REUSED", e);
    }

    @ExceptionHandler(InvalidIdempotencyKeyException.class)
    public ResponseEntity<ApiError> invalidKey(InvalidIdempotencyKeyException e) {
        return error(400, "INVALID_IDEMPOTENCY_KEY", e);
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ApiError> insufficientFunds(InsufficientFundsException e) {
        return error(402, "INSUFFICIENT_FUNDS", e);
    }

    @ExceptionHandler(CustomerNotFoundException.class)
    public ResponseEntity<ApiError> notFound(CustomerNotFoundException e) {
        return error(404, "CUSTOMER_NOT_FOUND", e);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> badRequest(IllegalArgumentException e) {
        return error(400, "BAD_REQUEST", e);
    }

    private static ResponseEntity<ApiError> error(int status, String code, RuntimeException e) {
        return ResponseEntity.status(status).body(new ApiError(code, e.getMessage()));
    }
}
