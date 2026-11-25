package com.amigoscode.sqltraps.db;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The gate in front of ./inspect "<any SELECT>". It lets one read-only SELECT through and refuses
 * everything else, with a message that says why.
 *
 * It is the first of two locks. The second one is the database: ./inspect runs the statement in a
 * READ ONLY transaction, so Postgres itself refuses a write that this check did not recognise.
 */
public final class ReadOnlySql {

    /** Thrown with a message meant for the person who typed the statement. */
    public static final class NotReadOnly extends RuntimeException {
        public NotReadOnly(String message) {
            super(message);
        }
    }

    private static final List<String> FORBIDDEN = List.of(
            "insert", "update", "delete", "merge", "truncate", "drop", "alter", "create", "grant", "revoke",
            "copy", "call", "do", "vacuum", "analyze", "analyse", "explain", "reindex", "cluster", "refresh",
            "lock", "set", "reset", "begin", "commit", "rollback", "savepoint", "prepare", "execute",
            "listen", "notify", "into", "share");

    private static final Pattern WORD = Pattern.compile("[a-z_][a-z0-9_]*");
    private static final Pattern LITERAL = Pattern.compile("'(?:[^']|'')*'|\"(?:[^\"]|\"\")*\"");
    private static final Pattern DANGEROUS_FUNCTION = Pattern.compile(
            "^(pg_terminate|pg_cancel|pg_reload|pg_rotate|pg_advisory|pg_read|pg_ls|set_config|setval|nextval|lo_|dblink).*");

    /** Returns the statement, trimmed and without its closing semicolon, or throws NotReadOnly. */
    public static String check(String sql) {
        String statement = sql == null ? "" : sql.strip();
        while (statement.endsWith(";")) {
            statement = statement.substring(0, statement.length() - 1).stripTrailing();
        }
        if (statement.isEmpty()) {
            throw new NotReadOnly("There is no statement. Give it one SELECT, in double quotes.");
        }
        // What is inside quotes is data, not SQL: 'DELETE ME' is a harmless text value.
        String code = LITERAL.matcher(statement).replaceAll("''").toLowerCase(Locale.ROOT);
        if (code.contains("--") || code.contains("/*")) {
            throw new NotReadOnly("SQL comments are not accepted. Remove the -- or /* */ part.");
        }
        if (code.contains(";")) {
            throw new NotReadOnly("Only one statement at a time. Remove everything after the first semicolon.");
        }
        Matcher words = WORD.matcher(code);
        boolean first = true;
        while (words.find()) {
            String word = words.group();
            if (first && !word.equals("select") && !word.equals("with")) {
                throw new NotReadOnly("Only SELECT is accepted (a statement that starts with SELECT or WITH). "
                        + "This one starts with " + word.toUpperCase(Locale.ROOT) + ".");
            }
            first = false;
            if (FORBIDDEN.contains(word)) {
                throw new NotReadOnly("Only a read-only SELECT is accepted. This statement contains "
                        + word.toUpperCase(Locale.ROOT) + ", which can change or lock data.");
            }
            if (DANGEROUS_FUNCTION.matcher(word).matches()) {
                throw new NotReadOnly("The function " + word + " is not accepted: it does more than read rows.");
            }
        }
        if (first) {
            throw new NotReadOnly("Only SELECT is accepted (a statement that starts with SELECT or WITH).");
        }
        return statement;
    }

    private ReadOnlySql() {
    }
}
