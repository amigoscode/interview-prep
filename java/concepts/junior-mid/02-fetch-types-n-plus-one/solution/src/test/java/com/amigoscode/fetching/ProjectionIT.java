package com.amigoscode.fetching;

import static org.assertj.core.api.Assertions.assertThat;

import com.amigoscode.fetching.support.IntegrationTest;
import com.amigoscode.fetching.support.Measured;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("task7")
class ProjectionIT extends IntegrationTest {

    @Test
    @DisplayName("Projection: the list of 200 orders runs 1 statement and returns the same JSON as the naive list")
    void projectionRunsOneStatement() {
        String naive = get("/api/orders?fetch=naive").body();

        Measured request = get("/api/orders?fetch=projection");

        assertThat(request.status()).isEqualTo(200);
        assertThat(request.statementCount()).as(request.describe()).isEqualTo(1);
        assertThat(request.body()).as("the JSON of ?fetch=projection").isEqualTo(naive);
    }

    @Test
    @DisplayName("Projection: the statement selects only the 5 columns the list shows, not whole entities")
    void projectionSelectsOnlyWhatTheListShows() {
        Measured request = get("/api/orders?fetch=projection");

        assertThat(request.statementsFrom("orders")).as(request.describe()).hasSize(1);
        String sql = request.statementsFrom("orders").get(0);
        String selectList = sql.substring("select ".length(), sql.indexOf(" from "));
        assertThat(selectList.split(",")).as("columns selected by: " + sql).hasSize(5);
    }
}
