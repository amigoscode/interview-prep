package com.amigoscode.transactional.trace;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * A teaching aid, not something you would ship. Every service method starts with
 * {@code TransactionTrace.record("Class.method")}, which asks Spring one question: is a
 * transaction open on this thread right now, and which method opened it? ./inspect prints the
 * answers, and the tests assert on them. Nothing to change here.
 */
public final class TransactionTrace {

    public enum Kind {
        /** A service method was entered. */
        METHOD,
        /** A new database transaction was opened. */
        BEGIN,
        COMMIT,
        ROLLBACK
    }

    /**
     * @param where             for METHOD: the service method. For the others: the method that opened the transaction
     * @param transactionActive METHOD only: was a transaction open when the method was entered?
     * @param transactionName   METHOD only: the method that opened that transaction, or null
     * @param readOnly          was the transaction marked read-only?
     * @param rollbackOnly      ROLLBACK only: had the transaction been marked "rollback-only" before it ended?
     */
    public record Event(Kind kind, String where, boolean transactionActive, String transactionName, boolean readOnly,
                        boolean rollbackOnly) {
    }

    private static final List<Event> EVENTS = new CopyOnWriteArrayList<>();
    private static volatile boolean printing;
    private static int depth;

    private TransactionTrace() {
    }

    /** Call this as the first line of a service method. */
    public static Event record(String method) {
        boolean active = TransactionSynchronizationManager.isActualTransactionActive();
        String name = active ? shortName(TransactionSynchronizationManager.getCurrentTransactionName()) : null;
        boolean readOnly = active && TransactionSynchronizationManager.isCurrentTransactionReadOnly();
        return add(new Event(Kind.METHOD, method, active, name, readOnly, false));
    }

    static synchronized Event add(Event event) {
        EVENTS.add(event);
        if (event.kind() == Kind.COMMIT || event.kind() == Kind.ROLLBACK) {
            depth = Math.max(0, depth - 1);
        }
        if (printing) {
            System.out.println("  " + "    ".repeat(depth) + describe(event));
        }
        if (event.kind() == Kind.BEGIN) {
            depth++;
        }
        return event;
    }

    private static String describe(Event event) {
        return switch (event.kind()) {
            case METHOD -> "inside " + event.where() + "   transaction: "
                    + (event.transactionActive() ? event.transactionName() + (event.readOnly() ? " (read-only)" : "") : "NONE");
            case BEGIN -> "BEGIN     " + event.where() + (event.readOnly() ? " (read-only)" : "");
            case COMMIT -> "COMMIT    " + event.where();
            case ROLLBACK -> "ROLLBACK  " + event.where() + (event.rollbackOnly() ? " (commit was asked for, but the transaction was marked rollback-only)" : "");
        };
    }

    /** Everything recorded since the last {@link #clear()}, oldest first. */
    public static List<Event> events() {
        return List.copyOf(EVENTS);
    }

    public static synchronized void clear() {
        EVENTS.clear();
        depth = 0;
    }

    /** Print every event to the console as it happens (./inspect switches this on). */
    public static void printAsItHappens(boolean on) {
        printing = on;
    }

    /** com.amigoscode.transactional.transfer.TransferService.transfer becomes TransferService.transfer. */
    static String shortName(String transactionName) {
        if (transactionName == null) {
            return "(no name)";
        }
        String[] parts = transactionName.split("\\.");
        return parts.length < 2 ? transactionName : parts[parts.length - 2] + "." + parts[parts.length - 1];
    }
}
