package com.amigoscode.fetching.sql;

import java.util.ArrayList;
import java.util.List;
import org.hibernate.resource.jdbc.spi.StatementInspector;

/**
 * Hibernate calls {@link #inspect} once for every SQL statement it is about to send. This class
 * only counts and remembers them, so the demo, the inspect tool and the tests can say
 * "this request ran N statements". It is switched on in application.properties.
 *
 * <p>A teaching tool: do not ship a static, shared recorder like this in a real application.
 */
public class SqlRecorder implements StatementInspector {

    /** Statements after this many are still counted, but their text is not kept. */
    private static final int KEEP = 200_000;

    private static final List<String> STATEMENTS = new ArrayList<>();
    private static int count;

    @Override
    public String inspect(String sql) {
        synchronized (STATEMENTS) {
            count++;
            if (STATEMENTS.size() < KEEP) {
                STATEMENTS.add(sql);
            }
        }
        return sql;
    }

    /** Forget everything. Call this right before the thing you want to measure. */
    public static void reset() {
        synchronized (STATEMENTS) {
            STATEMENTS.clear();
            count = 0;
        }
    }

    /** How many statements ran since the last {@link #reset()}. */
    public static int count() {
        synchronized (STATEMENTS) {
            return count;
        }
    }

    /** The statements since the last {@link #reset()}, in the order they ran. */
    public static List<String> statements() {
        synchronized (STATEMENTS) {
            return List.copyOf(STATEMENTS);
        }
    }
}
