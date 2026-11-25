package com.amigoscode.pagination.order;

/** The client asked for a page number or a page size the API does not allow. A 400. */
public class InvalidPageRequestException extends RuntimeException {

    public InvalidPageRequestException(String message) {
        super(message);
    }
}
