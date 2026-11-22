package com.amigoscode.fetching;

import static org.assertj.core.api.Assertions.assertThat;

import com.amigoscode.fetching.order.Order;
import com.amigoscode.fetching.support.IntegrationTest;
import com.amigoscode.fetching.support.Measured;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("task2")
class LazyFetchIT extends IntegrationTest {

    @Test
    @DisplayName("LAZY: the status report reads 200 orders with 1 statement and loads no customer")
    void reportDoesNotLoadCustomers() {
        Measured request = get("/api/orders/report");

        assertThat(request.status()).isEqualTo(200);
        assertThat((int) request.json("$.NEW")).isEqualTo(66);
        assertThat((int) request.json("$.PAID")).isEqualTo(67);
        assertThat((int) request.json("$.SHIPPED")).isEqualTo(67);
        assertThat(request.statementCount()).as(request.describe()).isEqualTo(1);
        assertThat(request.statementsFrom("customer")).as("statements that read a customer nobody asked for").isEmpty();
    }

    @Test
    @DisplayName("LAZY: loading one order leaves its customer as a proxy that is not loaded yet")
    void customerIsAProxy() {
        boolean customerLoaded = inTransaction(() -> {
            Order order = entityManager.find(Order.class, 1L);
            return Hibernate.isInitialized(order.getCustomer());
        });

        assertThat(customerLoaded).as("Order.customer loaded although nobody used it").isFalse();
    }

    @Test
    @DisplayName("LAZY: loading an order's items leaves each item's product as a proxy that is not loaded yet")
    void productIsAProxy() {
        boolean productLoaded = inTransaction(() -> {
            Order order = entityManager.find(Order.class, 1L);
            return Hibernate.isInitialized(order.getItems().get(0).getProduct());
        });

        assertThat(productLoaded).as("OrderItem.product loaded although nobody used it").isFalse();
    }
}
