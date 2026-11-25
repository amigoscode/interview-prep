package com.amigoscode.constraints.support;

import com.amigoscode.constraints.settings.SettingsService;
import com.amigoscode.constraints.subscription.SubscriptionService;
import com.amigoscode.constraints.user.SignupService;
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
 * Testcontainers (Docker must be running), because a constraint only exists in a real database.
 * One container is shared by every test class, and schema.sql is applied to it when the
 * application starts.
 */
@SpringBootTest(properties = "spring.main.banner-mode=off")
@Import(IntegrationTest.TestBeans.class)
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

    @TestConfiguration
    static class TestBeans {

        @Bean
        @Primary
        ControllableWork controllableWork() {
            return new ControllableWork();
        }
    }

    @Autowired
    protected SignupService signupService;

    @Autowired
    protected SubscriptionService subscriptionService;

    @Autowired
    protected SettingsService settingsService;

    @Autowired
    protected ControllableWork work;

    @Autowired
    protected JdbcTemplate jdbc;

    @BeforeEach
    void emptyDatabase() {
        work.reset();
        jdbc.execute("truncate table subscriptions, user_settings, users restart identity cascade");
    }

    /** Inserts a user with plain SQL, the way a script or another service would: no SignupService. */
    protected long insertUser(String email) {
        return jdbc.queryForObject("insert into users (email, display_name) values (?, 'Test') returning id",
                Long.class, email);
    }

    protected int userRows() {
        return jdbc.queryForObject("select count(*) from users", Integer.class);
    }

    protected int subscriptionRows(long userId, String status) {
        return jdbc.queryForObject("select count(*) from subscriptions where user_id = ? and status = ?",
                Integer.class, userId, status);
    }

    protected int subscriptionRows() {
        return jdbc.queryForObject("select count(*) from subscriptions", Integer.class);
    }

    protected int settingsRows(long userId) {
        return jdbc.queryForObject("select count(*) from user_settings where user_id = ?", Integer.class, userId);
    }
}
