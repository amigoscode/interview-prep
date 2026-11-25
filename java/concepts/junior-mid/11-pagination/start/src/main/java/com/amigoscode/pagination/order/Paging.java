package com.amigoscode.pagination.order;

/** The rules for the "page" and "size" request parameters, shared by every endpoint. */
public final class Paging {

    /** The page size to use. A client that sends no size gets everything. */
    public static int size(Integer requested) {
        return requested == null ? Integer.MAX_VALUE : requested;
    }

    /** The page number to use, starting at 0. */
    public static int page(Integer requested) {
        return requested == null ? 0 : requested;
    }

    private Paging() {
    }
}
