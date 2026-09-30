package com.amigoscode.orderapi.order;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    static final Instant NOW = Instant.parse("2026-09-30T12:00:00Z");

    @Test
    void newOrderIsCreatedWithAComputedTotal() {
        Order order = Order.create("c1", "r1",
                List.of(new OrderItem("Pizza", 2, 950), new OrderItem("Cola", 1, 250)), NOW);
        assertThat(order.status()).isEqualTo(OrderStatus.CREATED);
        assertThat(order.totalCents()).isEqualTo(2150);
        assertThat(order.createdAt()).isEqualTo(NOW);
        assertThat(order.id()).isNotNull();
    }

    @Test
    void changingItemsRecomputesTheTotalAndKeepsIdentity() {
        Order order = Order.create("c1", "r1", List.of(new OrderItem("Pizza", 1, 950)), NOW);
        Order updated = order.withItems(List.of(new OrderItem("Salad", 3, 700)));
        assertThat(updated.id()).isEqualTo(order.id());
        assertThat(updated.totalCents()).isEqualTo(2100);
        assertThat(order.totalCents()).isEqualTo(950); // the original is untouched
    }

    @Test
    void cancelledOrderCannotBeChangedButCanBeCancelledAgain() {
        Order cancelled = Order.create("c1", "r1", List.of(new OrderItem("Pizza", 1, 950)), NOW).cancel();
        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(cancelled.cancel()).isSameAs(cancelled);
        assertThatThrownBy(() -> cancelled.withItems(List.of(new OrderItem("Salad", 1, 700))))
                .isInstanceOf(OrderNotModifiableException.class);
    }

    @Test
    void itemsCannotBeChangedFromOutside() {
        List<OrderItem> items = new ArrayList<>(List.of(new OrderItem("Pizza", 1, 950)));
        Order order = Order.create("c1", "r1", items, NOW);
        items.add(new OrderItem("Free lunch", 1, 1));
        assertThat(order.items()).hasSize(1);
        assertThatThrownBy(() -> order.items().add(new OrderItem("x", 1, 1)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void overflowingTotalFailsLoudlyInsteadOfWrappingAround() {
        List<OrderItem> items = List.of(new OrderItem("Gold", 50, Long.MAX_VALUE / 10));
        assertThatThrownBy(() -> Order.create("c1", "r1", items, NOW)).isInstanceOf(ArithmeticException.class);
    }
}
