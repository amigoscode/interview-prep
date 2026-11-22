package com.amigoscode.transactional.bill;

/** A CHECKED exception: it extends Exception, not RuntimeException. */
public class BillRejectedException extends Exception {

    public BillRejectedException(String message) {
        super(message);
    }
}
