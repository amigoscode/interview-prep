package com.amigoscode.sqltraps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.amigoscode.sqltraps.db.ReadOnlySql;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Unit tests for the gate in front of ./inspect "<any SELECT>": no Spring, no database. */
@Tag("baseline")
class ReadOnlySqlTest {

    @Test
    @DisplayName("Inspect: a SELECT and a WITH ... SELECT are accepted, and the closing semicolon is dropped")
    void acceptsASelect() {
        assertThat(ReadOnlySql.check("  select id from orders where status = 'PAID';  "))
                .isEqualTo("select id from orders where status = 'PAID'");
        assertThat(ReadOnlySql.check("WITH paid AS (SELECT * FROM orders) SELECT COUNT(*) FROM paid"))
                .startsWith("WITH paid");
    }

    @Test
    @DisplayName("Inspect: INSERT, UPDATE, DELETE, DROP, TRUNCATE and friends are refused")
    void refusesStatementsThatWrite() {
        for (String sql : List.of(
                "INSERT INTO orders (id) VALUES (1)",
                "update orders set status = 'PAID'",
                "DELETE FROM orders",
                "DROP TABLE orders",
                "TRUNCATE orders",
                "ALTER TABLE orders ADD COLUMN x int",
                "VACUUM orders",
                "EXPLAIN ANALYZE DELETE FROM orders",
                "")) {
            assertThatThrownBy(() -> ReadOnlySql.check(sql)).as(sql).isInstanceOf(ReadOnlySql.NotReadOnly.class);
        }
    }

    @Test
    @DisplayName("Inspect: a write hidden behind a SELECT is refused: a second statement, SELECT INTO, a writing CTE, FOR UPDATE")
    void refusesAWriteHiddenBehindASelect() {
        for (String sql : List.of(
                "SELECT 1; DELETE FROM orders",
                "SELECT * INTO orders_copy FROM orders",
                "WITH gone AS (DELETE FROM orders RETURNING *) SELECT * FROM gone",
                "SELECT * FROM orders FOR UPDATE",
                "SELECT * FROM orders FOR SHARE",
                "SELECT pg_terminate_backend(1)",
                "SELECT 1 -- ; DROP TABLE orders")) {
            assertThatThrownBy(() -> ReadOnlySql.check(sql)).as(sql).isInstanceOf(ReadOnlySql.NotReadOnly.class);
        }
    }

    @Test
    @DisplayName("Inspect: a keyword inside quotes or inside a longer name is data, not a command")
    void keywordsInsideQuotesAreData() {
        assertThat(ReadOnlySql.check("SELECT 'DELETE; DROP' AS note")).isEqualTo("SELECT 'DELETE; DROP' AS note");
        assertThat(ReadOnlySql.check("SELECT created_at, 'it''s an update' FROM orders WHERE notes <> ''"))
                .contains("created_at");
        assertThat(ReadOnlySql.check("SELECT c.id AS \"set\" FROM customers c")).contains("\"set\"");
    }
}
