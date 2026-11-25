package com.amigoscode.pagination.order;

/** The "after" parameter is not a cursor this API handed out. A 400. */
public class InvalidCursorException extends RuntimeException {

    public InvalidCursorException() {
        super("The cursor in 'after' is not valid. Leave 'after' out to start from the newest order.");
    }
}
