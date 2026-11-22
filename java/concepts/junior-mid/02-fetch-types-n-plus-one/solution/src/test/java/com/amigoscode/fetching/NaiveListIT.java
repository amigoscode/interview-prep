package com.amigoscode.fetching;

import static org.assertj.core.api.Assertions.assertThat;

import com.amigoscode.fetching.support.IntegrationTest;
import com.amigoscode.fetching.support.Measured;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Task 1. These tests pass on purpose: they pin the bug down so you can see exactly what goes wrong. */
@Tag("task1")
class NaiveListIT extends IntegrationTest {

    @Test
    @DisplayName("Naive: 200 orders, 200 customers: 201 statements, 1 for the orders + 1 per customer (this documents the bug)")
    void oneStatementPerCustomer() {
        Measured request = get("/api/orders?fetch=naive");

        assertThat(request.status()).isEqualTo(200);
        assertThat((int) request.json("$.length()")).as("orders in the response").isEqualTo(ORDERS);
        assertThat(request.statementsFrom("orders")).as("statements that read the orders").hasSize(1);
        assertThat(request.statementsFrom("customer")).as("statements that read one customer").hasSize(200);
        assertThat(request.statementCount()).as(request.describe()).isEqualTo(201);
    }

    @Test
    @DisplayName("Naive: 200 orders, 20 customers: 21 statements, because a customer already in the session is not loaded twice")
    void theNIsTheNumberOfDifferentCustomers() {
        seed.reset(ORDERS, 20);

        Measured request = get("/api/orders?fetch=naive");

        assertThat((int) request.json("$.length()")).as("orders in the response").isEqualTo(ORDERS);
        assertThat(request.statementCount()).as(request.describe()).isEqualTo(21);
    }
}
