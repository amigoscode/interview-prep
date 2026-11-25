package com.amigoscode.isolation.demo;

import com.amigoscode.isolation.IsolationApplication;
import com.amigoscode.isolation.checkpoint.Checkpoint;
import com.amigoscode.isolation.checkpoint.ScriptedCheckpoint;
import com.amigoscode.isolation.statement.Statement;
import com.amigoscode.isolation.statement.StatementService;
import com.amigoscode.isolation.withdrawal.InsufficientFundsException;
import com.amigoscode.isolation.withdrawal.WithdrawalService;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Runs the application's own code (StatementService, WithdrawalService) as session A, and makes a
 * second session do its work at a checkpoint in the middle of A's transaction. What you see depends
 * on the @Transactional settings in the code.
 */
final class AppDemo {

    private final ConfigurableApplicationContext context;
    private final ScriptedCheckpoint checkpoint;
    private final JdbcTemplate jdbc;
    private final Timeline timeline = new Timeline(System.out);
    private final int pauseSeconds;

    private AppDemo(ConfigurableApplicationContext context, ScriptedCheckpoint checkpoint, int pauseSeconds) {
        this.context = context;
        this.checkpoint = checkpoint;
        this.jdbc = context.getBean(JdbcTemplate.class);
        this.pauseSeconds = pauseSeconds;
    }

    static void run(Anomaly anomaly, int pauseSeconds) throws SQLException {
        ScriptedCheckpoint checkpoint = new ScriptedCheckpoint();
        SpringApplication app = new SpringApplication(IsolationApplication.class);
        app.setBannerMode(Banner.Mode.OFF);
        app.setLogStartupInfo(false);
        // The demo's checkpoint replaces the one that does nothing.
        app.addInitializers(context -> ((GenericApplicationContext) context).registerBean(
                "demoCheckpoint", ScriptedCheckpoint.class, () -> checkpoint, bean -> bean.setPrimary(true)));
        try (ConfigurableApplicationContext context = app.run(
                "--logging.level.root=WARN",
                // A cancelled transaction is shown as one line in the timeline instead of a log entry.
                "--logging.level.org.hibernate.orm.jdbc.error=OFF")) {
            try (Connection connection = Db.connect()) {
                Db.reset(connection);
            }
            AppDemo demo = new AppDemo(context, checkpoint, pauseSeconds);
            System.out.println();
            switch (anomaly) {
                case NON_REPEATABLE_READ, PHANTOM -> demo.statement(anomaly);
                case LOST_UPDATE -> demo.withdrawals(anomaly, 1, 1, 100);
                case WRITE_SKEW -> demo.withdrawals(anomaly, 1, 2, 800);
                case DIRTY_READ -> throw new IllegalStateException("dirty-read is always plain SQL");
            }
            System.out.println();
        }
    }

    /** Session A is StatementService.statement. Session B commits a change between A's queries. */
    private void statement(Anomaly anomaly) throws SQLException {
        System.out.println("Anomaly: " + anomaly.flag() + " | running the application: StatementService.statement");
        System.out.println("Session A is the application, building alice's statement: the lines first, then the count and the total.");
        System.out.println(anomaly == Anomaly.PHANTOM
                ? "Session B opens a new account for alice and commits in between."
                : "Session B deposits 100 and commits in between.");
        System.out.println("Before: alice has two accounts, current (id 1) = 500 and savings (id 2) = 500.");
        System.out.println();

        Statement statement;
        try (Connection monitor = Db.connect(); Session b = new Session("B", Db.connect(), monitor, timeline)) {
            checkpoint.onFirst(Checkpoint.LINES_READ, () -> {
                timeline.result("A", "transaction isolation level: " + levelOfThisTransaction());
                timeline.result("A", "query 1 done: the lines are read");
                b.exec("begin");
                b.exec(anomaly == Anomaly.PHANTOM
                        ? "insert into account (id, owner, name, balance) values (3, 'alice', 'holiday', 250)"
                        : "update account set balance = balance + 100 where id = 1");
                b.exec("commit");
                pause();
            });
            timeline.step("A", "StatementService.statement(\"alice\")");
            statement = context.getBean(StatementService.class).statement("alice");
            timeline.result("A", "queries 2 and 3 done: the count and the total are read");
        }

        System.out.println();
        System.out.println("The statement the customer gets:");
        for (Statement.Line line : statement.lines()) {
            System.out.printf("    %-12s %6d%n", line.account(), line.balance());
        }
        System.out.println("    -------------------");
        System.out.printf("    %-12s %6d%n", statement.accountCount() + " accounts", statement.total());
        System.out.println();
        if (statement.addsUp()) {
            System.out.println("RESULT: OK. " + statement.lines().size() + " lines, " + statement.accountCount()
                    + " accounts, and the lines add up to the total: " + statement.total()
                    + ". What session B committed is not in this statement. It will be in the next one.");
        } else if (statement.lines().size() != statement.accountCount()) {
            System.out.println("RESULT: PHANTOM READ. The statement lists " + statement.lines().size()
                    + " accounts and then says there are " + statement.accountCount() + ". The lines add up to "
                    + statement.sumOfLines() + ", the total says " + statement.total() + ".");
        } else {
            System.out.println("RESULT: NON-REPEATABLE READ. The lines add up to " + statement.sumOfLines()
                    + ", the total says " + statement.total() + ". The queries of one transaction saw two different moments.");
        }
    }

    /** Session A and session B are both WithdrawalService.withdraw. B runs while A is between its check and its write. */
    private void withdrawals(Anomaly anomaly, long accountA, long accountB, long amount) {
        System.out.println("Anomaly: " + anomaly.flag() + " | running the application: WithdrawalService.withdraw");
        System.out.println(Demo.story(anomaly));
        System.out.println("Session B does all of its work while A is between checking the funds and writing the new balance.");
        System.out.println("Before: alice has two accounts, current (id 1) = 500 and savings (id 2) = 500.");
        System.out.println();

        Thread sessionA = Thread.currentThread();
        Map<String, Integer> attempts = new ConcurrentHashMap<>();
        Map<String, Throwable> thrown = new ConcurrentHashMap<>();
        Map<String, Boolean> paidOut = new ConcurrentHashMap<>();
        checkpoint.onReached(Checkpoint.FUNDS_CHECKED, arrival -> {
            String session = Thread.currentThread() == sessionA ? "A" : "B";
            int attempt = attempts.merge(session, 1, Integer::sum);
            timeline.result(session, "attempt " + attempt + (attempt > 1 ? " (a new transaction: the last one did not commit)" : "")
                    + ", transaction isolation level: " + levelOfThisTransaction());
            timeline.result(session, "funds checked: enough for " + amount);
            if (session.equals("A") && attempt == 1) {
                Thread sessionB = new Thread(() -> withdraw("B", accountB, amount, thrown, paidOut), "session-B");
                sessionB.start();
                try {
                    sessionB.join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                pause();
            }
        });
        withdraw("A", accountA, amount, thrown, paidOut);

        long current = jdbc.queryForObject("select balance from account where id = 1", Long.class);
        long savings = jdbc.queryForObject("select balance from account where id = 2", Long.class);
        System.out.println();
        System.out.println("Withdrawals paid out:  " + paidOut.size() + " x " + amount);
        System.out.println("Current (id 1):        " + current);
        System.out.println("Savings (id 2):        " + savings);
        System.out.println("Both together:         " + (current + savings));
        System.out.println();

        if (!thrown.isEmpty()) {
            String session = thrown.keySet().iterator().next();
            System.out.println("RESULT: NOT HANDLED. The data is right, because Postgres cancelled session " + session
                    + "'s transaction. But the customer got a " + thrown.get(session).getClass().getSimpleName()
                    + ". Nothing was wrong with the request: it has to be run again.");
        } else if (anomaly == Anomaly.LOST_UPDATE && current != 500 - paidOut.size() * amount) {
            System.out.println("RESULT: LOST UPDATE. " + paidOut.size() + " withdrawals of " + amount
                    + " were paid out, but the balance only went from 500 to " + current + ".");
        } else if (current + savings < 0) {
            System.out.println("RESULT: WRITE SKEW. Both withdrawals of " + amount + " went through and the accounts hold "
                    + (current + savings) + " together. The rule is broken, and neither transaction changed a row the other one changed.");
        } else {
            System.out.println("RESULT: OK. " + paidOut.size() + " x " + amount + " paid out, both accounts together went from 1000 to "
                    + (current + savings) + "." + (attempts.getOrDefault("A", 1) > 1
                    ? " Session A was cancelled once and ran again in a new transaction."
                    : !paidOut.containsKey("A")
                    ? " Session A passed the check, was cancelled, ran again in a new transaction and was refused."
                    : ""));
        }
    }

    private void withdraw(String session, long accountId, long amount, Map<String, Throwable> thrown,
                          Map<String, Boolean> paidOut) {
        timeline.step(session, "WithdrawalService.withdraw(" + accountId + ", " + amount + ")");
        try {
            long newBalance = context.getBean(WithdrawalService.class).withdraw(accountId, amount);
            paidOut.put(session, true);
            timeline.result(session, "returns " + newBalance + ", the new balance of account " + accountId);
        } catch (InsufficientFundsException e) {
            timeline.result(session, "refused, InsufficientFundsException: " + e.getMessage());
        } catch (RuntimeException e) {
            thrown.put(session, e);
            timeline.result(session, "THROWS " + e.getClass().getName());
            Throwable root = e;
            while (root.getCause() != null) {
                root = root.getCause();
            }
            String message = String.valueOf(root.getMessage()).lines().findFirst().orElse("");
            timeline.result(session, "caused by " + root.getClass().getSimpleName()
                    + (root instanceof SQLException sql ? ", SQLSTATE " + sql.getSQLState() : "") + ": " + message);
        }
    }

    /**
     * Asked on the calling thread, so it runs inside that thread's transaction, on its connection.
     * The session also puts the level into its application_name, where ./inspect can read it:
     * Postgres has no column that shows another session's isolation level.
     */
    private String levelOfThisTransaction() {
        String level = jdbc.queryForObject("show transaction_isolation", String.class);
        jdbc.execute("set local application_name = 'bank-app (" + level + ")'");
        return level;
    }

    private void pause() {
        if (pauseSeconds > 0) {
            timeline.note("Paused for " + pauseSeconds + " seconds. Run ./inspect in another terminal now.");
            try {
                Thread.sleep(pauseSeconds * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
