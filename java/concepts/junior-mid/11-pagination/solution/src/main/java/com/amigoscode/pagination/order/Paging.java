package com.amigoscode.pagination.order;

/** The rules for the "page" and "size" request parameters, shared by every endpoint. */
public final class Paging {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    /** The page size to use: the default when the client sent none, a 400 when it is out of range. */
    public static int size(Integer requested) {
        if (requested == null) {
            return DEFAULT_SIZE;
        }
        if (requested < 1 || requested > MAX_SIZE) {
            throw new InvalidPageRequestException("size must be between 1 and " + MAX_SIZE + ", got " + requested);
        }
        return requested;
    }

    /** The page number to use, starting at 0: the first page when the client sent none. */
    public static int page(Integer requested) {
        if (requested == null) {
            return 0;
        }
        if (requested < 0) {
            throw new InvalidPageRequestException("page must be 0 or more, got " + requested);
        }
        return requested;
    }

    private Paging() {
    }
}
