package com.amigoscode.karat.snippets;

import java.util.Date;

/**
 * Theory question: "What design pattern is this program missing?" Defensive copying: {@link Date}
 * is mutable, so handing out the field lets any caller change the employee's history. In new code
 * use {@link java.time.LocalDate}, which is immutable, and the copies disappear.
 */
public class Employee {

    private final Date joiningDate;

    public Employee(Date joiningDate) {
        // Copy on the way in, so the caller's Date cannot change us later.
        this.joiningDate = new Date(joiningDate.getTime());
    }

    /** The day the employee joined. Nobody outside this class may change it. */
    public Date getJoiningDate() {
        // Copy on the way out, so a caller's setTime() cannot change us either.
        return new Date(joiningDate.getTime());
    }
}
