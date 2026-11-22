package com.amigoscode.idempotency.support;

import com.amigoscode.idempotency.key.IdempotencyCleanup;
import com.amigoscode.idempotency.payment.PaymentRequest;
import com.amigoscode.idempotency.payment.PaymentResult;
import com.amigoscode.idempotency.payment.PaymentService;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
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
 * Base class for the *IT tests. They run the real application against a real Postgres started by
 * Testcontainers (Docker must be running), because a UNIQUE constraint and a waiting INSERT do not
 * exist in a mock or an in-memory database. One container is shared by every test class.
 */
@SpringBootTest(properties = "spring.main.banner-mode=off")
@Import(IntegrationTest.TestBeans.class)
public abstract class IntegrationTest {

    public static final long MARIA = 1;
    public static final long JAMAL = 2;
    public static final long START_BALANCE = 10_000;

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
        ControllableCardProcessor controllableCardProcessor() {
            return new ControllableCardProcessor();
        }
    }

    @Autowired
    protected PaymentService paymentService;

    @Autowired
    protected IdempotencyCleanup cleanup;

    @Autowired
    protected ControllableCardProcessor cardProcessor;

    @Autowired
    protected JdbcTemplate jdbc;

    @BeforeEach
    void everyoneStartsWithAHundred() {
        cardProcessor.reset();
        jdbc.execute("truncate table idempotency_key, payment restart identity");
        jdbc.update("update customer set balance_cents = ?", START_BALANCE);
    }

    @AfterEach
    void letEveryoneGo() {
        cardProcessor.release();
    }

    /** A key the way a client makes one: a random UUID. */
    protected static String newKey() {
        return UUID.randomUUID().toString();
    }

    /** Maria pays {@code amountCents} with this idempotency key (null = no Idempotency-Key header). */
    protected PaymentResult mariaPays(long amountCents, String key) {
        return paymentService.pay(key, new PaymentRequest(MARIA, amountCents));
    }

    protected int paymentRows() {
        return jdbc.queryForObject("select count(*) from payment", Integer.class);
    }

    protected int keyRows() {
        return jdbc.queryForObject("select count(*) from idempotency_key", Integer.class);
    }

    protected long balance(long customerId) {
        return jdbc.queryForObject("select balance_cents from customer where id = ?", Long.class, customerId);
    }
}
