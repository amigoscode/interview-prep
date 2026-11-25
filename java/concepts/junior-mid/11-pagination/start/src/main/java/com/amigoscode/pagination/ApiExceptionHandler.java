package com.amigoscode.pagination;

import com.amigoscode.pagination.order.InvalidCursorException;
import com.amigoscode.pagination.order.InvalidPageRequestException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Anything not listed here becomes a 500. */
@RestControllerAdvice
public class ApiExceptionHandler {

    public record ApiError(String code, String message) {
    }

    @ExceptionHandler(InvalidPageRequestException.class)
    public ResponseEntity<ApiError> invalidPageRequest(InvalidPageRequestException e) {
        return error("INVALID_PAGE_REQUEST", e.getMessage());
    }

    @ExceptionHandler(InvalidCursorException.class)
    public ResponseEntity<ApiError> invalidCursor(InvalidCursorException e) {
        return error("INVALID_CURSOR", e.getMessage());
    }

    /** For example size=abc. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> notANumber(MethodArgumentTypeMismatchException e) {
        return error("BAD_REQUEST", e.getName() + " must be a number");
    }

    private static ResponseEntity<ApiError> error(String code, String message) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiError(code, message));
    }
}
