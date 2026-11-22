package com.amigoscode.fetching;

import static org.assertj.core.api.Assertions.assertThat;

import com.amigoscode.fetching.customer.Customer;
import com.amigoscode.fetching.order.Order;
import com.amigoscode.fetching.order.OrderDetails;
import com.amigoscode.fetching.order.OrderStatus;
import com.amigoscode.fetching.order.OrderSummary;
import com.amigoscode.fetching.product.Product;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the DTO mapping: plain objects, no Spring, no database, no Hibernate. They show
 * exactly which getters the mapping calls. On a real, lazily loaded order each of those calls can
 * be a SQL statement.
 */
@Tag("baseline")
class OrderMappingTest {

    private static final Customer ADA = new Customer(3L, "Ada", "ada@example.com", "London");
    private static final Instant PLACED_AT = Instant.parse("2026-01-01T09:07:00Z");

    @Test
    @DisplayName("Mapping: an order summary carries the order's id, status and time, and the customer's name and city")
    void summaryCarriesTheCustomer() {
        Order order = new Order(7L, OrderStatus.PAID, PLACED_AT, ADA);

        OrderSummary summary = OrderSummary.from(order);

        assertThat(summary).isEqualTo(new OrderSummary(7, OrderStatus.PAID, PLACED_AT, "Ada", "London"));
    }

    @Test
    @DisplayName("Mapping: order details list every item and add up quantity x price for the total")
    void detailsAddUpTheItems() {
        Order order = new Order(7L, OrderStatus.NEW, PLACED_AT, ADA)
                .addItem(new Product(1L, "Keyboard", new BigDecimal("40.00")), 2)
                .addItem(new Product(2L, "Mouse", new BigDecimal("12.50")), 3);

        OrderDetails details = OrderDetails.from(order);

        assertThat(details.customerName()).isEqualTo("Ada");
        assertThat(details.items()).containsExactly(
                new OrderDetails.Line("Keyboard", 2, new BigDecimal("40.00")),
                new OrderDetails.Line("Mouse", 3, new BigDecimal("12.50")));
        assertThat(details.total()).isEqualByComparingTo("117.50");
    }
}
