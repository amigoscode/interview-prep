package com.amigoscode.sqltransfer;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Test helper, given. Runs two database sessions on two threads and lets a test script the
 * interleaving step by step, which is the only way to make a race deterministic.
 *
 * <pre>
 *   TwoSessions.run(db, (s1, s2) -> {
 *       s1.step(c -> read(c, "A"));      // runs on session 1's thread, blocks until done
 *       s2.step(c -> read(c, "A"));      // then on session 2's thread
 *       s1.step(c -> { update(c); c.commit(); });
 *       s2.step(c -> { update(c); c.commit(); });
 *   });
 * </pre>
 *
 * A step that blocks in the database (waiting for a row lock) blocks the test too, which is
 * exactly what you want to observe. Use {@link Session#stepAsync} when you expect that.
 */
public final class TwoSessions {

    public interface Step { void run(Connection c) throws Exception; }
    public interface Script { void run(Session s1, Session s2) throws Exception; }

    public static final class Session implements AutoCloseable {
        private final Connection connection;
        private final ExecutorService thread;

        Session(Connection connection, String name) {
            this.connection = connection;
            this.thread = Executors.newSingleThreadExecutor(r -> new Thread(r, name));
        }

        /** Run on this session's thread and wait for it to finish. */
        public void step(Step step) throws Exception {
            stepAsync(step).get(30, TimeUnit.SECONDS);
        }

        /** Run on this session's thread without waiting; the Future completes when it does. */
        public Future<?> stepAsync(Step step) {
            return thread.submit(() -> { step.run(connection); return null; });
        }

        @Override public void close() throws Exception {
            thread.submit(() -> { try { connection.rollback(); } catch (Exception ignored) { } connection.close(); return null; })
                  .get(5, TimeUnit.SECONDS);
            thread.shutdownNow();
        }
    }

    public static void run(Database db, Script script) throws Exception {
        List<Exception> failures = new ArrayList<>();
        try (Session s1 = new Session(db.connect(), "session-1");
             Session s2 = new Session(db.connect(), "session-2")) {
            try {
                script.run(s1, s2);
            } catch (Exception e) {
                failures.add(e);
            }
        }
        if (!failures.isEmpty()) throw failures.get(0);
    }

    private TwoSessions() { }
}
