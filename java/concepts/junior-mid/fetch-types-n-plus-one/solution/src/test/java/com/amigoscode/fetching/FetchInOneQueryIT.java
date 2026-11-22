package com.amigoscode.fetching;

import static org.assertj.core.api.Assertions.assertThat;

import com.amigoscode.fetching.support.IntegrationTest;
import com.amigoscode.fetching.support.Measured;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("task6")
class FetchInOneQueryIT extends IntegrationTest {

    @Test
    @DisplayName("JOIN FETCH: the list of 200 orders runs 1 statement and returns the same JSON as the naive list")
    void joinFetchRunsOneStatement() {
        String naive = get("/api/orders?fetch=naive").body();

        Measured request = get("/api/orders?fetch=join-fetch");

        assertThat(request.status()).isEqualTo(200);
        assertThat(request.statementCount()).as(request.describe()).isEqualTo(1);
        assertThat(request.body()).as("the JSON of ?fetch=join-fetch").isEqualTo(naive);
    }

    @Test
    @DisplayName("@EntityGraph: the list of 200 orders runs 1 statement and returns the same JSON as the naive list")
    void entityGraphRunsOneStatement() {
        String naive = get("/api/orders?fetch=naive").body();

        Measured request = get("/api/orders?fetch=entity-graph");

        assertThat(request.status()).isEqualTo(200);
        assertThat(request.statementCount()).as(request.describe()).isEqualTo(1);
        assertThat(request.body()).as("the JSON of ?fetch=entity-graph").isEqualTo(naive);
    }
}
