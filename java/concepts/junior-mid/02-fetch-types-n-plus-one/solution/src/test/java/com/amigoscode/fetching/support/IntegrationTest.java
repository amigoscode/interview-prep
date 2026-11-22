package com.amigoscode.fetching.support;

import com.amigoscode.fetching.seed.Seed;
import com.amigoscode.fetching.sql.SqlRecorder;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base class for the *IT tests. They run the real application against a real Postgres started by
 * Testcontainers (Docker must be running), because the number of SQL statements is decided by
 * Hibernate and the mapping, and no mock can tell you that. One container is shared by every test
 * class.
 *
 * <p>The tests do not measure time. They count SQL statements: {@link #get(String)} sends one
 * request through the whole application and returns the statements Hibernate ran for it.
 */
@SpringBootTest(properties = "spring.main.banner-mode=off")
public abstract class IntegrationTest {

    protected static final int ORDERS = 200;

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
    private WebApplicationContext context;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    protected Seed seed;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected EntityManager entityManager;

    private MockMvc api;

    @BeforeEach
    void fillTheShop() {
        api = MockMvcBuilders.webAppContextSetup(context).build();
        // 200 orders, one customer each, 1 to 3 items per order, 20 products.
        seed.reset(ORDERS, ORDERS);
    }

    /** Sends GET url through the whole application and records the SQL statements it ran. */
    protected Measured get(String url) {
        try {
            SqlRecorder.reset();
            MvcResult result = api.perform(MockMvcRequestBuilders.get(url)).andReturn();
            return new Measured(url, result.getResponse().getStatus(),
                    result.getResponse().getContentAsString(StandardCharsets.UTF_8), SqlRecorder.statements());
        } catch (Exception e) {
            // An exception nobody handled: in the running application this is a 500.
            throw new AssertionError("GET " + url + " failed with " + rootCause(e), e);
        }
    }

    /** Runs the code in one read-only transaction, the way a @Transactional service method would. */
    protected <T> T inTransaction(Supplier<T> work) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setReadOnly(true);
        return transaction.execute(status -> work.get());
    }

    private static String rootCause(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getName() + ": " + root.getMessage();
    }
}
