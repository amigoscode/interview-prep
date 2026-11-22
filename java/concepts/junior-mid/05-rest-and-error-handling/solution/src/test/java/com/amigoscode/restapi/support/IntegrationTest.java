package com.amigoscode.restapi.support;

import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base class for the *IT tests. They start the real application on a random port, against a real
 * Postgres started by Testcontainers (Docker must be running), and talk to it over HTTP like any
 * client would. One container and one application are shared by every test class.
 *
 * Every test starts from the six orders of seed.sql:
 * 1, 2, 3 PENDING | 4 PAID (has notes, a phone number and an internal note) | 5 SHIPPED | 6 CANCELLED
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "spring.main.banner-mode=off")
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

    /** A complete, valid body for POST /api/orders and PUT /api/orders/{id}. */
    protected static final String VALID_ORDER = """
            {"customer": "Nina Patel", "product": "MOUSE", "quantity": 2, "shippingAddress": "8 Queen Square, Bath"}
            """;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Environment environment;

    protected Api api;

    @BeforeEach
    void sixKnownOrders() {
        new ResourceDatabasePopulator(new ClassPathResource("seed.sql")).execute(dataSource);
        api = new Api("http://localhost:" + environment.getProperty("local.server.port"));
    }

    /** One column of one order, as text. null when the column is empty. */
    protected String column(long id, String column) {
        return jdbc.queryForObject("select " + column + "::text from orders where id = ?", String.class, id);
    }

    protected String statusOf(long id) {
        return column(id, "status");
    }

    protected int orderRows() {
        return jdbc.queryForObject("select count(*) from orders", Integer.class);
    }

    /** The whole orders table. Compare two of these to prove that nothing changed. */
    protected List<Map<String, Object>> everyOrder() {
        return jdbc.queryForList("select * from orders order by id");
    }
}
