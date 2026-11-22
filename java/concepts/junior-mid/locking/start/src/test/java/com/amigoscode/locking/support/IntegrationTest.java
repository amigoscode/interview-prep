package com.amigoscode.locking.support;

import com.amigoscode.locking.order.OrderService;
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
 * Testcontainers (Docker must be running), because none of these bugs show up against mocks or an
 * in-memory database. One container is shared by every test class.
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
        ControllableFraudCheck controllableFraudCheck() {
            return new ControllableFraudCheck();
        }
    }

    @Autowired
    protected OrderService orderService;

    @Autowired
    protected ControllableFraudCheck fraudCheck;

    @Autowired
    protected JdbcTemplate jdbc;

    @BeforeEach
    void emptyShop() {
        fraudCheck.reset();
        jdbc.execute("truncate table orders restart identity");
        stockIs(1);
    }

    @AfterEach
    void letEveryoneGo() {
        fraudCheck.release();
    }

    protected void stockIs(int stock) {
        jdbc.update("update product set stock = ?, version = 0 where id = ?", stock, Buyers.PRODUCT_ID);
    }

    protected int stock() {
        return jdbc.queryForObject("select stock from product where id = ?", Integer.class, Buyers.PRODUCT_ID);
    }

    protected long version() {
        return jdbc.queryForObject("select version from product where id = ?", Long.class, Buyers.PRODUCT_ID);
    }

    protected int orderRows() {
        return jdbc.queryForObject("select count(*) from orders", Integer.class);
    }
}
