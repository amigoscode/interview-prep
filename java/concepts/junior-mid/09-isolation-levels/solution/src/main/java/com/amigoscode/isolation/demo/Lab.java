package com.amigoscode.isolation.demo;

import com.amigoscode.isolation.demo.Outcome.Verdict;
import com.amigoscode.isolation.demo.Session.Pending;
import com.amigoscode.isolation.demo.Session.Result;
import java.io.PrintStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Properties;

/**
 * Plain SQL in two sessions, one statement at a time, in a fixed order. No Spring, no Hibernate:
 * this is what Postgres itself does at each isolation level. The demo prints it, and the tests
 * assert on the {@link Outcome}, so everything the demo shows is also checked.
 */
public final class Lab {

    /**
     * @param forUpdate    write-skew only: the read locks the rows it reads (SELECT ... FOR UPDATE)
     * @param pauseSeconds hold both transactions open for this long in the middle, for ./inspect
     */
    public record Options(boolean forUpdate, int pauseSeconds) {

        public static final Options NONE = new Options(false, 0);
    }

    private static final int WITHDRAWAL = 800;

    private final String url;
    private final String user;
    private final String password;
    private final Timeline timeline;

    /** @param out where to print the timeline, or null to print nothing */
    public Lab(String url, String user, String password, PrintStream out) {
        this.url = url;
        this.user = user;
        this.password = password;
        this.timeline = new Timeline(out);
    }

    public Outcome run(Anomaly anomaly, Level level) {
        return run(anomaly, level, Options.NONE);
    }

    public Outcome run(Anomaly anomaly, Level level, Options options) {
        try (Connection monitor = DriverManager.getConnection(url, user, password)) {
            Db.reset(monitor);
            try (Session a = new Session("A", session(), monitor, timeline);
                 Session b = new Session("B", session(), monitor, timeline)) {
                Run run = new Run(level, options, a, b, monitor);
                return switch (anomaly) {
                    case DIRTY_READ -> dirtyRead(run);
                    case NON_REPEATABLE_READ -> nonRepeatableRead(run);
                    case PHANTOM -> phantom(run);
                    case LOST_UPDATE -> lostUpdate(run);
                    case WRITE_SKEW -> writeSkew(run);
                };
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * A connection that sends every statement as plain text, the way psql does, instead of as a
     * prepared statement. Nothing is left open on the server between two statements, so ./inspect
     * shows exactly which sessions are holding a snapshot.
     */
    private Connection session() throws SQLException {
        Properties properties = new Properties();
        properties.setProperty("user", user);
        properties.setProperty("password", password);
        properties.setProperty("preferQueryMode", "simple");
        return DriverManager.getConnection(url, properties);
    }

    /** B changes a balance and does not commit. Can A see the change? */
    private Outcome dirtyRead(Run run) {
        Session a = run.a;
        Session b = run.b;
        a.exec(run.begin());
        a.exec("show transaction_isolation");
        b.exec("begin");
        b.exec("update account set balance = 0 where id = 1");
        run.pause();
        Result read = a.exec("select balance from account where id = 1");
        b.exec("rollback");
        a.end();

        if (read.value().equals("0")) {
            return run.outcome(Anomaly.DIRTY_READ, Verdict.HAPPENED, List.of(read.value()), run.balance(1),
                    "DIRTY READ. Session A read 0, a value that was never committed.");
        }
        return run.outcome(Anomaly.DIRTY_READ, Verdict.PREVENTED, List.of(read.value()), run.balance(1),
                "NO DIRTY READ. Session A read " + read.value() + " while B's uncommitted 0 was in the table."
                        + (run.level == Level.READ_UNCOMMITTED
                        ? " Postgres accepts READ UNCOMMITTED and runs it as READ COMMITTED." : ""));
    }

    /** A reads one row twice. B changes it and commits in between. */
    private Outcome nonRepeatableRead(Run run) {
        Session a = run.a;
        Session b = run.b;
        a.exec(run.begin());
        Result first = a.exec("select balance from account where id = 1");
        b.exec("begin");
        b.exec("update account set balance = balance + 100 where id = 1");
        b.exec("commit");
        run.pause();
        Result second = a.exec("select balance from account where id = 1");
        a.end();

        List<String> seen = List.of(first.value(), second.value());
        if (!first.value().equals(second.value())) {
            return run.outcome(Anomaly.NON_REPEATABLE_READ, Verdict.HAPPENED, seen, run.balance(1),
                    "NON-REPEATABLE READ. Session A read the same row twice in one transaction and got "
                            + first.value() + ", then " + second.value() + ".");
        }
        return run.outcome(Anomaly.NON_REPEATABLE_READ, Verdict.PREVENTED, seen, run.balance(1),
                "OK. Session A read " + first.value() + " both times. The committed value is " + run.balance(1)
                        + ", and A will see it in its next transaction.");
    }

    /** A runs the same query twice. B inserts a row that matches it and commits in between. */
    private Outcome phantom(Run run) {
        Session a = run.a;
        Session b = run.b;
        String count = "select count(*) from account where owner = 'alice'";
        a.exec(run.begin());
        Result first = a.exec(count);
        b.exec("begin");
        b.exec("insert into account (id, owner, name, balance) values (3, 'alice', 'holiday', 250)");
        b.exec("commit");
        run.pause();
        Result second = a.exec(count);
        a.end();

        List<String> seen = List.of(first.value(), second.value());
        long rows = run.number(count);
        if (!first.value().equals(second.value())) {
            return run.outcome(Anomaly.PHANTOM, Verdict.HAPPENED, seen, rows,
                    "PHANTOM READ. The same query returned " + first.value() + " rows, then " + second.value()
                            + ": a row appeared in the middle of Session A's transaction.");
        }
        return run.outcome(Anomaly.PHANTOM, Verdict.PREVENTED, seen, rows,
                "OK. Session A counted " + first.value() + " rows both times. The table has " + rows
                        + ", and A will see the new one in its next transaction.");
    }

    /** A and B each withdraw 100 from the same account: read the balance, subtract in the application, write. */
    private Outcome lostUpdate(Run run) {
        Session a = run.a;
        Session b = run.b;
        String read = "select balance from account where id = 1";
        a.exec(run.begin());
        long balanceA = a.exec(read).number();
        b.exec(run.begin());
        long balanceB = b.exec(read).number();
        b.comment(balanceB + " - 100 = " + (balanceB - 100) + ", worked out in the application");
        b.exec("update account set balance = " + (balanceB - 100) + " where id = 1");
        b.exec("commit");
        run.pause();
        a.comment(balanceA + " - 100 = " + (balanceA - 100) + ", worked out in the application");
        Result write = a.exec("update account set balance = " + (balanceA - 100) + " where id = 1");
        a.end();

        List<String> seen = List.of(String.valueOf(balanceA), String.valueOf(balanceB));
        long balance = run.balance(1);
        if (write.failed()) {
            return run.failure(Anomaly.LOST_UPDATE, "A", write, seen, balance,
                    "Nothing was lost: the balance is " + balance + " after one withdrawal. "
                            + "A has to run again in a new transaction.");
        }
        return run.outcome(Anomaly.LOST_UPDATE, Verdict.HAPPENED, seen, balance,
                "LOST UPDATE. Two withdrawals of 100 were paid out, but the balance only went from 500 to "
                        + balance + ".");
    }

    /**
     * A withdraws 800 from account 1, B withdraws 800 from account 2. Each checks that the two
     * accounts together hold at least 800. They write different rows.
     */
    private Outcome writeSkew(Run run) {
        Session a = run.a;
        Session b = run.b;
        String read = run.options.forUpdate()
                ? "select balance from account where owner = 'alice' order by id for update"
                : "select balance from account where owner = 'alice' order by id";

        a.exec(run.begin());
        Result readA = a.exec(read);
        boolean allowedA = run.check(a, readA);
        b.exec(run.begin());
        Pending readB = b.send(read);
        run.waited = readB.waited();
        boolean allowedB = !readB.waited() && run.check(b, readB.result());

        Result failure = null;
        String failed = null;
        if (allowedA) {
            Result write = a.exec("update account set balance = balance - " + WITHDRAWAL + " where id = 1");
            if (write.failed()) {
                failure = write;
                failed = "A";
            }
        }
        if (allowedB) {
            Result write = b.exec("update account set balance = balance - " + WITHDRAWAL + " where id = 2");
            if (write.failed()) {
                failure = write;
                failed = "B";
            }
        }
        run.pause();
        Result commitA = a.end();
        if (commitA.failed()) {
            failure = commitA;
            failed = "A";
        }
        if (readB.waited()) {
            allowedB = run.check(b, readB.result());
            if (allowedB) {
                b.exec("update account set balance = balance - " + WITHDRAWAL + " where id = 2");
            }
        }
        Result commitB = allowedB || b.failed() ? b.end() : b.exec("rollback");
        if (commitB.failed()) {
            failure = commitB;
            failed = "B";
        }

        List<String> seen = List.of(String.valueOf(readA.sum()), String.valueOf(readB.result().sum()));
        long combined = run.number("select sum(balance) from account where owner = 'alice'");
        if (failure != null) {
            return run.failure(Anomaly.WRITE_SKEW, failed, failure, seen, combined,
                    "The rule holds: the accounts hold " + combined + " together after one withdrawal. "
                            + failed + " has to run again in a new transaction, and will then see " + combined + ".");
        }
        if (combined < 0) {
            return run.outcome(Anomaly.WRITE_SKEW, Verdict.HAPPENED, seen, combined,
                    "WRITE SKEW. Both sessions saw 1000, both withdrew " + WITHDRAWAL + ", both committed. "
                            + "The accounts now hold " + combined + " together. Neither session changed a row the other one changed.");
        }
        return run.outcome(Anomaly.WRITE_SKEW, Verdict.PREVENTED, seen, combined,
                "OK. Session B had to wait for A's row locks, then read " + readB.result().sum()
                        + " and refused its withdrawal. The accounts hold " + combined + " together.");
    }

    /** Everything one run needs. */
    private final class Run {

        private final Level level;
        private final Options options;
        private final Session a;
        private final Session b;
        private final Connection monitor;
        private boolean waited;

        private Run(Level level, Options options, Session a, Session b, Connection monitor) {
            this.level = level;
            this.options = options;
            this.a = a;
            this.b = b;
            this.monitor = monitor;
        }

        String begin() {
            return "begin isolation level " + level.sql();
        }

        /** The application's rule, applied to what a session read: do the accounts together cover the withdrawal? */
        boolean check(Session session, Result read) {
            boolean allowed = read.sum() >= WITHDRAWAL;
            session.comment(read.addition() + " = " + read.sum() + (allowed
                    ? ", enough for " + WITHDRAWAL + ": go ahead"
                    : ", not enough for " + WITHDRAWAL + ": refuse"));
            return allowed;
        }

        void pause() {
            if (options.pauseSeconds() > 0) {
                timeline.note("Paused for " + options.pauseSeconds()
                        + " seconds. Run ./inspect in another terminal now.");
                try {
                    Thread.sleep(options.pauseSeconds() * 1000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        long balance(long accountId) {
            return number("select balance from account where id = " + accountId);
        }

        long number(String sql) {
            try (Statement statement = monitor.createStatement(); ResultSet row = statement.executeQuery(sql)) {
                row.next();
                return row.getLong(1);
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
        }

        Outcome outcome(Anomaly anomaly, Verdict verdict, List<String> seen, long finalValue, String result) {
            return new Outcome(anomaly, level, verdict, seen, null, null, null, waited, finalValue, result);
        }

        Outcome failure(Anomaly anomaly, String session, Result failure, List<String> seen, long finalValue,
                        String whatNow) {
            String message = failure.error().lines().findFirst().orElse("").replaceFirst("^ERROR: ", "");
            Verdict verdict = "40001".equals(failure.sqlState()) ? Verdict.SERIALIZATION_FAILURE : Verdict.PREVENTED;
            return new Outcome(anomaly, level, verdict, seen, session, failure.sqlState(), message, waited, finalValue,
                    "SERIALIZATION FAILURE (SQLSTATE " + failure.sqlState() + "). Postgres cancelled session "
                            + session + ": \"" + message + "\". " + whatNow);
        }
    }
}
