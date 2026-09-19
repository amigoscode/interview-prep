package com.amigoscode.ledger;

import java.math.BigDecimal;

/**
 * Story 1 starts here. Add fields and methods as the stories demand; the signatures below are
 * only the shape the interviewer's skeleton usually has. Write the test first when you can.
 */
public final class Ledger {

    public void open(String accountId, BigDecimal openingBalance) {
        throw new UnsupportedOperationException("story 1");
    }

    public BigDecimal balance(String accountId) {
        throw new UnsupportedOperationException("story 1");
    }

    public void deposit(String accountId, BigDecimal amount) {
        throw new UnsupportedOperationException("story 1");
    }

    public void withdraw(String accountId, BigDecimal amount) {
        throw new UnsupportedOperationException("story 1");
    }

    public void transfer(String fromId, String toId, BigDecimal amount) {
        throw new UnsupportedOperationException("story 2");
    }
}
