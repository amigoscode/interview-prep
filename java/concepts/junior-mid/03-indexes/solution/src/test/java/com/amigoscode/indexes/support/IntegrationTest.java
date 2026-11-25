package com.amigoscode.indexes.support;

import com.amigoscode.indexes.db.Explain;
import com.amigoscode.indexes.db.IndexInfo;
import com.amigoscode.indexes.db.Plan;
import com.amigoscode.indexes.shop.Query;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base class for the *IT tests. They start the real application against a real Postgres started by
 * Testcontainers (Docker must be running): the planner only chooses an index when there is real
 * data to choose for. One container is shared by every test class.
 *
 * The application seeds 200,000 orders at startup (the demo uses 1,000,000) and applies
 * src/main/resources/indexes.sql. The tests never look at the clock: they ask Postgres for the plan
 * of a query and check which scan it chose, how many rows it read and how many pages it touched.
 */
@SpringBootTest(properties = {"spring.main.banner-mode=off", "shop.seed.orders=" + IntegrationTest.ORDERS})
public abstract class IntegrationTest {

    /** Orders in the test database. Customers: a tenth of that. */
    protected static final long ORDERS = 200_000;

    /** An ordinary customer with 10 orders spread over two years. */
    protected static final long TYPICAL_CUSTOMER = 4242;

    /** One of the ten big accounts: 200 orders in the test database. */
    protected static final long BIG_CUSTOMER = 1;

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

    @Autowired
    private TransactionTemplate transaction;

    /** Runs the query with EXPLAIN ANALYZE and returns what Postgres did. */
    protected Plan plan(Query query) {
        return jdbc.execute((ConnectionCallback<Plan>) connection -> Explain.analyze(connection, query));
    }

    /** Every index on orders and customers, as the database describes it. */
    protected List<IndexInfo> indexes() {
        return jdbc.execute((ConnectionCallback<List<IndexInfo>>) IndexInfo::all);
    }

    /** The index with this name. */
    protected IndexInfo index(String name) {
        return indexes().stream().filter(index -> index.name().equals(name)).findFirst().orElseThrow();
    }

    /** What the indexes of a table cover, for example "(customer_id, created_at)". */
    protected List<String> indexedColumns(String table) {
        return indexes().stream().filter(index -> index.table().equals(table)).map(IndexInfo::columns).toList();
    }

    /**
     * Runs the work inside a transaction and rolls it back. In Postgres even CREATE INDEX and
     * DROP INDEX can be rolled back, so a test can try out a different set of indexes and leave
     * the database exactly as it found it.
     */
    protected <T> T thenRollBack(Supplier<T> work) {
        return transaction.execute(status -> {
            status.setRollbackOnly();
            return work.get();
        });
    }

    /** Hides every index except the primary keys. Only call it inside thenRollBack. */
    protected void dropEveryIndexExceptThePrimaryKeys() {
        for (IndexInfo index : indexes()) {
            if (index.secondary()) {
                jdbc.execute("drop index \"" + index.name() + "\"");
            }
        }
    }

    protected String trackingNumberOf(long orderId) {
        return jdbc.queryForObject("select tracking_number from orders where id = ?", String.class, orderId);
    }
}
