package com.amigoscode.karat.fooddelivery;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * This is not a complete test suite, but tests some basic functionality of the code and shows how
 * to use it. One test fails on purpose: that is part 1.
 */
@Disabled("fooddelivery drill: remove this line to begin part 1")
class OrderManagerTest {

    @Test
    void orderManager() {
        OrderManager om = new OrderManager();
        om.addOrder(new Order(1, 10, 100, 25.0, 3.2, OrderStatus.PLACED));
        om.addOrder(new Order(2, 10, 101, 55.0, 1.4, OrderStatus.PREPARING));
        om.addOrder(new Order(3, 11, 102, 15.0, 6.0, OrderStatus.OUT_FOR_DELIVERY));
        om.addOrder(new Order(4, 11, 103, 40.0, 2.0, OrderStatus.DELIVERED));
        om.addOrder(new Order(5, 12, 104, 18.0, 4.5, OrderStatus.CANCELED));

        OrderStats stats = om.getOrderStatistics();

        assertThat(stats.totalOrders).isEqualTo(5);
        assertThat(stats.activeOrders).isEqualTo(3);
        assertThat(stats.closedOrders).isEqualTo(2);
    }

    @Test
    @Disabled("part 2: remove this line when part 1 is green")
    void addDelivery() {
        OrderManager om = new OrderManager();
        om.addOrder(new Order(1, 10, 100, 25.0, 3.2, OrderStatus.DELIVERED));
        om.addOrder(new Order(2, 10, 101, 55.0, 1.4, OrderStatus.DELIVERED));

        assertThat(om.addDelivery(1, new Delivery(101, 10, 40))).isTrue();
        assertThat(om.addDelivery(2, new Delivery(102, 50, 80))).isTrue();
        assertThat(om.addDelivery(1, new Delivery(103, 90, 150))).isTrue();
        // Non-existent order: delivery should be ignored.
        assertThat(om.addDelivery(999, new Delivery(105, 0, 10))).isFalse();
    }

    @Test
    @Disabled("part 2: remove this line when part 1 is green")
    void averageDeliveryTimeByRestaurant() {
        OrderManager om = new OrderManager();
        om.addOrder(new Order(1, 10, 100, 25.0, 3.2, OrderStatus.DELIVERED));
        om.addOrder(new Order(2, 10, 101, 55.0, 1.4, OrderStatus.DELIVERED));
        om.addOrder(new Order(3, 11, 102, 15.0, 6.0, OrderStatus.DELIVERED));

        om.addDelivery(1, new Delivery(101, 10, 40));
        om.addDelivery(2, new Delivery(102, 50, 80));
        om.addDelivery(2, new Delivery(103, 90, 150));
        om.addDelivery(3, new Delivery(104, 20, 50));
        om.addDelivery(999, new Delivery(105, 0, 10)); // ignored

        Map<Integer, Double> avg = om.getAverageDeliveryTimeByRestaurant();

        assertThat(avg.get(10)).isCloseTo(40.0, within(1e-4));
        assertThat(avg.get(11)).isCloseTo(30.0, within(1e-4));
    }

    @Test
    @Disabled("part 3: remove this line when part 2 is green")
    void deliveryFees() {
        OrderManager om = new OrderManager();
        om.addOrder(new Order(1, 10, 100, 25.0, 1.0, OrderStatus.PLACED));
        om.addOrder(new Order(2, 10, 101, 35.0, 2.1, OrderStatus.PLACED));
        om.addOrder(new Order(3, 11, 102, 55.0, 10.0, OrderStatus.PLACED));
        om.addOrder(new Order(4, 12, 103, 29.0, 1.2, OrderStatus.PLACED));

        Map<Integer, Double> fees = om.getDeliveryFees();

        assertThat(fees.get(1)).isCloseTo(2.0, within(1e-4));
        assertThat(fees.get(2)).isCloseTo(2.0, within(1e-4));
        assertThat(fees.get(3)).isCloseTo(0.0, within(1e-4));
        assertThat(fees.get(4)).isCloseTo(3.0, within(1e-4));
    }
}
