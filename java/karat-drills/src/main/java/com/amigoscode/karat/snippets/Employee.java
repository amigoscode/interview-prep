package com.amigoscode.karat.snippets;

import java.util.Date;

/** Theory question: "What design pattern is this program missing?" */
public class Employee {

    private final Date joiningDate;

    public Employee(Date joiningDate) {
        this.joiningDate = joiningDate;
    }

    /** The day the employee joined. Nobody outside this class may change it. */
    public Date getJoiningDate() {
        return joiningDate;
    }
}
