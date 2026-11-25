package com.amigoscode.isolation.demo;

import java.util.List;

/**
 * What one run of one anomaly at one isolation level did.
 *
 * @param seen          the values the sessions read, in the order they read them
 * @param failedSession "A" or "B" when the database cancelled one of them, otherwise null
 * @param sqlState      the SQLSTATE of that failure (40001 is a serialization failure), otherwise null
 * @param error         the database's error message, otherwise null
 * @param waited        true when a session had to wait for a lock held by the other one
 * @param finalValue    what is committed at the end: a balance, a row count or a combined balance
 * @param result        the RESULT line
 */
public record Outcome(Anomaly anomaly, Level level, Verdict verdict, List<String> seen, String failedSession,
                      String sqlState, String error, boolean waited, long finalValue, String result) {

    public enum Verdict {
        /** The anomaly happened and nobody was told. */
        HAPPENED,
        /** It did not happen and both sessions finished normally. */
        PREVENTED,
        /** It did not happen because the database cancelled one transaction with SQLSTATE 40001. */
        SERIALIZATION_FAILURE
    }
}
