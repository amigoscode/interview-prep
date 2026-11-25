package com.amigoscode.pagination.demo;

import com.amigoscode.pagination.PaginationApplication;
import com.amigoscode.pagination.db.DatabaseSetup;
import com.amigoscode.pagination.db.Explain;
import com.amigoscode.pagination.db.Plan;
import com.amigoscode.pagination.db.SqlRecorder;
import com.amigoscode.pagination.db.SqlStatement;
import com.amigoscode.pagination.order.Cursor;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * What ./demo and ./inspect share: the seeded database, the real application on a free port, HTTP
 * requests against it, the SQL each request sent, and EXPLAIN for that SQL.
 */
final class App implements AutoCloseable {

    /** What came back from one GET, and the SQL statements the application sent to answer it. */
    record Response(String path, int status, JsonNode body, int bytes, long millis, List<SqlStatement> statements) {

        boolean ok() {
            return status == 200;
        }

        /** The ids of the orders in "items", in the order they were returned. */
        List<Long> ids() {
            List<Long> ids = new ArrayList<>();
            JsonNode items = body.path("items");
            for (int i = 0; i < items.size(); i++) {
                ids.add(items.get(i).path("id").asLong());
            }
            return ids;
        }

        boolean hasNext() {
            return body.path("hasNext").asBoolean(false);
        }

        /** The cursor for the next page of the feed, or null. */
        String nextCursor() {
            JsonNode cursor = body.path("nextCursor");
            return cursor.isMissingNode() || cursor.isNull() ? null : cursor.asString();
        }

        /** For a response that is not a 200: "INVALID_CURSOR: The cursor ...". */
        String error() {
            String code = body.path("code").asString("");
            String message = body.path("message").asString(body.path("error").asString(""));
            return (code.isEmpty() ? "" : code + ": ") + message;
        }

        /** The statements that fetch rows: everything except SELECT COUNT. */
        List<SqlStatement> pageQueries() {
            return statements.stream().filter(statement -> !statement.isCount()).toList();
        }

        List<SqlStatement> countQueries() {
            return statements.stream().filter(SqlStatement::isCount).toList();
        }
    }

    private static final JsonMapper JSON = new JsonMapper();

    private final Connection connection;
    private final ConfigurableApplicationContext context;
    private final String baseUrl;
    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    private final long orders;

    private App(Connection connection, ConfigurableApplicationContext context, long orders) {
        this.connection = connection;
        this.context = context;
        this.baseUrl = "http://localhost:" + context.getEnvironment().getProperty("local.server.port");
        this.orders = orders;
    }

    /**
     * Seeds the database if needed, applies indexes.sql and starts the application. Returns null,
     * after printing why, when Postgres is not reachable or indexes.sql has a mistake in it.
     */
    static App start(boolean reseed) throws Exception {
        if (!Db.isUp()) {
            return null;
        }
        Connection connection = Db.connect();
        System.out.println();
        if (reseed) {
            DatabaseSetup.forget(connection);
        }
        boolean[] logged = new boolean[1];
        try {
            DatabaseSetup.run(connection, DatabaseSetup.DEFAULT_ORDERS, message -> {
                logged[0] = true;
                System.out.println(message);
            });
        } catch (DatabaseSetup.SetupException e) {
            System.out.println(e.getMessage());
            System.out.println();
            System.out.println("Fix the statement and run the command again.");
            System.out.println();
            connection.close();
            return null;
        }
        if (logged[0]) {
            System.out.println();
        }
        try (Statement statement = connection.createStatement()) {
            // An order left behind by a demo run that was stopped half way.
            statement.execute("delete from orders where id > " + DatabaseSetup.DEFAULT_ORDERS);
        }

        SpringApplication app = new SpringApplication(PaginationApplication.class);
        app.setBannerMode(Banner.Mode.OFF);
        app.setLogStartupInfo(false);
        ConfigurableApplicationContext context = app.run(
                "--server.port=0",
                "--logging.level.root=WARN",
                // A 500 is shown as one line instead of a full stack trace.
                "--logging.level.org.apache.catalina.core.ContainerBase=OFF",
                "--spring.web.error.include-message=always");
        App started = new App(connection, context, DatabaseSetup.DEFAULT_ORDERS);
        // Throwaway requests, so the first real one is not slowed down by startup work.
        started.get("/api/orders/feed?size=1");
        started.get("/api/orders?page=0&size=1");
        started.get("/api/customers/1/orders?page=0&size=1");
        return started;
    }

    long orders() {
        return orders;
    }

    Connection connection() {
        return connection;
    }

    /** Sends GET path to the running application and writes down the SQL it sends meanwhile. */
    Response get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path)).build();
        SqlRecorder.start();
        long start = System.nanoTime();
        HttpResponse<byte[]> response;
        List<SqlStatement> statements;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } finally {
            statements = SqlRecorder.stop();
        }
        long millis = (System.nanoTime() - start) / 1_000_000;
        JsonNode body;
        try {
            body = JSON.readTree(response.body());
        } catch (RuntimeException e) {
            body = JSON.createObjectNode();
        }
        return new Response(path, response.statusCode(), body, response.body().length, millis, statements);
    }

    /** Runs the statement again with EXPLAIN (ANALYZE, BUFFERS) and returns what Postgres did. */
    Plan plan(SqlStatement statement) throws SQLException {
        return Explain.analyze(connection, statement);
    }

    List<String> planText(SqlStatement statement) throws SQLException {
        return Explain.text(connection, statement);
    }

    /** The ids the feed must return for these positions: newest first, highest id first within one created_at. */
    List<Long> expectedIds(long offset, int limit) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(
                "select id from orders order by created_at desc, id desc limit ? offset ?")) {
            select.setInt(1, limit);
            select.setLong(2, offset);
            List<Long> ids = new ArrayList<>();
            try (ResultSet row = select.executeQuery()) {
                while (row.next()) {
                    ids.add(row.getLong(1));
                }
            }
            return ids;
        }
    }

    /**
     * The path of a feed page that starts after this many orders. A real client can only get such a
     * cursor by following nextCursor from the top. The tools take a shortcut: they look the row up
     * in the database and build the cursor from it.
     */
    String feedPathAfter(long ordersRead, int size) throws SQLException {
        if (ordersRead == 0) {
            return "/api/orders/feed?size=" + size;
        }
        try (PreparedStatement select = connection.prepareStatement(
                "select created_at, id from orders order by created_at desc, id desc limit 1 offset ?")) {
            select.setLong(1, ordersRead - 1);
            try (ResultSet row = select.executeQuery()) {
                row.next();
                Cursor cursor = new Cursor(row.getObject(1, OffsetDateTime.class).toInstant(), row.getLong(2));
                return feedPath(cursor.encode(), size);
            }
        }
    }

    static String feedPath(String cursor, int size) {
        return "/api/orders/feed?size=" + size + "&after=" + URLEncoder.encode(cursor, StandardCharsets.UTF_8);
    }

    /** The values bound to a statement, readable: 99980, 20. */
    static String values(SqlStatement statement) {
        return statement.parameters().stream()
                .map(value -> value instanceof Timestamp timestamp ? "'" + timestamp.toInstant() + "'" : String.valueOf(value))
                .collect(Collectors.joining(", "));
    }

    static String number(long value) {
        return String.format(Locale.US, "%,d", value);
    }

    @Override
    public void close() throws SQLException {
        try {
            context.close();
        } finally {
            connection.close();
        }
    }
}
