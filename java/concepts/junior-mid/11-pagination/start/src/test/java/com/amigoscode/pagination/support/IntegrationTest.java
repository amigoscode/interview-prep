package com.amigoscode.pagination.support;

import com.amigoscode.pagination.db.Explain;
import com.amigoscode.pagination.db.IndexInfo;
import com.amigoscode.pagination.db.Plan;
import com.amigoscode.pagination.db.SqlRecorder;
import com.amigoscode.pagination.db.SqlStatement;
import com.amigoscode.pagination.order.Cursor;
import com.amigoscode.pagination.order.OrderResponse;
import com.amigoscode.pagination.order.OrderService;
import java.time.OffsetDateTime;
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
 * Testcontainers (Docker must be running): a query plan only exists in a real database. One
 * container is shared by every test class.
 *
 * The application seeds 20,000 orders at startup (the demo uses 1,000,000) and applies
 * src/main/resources/indexes.sql. The tests never look at the clock. They call the service, write
 * down the SQL it really sent, and check three things: which rows came back, how many statements
 * were sent, and what Postgres did to answer them (the plan, the rows it read).
 */
@SpringBootTest(properties = {"spring.main.banner-mode=off", "shop.seed.orders=" + IntegrationTest.ORDERS})
public abstract class IntegrationTest {

    /** Orders in the test database: ids 1 to 20,000, in 500 batches of 40 that share a created_at. */
    protected static final int ORDERS = 20_000;

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

    /** What a call returned, and the SQL statements it sent to Postgres. */
    public record Recorded<T>(T result, List<SqlStatement> statements) {

        /** The statements that fetch rows: everything except SELECT COUNT. */
        public List<SqlStatement> pageQueries() {
            return statements.stream().filter(statement -> !statement.isCount()).toList();
        }

        public List<SqlStatement> countQueries() {
            return statements.stream().filter(SqlStatement::isCount).toList();
        }
    }

    @Autowired
    protected OrderService orderService;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate transaction;

    /** Runs the call and writes down every SQL statement it sends. */
    protected <T> Recorded<T> record(Supplier<T> call) {
        SqlRecorder.start();
        try {
            T result = call.get();
            return new Recorded<>(result, SqlRecorder.stop());
        } finally {
            SqlRecorder.stop();
        }
    }

    /** Runs the recorded statement again with EXPLAIN ANALYZE and returns what Postgres did. */
    protected Plan plan(SqlStatement statement) {
        return jdbc.execute((ConnectionCallback<Plan>) connection -> Explain.analyze(connection, statement));
    }

    /** What the indexes on orders cover, for example "(created_at, id)". */
    protected List<String> indexedColumns() {
        return jdbc.execute((ConnectionCallback<List<IndexInfo>>) IndexInfo::all).stream()
                .map(IndexInfo::columns).toList();
    }

    /**
     * Runs the work inside a transaction and rolls it back, so a test can insert, delete or update
     * orders and leave the database exactly as it found it. The service joins the same transaction
     * and sees those changes.
     */
    protected <T> T thenRollBack(Supplier<T> work) {
        return transaction.execute(status -> {
            status.setRollbackOnly();
            return work.get();
        });
    }

    protected void thenRollBack(Runnable work) {
        thenRollBack(() -> {
            work.run();
            return null;
        });
    }

    /** The ids the API must return for these positions: newest first, and the highest id first within one created_at. */
    protected List<Long> expectedIds(int offset, int limit) {
        return jdbc.queryForList("select id from orders order by created_at desc, id desc limit ? offset ?",
                Long.class, limit, offset);
    }

    /** The cursor a client holds after reading this many orders from the top of the feed. */
    protected Cursor cursorAfter(int ordersRead) {
        return jdbc.queryForObject(
                "select created_at, id from orders order by created_at desc, id desc limit 1 offset ?",
                (row, n) -> new Cursor(row.getObject("created_at", OffsetDateTime.class).toInstant(), row.getLong("id")),
                ordersRead - 1);
    }

    /** A brand new order, newer than every other one. Returns its id. */
    protected long insertNewestOrder() {
        long id = jdbc.queryForObject("select max(id) + 1 from orders", Long.class);
        jdbc.update("""
                insert into orders (id, customer_id, status, total_cents, created_at)
                select ?, 1001, 'PENDING', 1999, max(created_at) + interval '1 minute' from orders
                """, id);
        return id;
    }

    /**
     * Gives a new customer this many orders, a dozen per created_at, and stores them in a shuffled
     * order, the way rows end up on disk after years of inserts and updates. Returns the ids the API
     * must return for that customer, first to last.
     */
    protected List<Long> customerWithOrders(long customerId, int orders) {
        jdbc.update("""
                insert into orders (id, customer_id, status, total_cents, created_at)
                select 5000000 + i, ?, 'DELIVERED', 1999,
                       timestamptz '2030-01-01 00:00:00+00' + make_interval(mins => ((i - 1) / 12)::int)
                  from generate_series(1, ?) as i
                 order by md5(i::text)
                """, customerId, orders);
        return jdbc.queryForList("select id from orders where customer_id = ? order by created_at desc, id desc",
                Long.class, customerId);
    }

    protected static List<Long> ids(List<OrderResponse> orders) {
        return orders.stream().map(OrderResponse::id).toList();
    }
}
