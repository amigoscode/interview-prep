package com.amigoscode.restapi;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * "One place for all errors." Whatever goes wrong, the client gets a 500 and everything we know.
 *
 * TODO Task 4: this catches every exception, including the ones Spring throws for a bad request,
 * and calls all of them a server error. It also sends class names and stack frames to the client.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handle(Exception e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", e.getClass().getName());
        body.put("message", e.getMessage());
        body.put("trace", Arrays.stream(e.getStackTrace()).limit(3).map(StackTraceElement::toString).toList());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
