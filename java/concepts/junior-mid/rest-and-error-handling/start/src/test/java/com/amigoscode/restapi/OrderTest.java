package com.amigoscode.restapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.amigoscode.restapi.order.Order;
import com.amigoscode.restapi.order.OrderStatus;
import com.amigoscode.restapi.order.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Unit tests for the rules inside Order. Plain Java: no Spring, no database, no HTTP. */
@Tag("baseline")
class OrderTest {

    @Test
    @DisplayName("Order: the total is the unit price times the quantity")
    void totalIsPriceTimesQuantity() {
        Order order = new Order("Nina Patel", Product.MONITOR, 3, "8 Queen Square, Bath");

        assertThat(order.getTotal()).isEqualByComparingTo("600.00");
        assertThat(order.getStatus()).as("a new order").isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("Order: a pending order and a paid order can be cancelled")
    void pendingAndPaidCanBeCancelled() {
        Order pending = order(OrderStatus.PENDING);
        Order paid = order(OrderStatus.PAID);

        pending.cancel();
        paid.cancel();

        assertThat(pending.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(paid.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("Order: cancelling a cancelled order changes nothing and does not fail")
    void cancellingTwiceChangesNothing() {
        Order order = order(OrderStatus.CANCELLED);

        order.cancel();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("Order: a shipped order cannot be cancelled and stays SHIPPED")
    void shippedCannotBeCancelled() {
        Order order = order(OrderStatus.SHIPPED);

        assertThatThrownBy(order::cancel).isInstanceOf(RuntimeException.class).hasMessageContaining("SHIPPED");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
    }

    private static Order order(OrderStatus status) {
        Order order = new Order("Nina Patel", Product.MOUSE, 1, "8 Queen Square, Bath");
        order.setStatus(status);
        return order;
    }
}
