package com.amigoscode.transactional.support;

import com.amigoscode.transactional.demo.Bank;
import com.amigoscode.transactional.trace.TransactionTrace;
import com.amigoscode.transactional.trace.TransactionTrace.Kind;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base class for the *IT tests. They run the real application against a real Postgres started by
 * Testcontainers (Docker must be running), because a transaction that is never opened looks
 * exactly like one that is, until something fails against a real database. One container is
 * shared by every test class.
 *
 * <p>These tests are NOT @Transactional. A transactional test wraps every call in a transaction of
 * its own and rolls it back at the end, which would hide every bug this project is about. The
 * state is reset with plain SQL instead, and checked with plain SQL.
 */
@SpringBootTest(properties = "spring.main.banner-mode=off")
public abstract class IntegrationTest {

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

    @Autowired
    protected JdbcTemplate jdbc;

    /** Alice 100, Bob 0, Carol 0 (frozen), Acme Ltd 1000, an empty audit log and an empty trace. */
    @BeforeEach
    void resetTheBank() {
        Bank.reset(jdbc);
        TransactionTrace.clear();
    }

    protected long balance(long accountId) {
        return Bank.balance(jdbc, accountId);
    }

    protected String owner(long accountId) {
        return Bank.owner(jdbc, accountId);
    }

    /** All the money in the bank: 1100 at the start of every test. */
    protected long totalMoney() {
        return Bank.total(jdbc);
    }

    protected int auditRows() {
        return Bank.auditRows(jdbc);
    }

    /** How many database transactions were really opened, committed or rolled back since the test started. */
    protected long transactions(Kind kind) {
        return TransactionTrace.events().stream().filter(event -> event.kind() == kind).count();
    }
}
