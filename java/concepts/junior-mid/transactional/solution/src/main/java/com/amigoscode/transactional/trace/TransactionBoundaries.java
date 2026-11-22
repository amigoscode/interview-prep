package com.amigoscode.transactional.trace;

import com.amigoscode.transactional.trace.TransactionTrace.Event;
import com.amigoscode.transactional.trace.TransactionTrace.Kind;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionExecution;
import org.springframework.transaction.TransactionExecutionListener;

/**
 * Spring calls this every time the transaction manager really opens, commits or rolls back a
 * database transaction. It only writes the event into {@link TransactionTrace}. Nothing to change here.
 */
@Component
class TransactionBoundaries implements TransactionExecutionListener {

    private static final ThreadLocal<Boolean> COMMIT_ASKED_FOR = ThreadLocal.withInitial(() -> false);

    @Override
    public void afterBegin(TransactionExecution transaction, Throwable beginFailure) {
        if (beginFailure == null && transaction.isNewTransaction()) {
            add(Kind.BEGIN, transaction, false);
        }
    }

    @Override
    public void beforeCommit(TransactionExecution transaction) {
        COMMIT_ASKED_FOR.set(true);
    }

    @Override
    public void afterCommit(TransactionExecution transaction, Throwable commitFailure) {
        COMMIT_ASKED_FOR.remove();
        if (transaction.isNewTransaction()) {
            add(commitFailure == null ? Kind.COMMIT : Kind.ROLLBACK, transaction, false);
        }
    }

    @Override
    public void afterRollback(TransactionExecution transaction, Throwable rollbackFailure) {
        // A rollback that arrives after a commit was asked for: the transaction had been marked rollback-only.
        boolean commitWasAskedFor = COMMIT_ASKED_FOR.get();
        COMMIT_ASKED_FOR.remove();
        if (transaction.isNewTransaction()) {
            add(Kind.ROLLBACK, transaction, commitWasAskedFor);
        }
    }

    private static void add(Kind kind, TransactionExecution transaction, boolean rollbackOnly) {
        String openedBy = TransactionTrace.shortName(transaction.getTransactionName());
        TransactionTrace.add(new Event(kind, openedBy, true, openedBy, transaction.isReadOnly(), rollbackOnly));
    }
}
