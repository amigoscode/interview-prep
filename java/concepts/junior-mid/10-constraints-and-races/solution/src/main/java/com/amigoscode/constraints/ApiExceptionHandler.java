package com.amigoscode.constraints;

import com.amigoscode.constraints.settings.Strategy.UnknownStrategyException;
import com.amigoscode.constraints.subscription.AlreadySubscribedException;
import com.amigoscode.constraints.subscription.SubscriptionNotFoundException;
import com.amigoscode.constraints.user.EmailTakenException;
import com.amigoscode.constraints.user.UserNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Anything not listed here, including a raw DataIntegrityViolationException, becomes a 500. */
@RestControllerAdvice
public class ApiExceptionHandler {

    public record ApiError(String code, String message) {
    }

    @ExceptionHandler(EmailTakenException.class)
    public ResponseEntity<ApiError> emailTaken(EmailTakenException e) {
        return error(HttpStatus.CONFLICT, "EMAIL_TAKEN", e);
    }

    @ExceptionHandler(AlreadySubscribedException.class)
    public ResponseEntity<ApiError> alreadySubscribed(AlreadySubscribedException e) {
        return error(HttpStatus.CONFLICT, "ALREADY_SUBSCRIBED", e);
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiError> userNotFound(UserNotFoundException e) {
        return error(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", e);
    }

    @ExceptionHandler(SubscriptionNotFoundException.class)
    public ResponseEntity<ApiError> subscriptionNotFound(SubscriptionNotFoundException e) {
        return error(HttpStatus.NOT_FOUND, "SUBSCRIPTION_NOT_FOUND", e);
    }

    @ExceptionHandler({UnknownStrategyException.class, IllegalArgumentException.class})
    public ResponseEntity<ApiError> badRequest(RuntimeException e) {
        return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", e);
    }

    private static ResponseEntity<ApiError> error(HttpStatus status, String code, RuntimeException e) {
        return ResponseEntity.status(status).body(new ApiError(code, e.getMessage()));
    }
}
