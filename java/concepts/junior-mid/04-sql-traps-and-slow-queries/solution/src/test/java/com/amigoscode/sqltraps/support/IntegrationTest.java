package com.amigoscode.sqltraps.support;

import com.amigoscode.sqltraps.db.DatabaseSetup;
import com.amigoscode.sqltraps.db.DatabaseSetup.Shop;
import com.amigoscode.sqltraps.db.Explain;
import com.amigoscode.sqltraps.db.Plan;
import com.amigoscode.sqltraps.shop.Query;
import com.amigoscode.sqltraps.shop.ShopRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * Testcontainers (Docker must be running): a wrong join and a wrong plan only show up in a real
 * database. One container is shared by every test class.
 *
 * The tests never look at the clock. They check the ROWS a query returns (exact ids and numbers,
 * known from seed-small.sql) and the SHAPE of its plan (which join type, which steps, how many rows
 * read), taken from EXPLAIN (ANALYZE, FORMAT JSON) with parallel query and JIT switched off.
 */
@SpringBootTest(properties = {"spring.main.banner-mode=off", "shop.seed=small"})
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

    @Autowired
    protected ShopRepository shop;

    @Autowired
    private TransactionTemplate transaction;

    /** 8 customers and 12 orders, every row known. Does nothing if that is what is loaded. */
    protected void smallShop() {
        load(Shop.SMALL);
    }

    /** The small shop plus 300,000 generated orders, analyzed. Does nothing if that is what is loaded. */
    protected void bigShop() {
        load(Shop.BIG);
    }

    private void load(Shop wanted) {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            DatabaseSetup.ensure(connection, wanted, message -> { });
            return null;
        });
    }

    /** The big shop, loaded on top of statistics that describe the small one. ANALYZE has not run. */
    protected void bigShopWithStaleStatistics() {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            DatabaseSetup.loadBigWithStaleStatistics(connection, message -> { });
            return null;
        });
    }

    protected void analyze() {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            DatabaseSetup.analyzeNow(connection);
            return null;
        });
    }

    /** Runs the query the way the application does and returns its rows. */
    protected List<Map<String, Object>> rows(Query query) {
        return shop.rows(query);
    }

    /** The first column of every row, as numbers: the customer ids of a report, in the order returned. */
    protected static List<Long> ids(List<Map<String, Object>> rows) {
        return rows.stream().map(row -> ((Number) row.values().iterator().next()).longValue()).toList();
    }

    /** One column of a report, by customer id: {1=18000, 6=12000}. */
    protected static Map<Long, Long> byId(List<Map<String, Object>> rows, String column) {
        Map<Long, Long> values = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Object value = row.get(column);
            values.put(((Number) row.values().iterator().next()).longValue(), value == null ? null : ((Number) value).longValue());
        }
        return values;
    }

    /** Runs the query with EXPLAIN ANALYZE and returns what Postgres did. */
    protected Plan plan(Query query) {
        return jdbc.execute((ConnectionCallback<Plan>) connection -> Explain.analyze(connection, query));
    }

    /** Runs the work inside a transaction and rolls it back, so the shop is left exactly as it was. */
    protected <T> T thenRollBack(Supplier<T> work) {
        return transaction.execute(status -> {
            status.setRollbackOnly();
            return work.get();
        });
    }
}
