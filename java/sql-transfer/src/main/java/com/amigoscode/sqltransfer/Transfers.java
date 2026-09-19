package com.amigoscode.sqltransfer;

import java.math.BigDecimal;

/**
 * Three ways to move money between two rows. Each method opens its own session from the
 * {@link Database}, runs one transaction, and either commits or rolls back.
 */
public final class Transfers {

    private final Database db;

    public Transfers(Database db) {
        this.db = db;
    }

    /** Story 1: read, check, debit, credit, commit. Correct alone, wrong under concurrency. */
    public void transferNaive(String fromId, String toId, BigDecimal amount) {
        throw new UnsupportedOperationException("story 1");
    }

    /** Story 3: SELECT ... FOR UPDATE on both rows, in id order, then the same steps. */
    public void transferPessimistic(String fromId, String toId, BigDecimal amount) {
        throw new UnsupportedOperationException("story 3");
    }

    /** Story 4: version column; UPDATE ... WHERE version = ?; retry when 0 rows changed. */
    public void transferOptimistic(String fromId, String toId, BigDecimal amount) {
        throw new UnsupportedOperationException("story 4");
    }
}
