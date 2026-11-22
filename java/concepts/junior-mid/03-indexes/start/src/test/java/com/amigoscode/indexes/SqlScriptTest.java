package com.amigoscode.indexes;

import static org.assertj.core.api.Assertions.assertThat;

import com.amigoscode.indexes.db.SqlScript;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Unit tests for the reader of indexes.sql: no Spring, no database. */
@Tag("baseline")
class SqlScriptTest {

    @Test
    @DisplayName("indexes.sql: every statement that ends with a semicolon is run, in order")
    void splitsStatements() {
        assertThat(SqlScript.statements("""
                CREATE INDEX a ON orders (status);
                CREATE INDEX b
                    ON orders (created_at) WHERE status = 'PENDING';
                """)).containsExactly(
                "CREATE INDEX a ON orders (status)",
                "CREATE INDEX b ON orders (created_at) WHERE status = 'PENDING'");
    }

    @Test
    @DisplayName("indexes.sql: comments and blank lines are ignored, so a commented-out index is not created")
    void ignoresComments() {
        assertThat(SqlScript.statements("""
                -- CREATE INDEX a ON orders (status);

                CREATE INDEX b ON orders (created_at); -- newest first
                """)).containsExactly("CREATE INDEX b ON orders (created_at)");
    }

    @Test
    @DisplayName("indexes.sql: the file in this project can be read, and every statement in it is a CREATE INDEX")
    void readsTheProjectFile() {
        assertThat(SqlScript.statements(SqlScript.read("indexes.sql")))
                .isNotEmpty()
                .allMatch(statement -> statement.toUpperCase().matches("CREATE (UNIQUE )?INDEX .*"));
    }
}
