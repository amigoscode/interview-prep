package com.amigoscode.isolation.support;

import com.amigoscode.isolation.checkpoint.ScriptedCheckpoint;
import com.amigoscode.isolation.demo.Db;
import com.amigoscode.isolation.demo.Lab;
import com.amigoscode.isolation.statement.StatementService;
import com.amigoscode.isolation.withdrawal.WithdrawalService;
import com.amigoscode.isolation.withdrawal.Withdrawer;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base class for the *IT tests. They run against a real Postgres started by Testcontainers (Docker
 * must be running), because isolation levels are something the database does: none of this shows
 * up against mocks or an in-memory database. One container is shared by every test class.
 *
 * <p>Nothing here sleeps and hopes. A test decides the exact order of the steps: either it drives
 * two database sessions one statement at a time ({@link #lab()}), or it makes a second session do
 * its work at a {@link ScriptedCheckpoint} in the middle of the application's transaction.
 */
@SpringBootTest(properties = "spring.main.banner-mode=off")
@Import(IntegrationTest.TestBeans.class)
public abstract class IntegrationTest {

    public static final long CURRENT = 1;
    public static final long SAVINGS = 2;

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @TestConfiguration
    static class TestBeans {

        @Bean
        @Primary
        ScriptedCheckpoint scriptedCheckpoint() {
            return new ScriptedCheckpoint();
        }
    }

    @Autowired
    protected StatementService statementService;

    @Autowired
    protected WithdrawalService withdrawalService;

    @Autowired
    protected Withdrawer withdrawer;

    @Autowired
    protected ScriptedCheckpoint checkpoint;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    /** Every test starts with alice's two accounts: current (id 1) = 500 and savings (id 2) = 500. */
    @BeforeEach
    void aliceHasTwoAccounts() throws SQLException {
        checkpoint.reset();
        try (Connection connection = dataSource.getConnection()) {
            Db.reset(connection);
        }
    }

    /** Plain SQL in two sessions, stepped in a fixed order. Prints nothing. */
    protected Lab lab() {
        return new Lab(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword(), null);
    }

    /**
     * Runs SQL in a different database session from the one the calling thread's transaction uses,
     * and commits it at once. This is "somebody else" committing while the application is busy.
     */
    protected void inAnotherSession(String sql) {
        // dataSource.getConnection() hands out a fresh pooled connection. (JdbcTemplate would not do:
        // inside a transaction it joins the transaction's own connection.)
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Runs {@code task} on a second thread and waits for it to finish, so it gets its own
     * transaction and its own session. Returns what it threw, or null when it succeeded.
     */
    protected Throwable inAnotherThread(Runnable task) {
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try {
                task.run();
            } catch (Throwable e) {
                thrown.set(e);
            }
        }, "second-customer");
        thread.start();
        try {
            thread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        return thrown.get();
    }

    /** The isolation level of the transaction the calling thread is in, as Postgres reports it. */
    protected String isolationLevelOfThisTransaction() {
        return jdbc.queryForObject("select current_setting('transaction_isolation')", String.class);
    }

    protected long balance(long accountId) {
        return jdbc.queryForObject("select balance from account where id = ?", Long.class, accountId);
    }

    protected long combinedBalance() {
        return jdbc.queryForObject("select sum(balance) from account where owner = 'alice'", Long.class);
    }
}
