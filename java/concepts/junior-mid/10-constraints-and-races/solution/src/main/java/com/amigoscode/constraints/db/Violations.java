package com.amigoscode.constraints.db;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Answers the question "which constraint did the database say no to?". Spring wraps Hibernate's
 * ConstraintViolationException, and Hibernate reads the constraint name from the error Postgres
 * sent.
 */
public final class Violations {

    /** True when the constraint the database refused the row for is one of {@code names}. */
    public static boolean isOn(DataIntegrityViolationException e, String... names) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                for (String name : names) {
                    if (name.equals(violation.getConstraintName())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private Violations() {
    }
}
