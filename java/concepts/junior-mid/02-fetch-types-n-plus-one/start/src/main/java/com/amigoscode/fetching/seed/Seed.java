package com.amigoscode.fetching.seed;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Fills the shop with data, using plain SQL. The data is the same on every run: order 7 always has
 * the same customer, the same items and the same total. Nothing to change here.
 */
@Component
public class Seed implements ApplicationRunner {

    public static final int DEFAULT_ORDERS = 200;
    public static final int PRODUCTS = 20;

    private final JdbcTemplate jdbc;

    public Seed(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** An empty database gets the default shop when the application starts. */
    @Override
    public void run(ApplicationArguments args) {
        Integer customers = jdbc.queryForObject("select count(*) from customer", Integer.class);
        if (customers == null || customers == 0) {
            reset(DEFAULT_ORDERS, DEFAULT_ORDERS);
        }
    }

    /**
     * Empties the shop and fills it again: {@code orders} orders shared out over {@code customers}
     * customers, 1 to 3 items per order, 20 products.
     */
    public void reset(int orders, int customers) {
        jdbc.execute("truncate table order_item, orders, customer, product restart identity");
        jdbc.update("""
                insert into product (id, name, price)
                select g,
                       (array['Keyboard','Mouse','Monitor','Laptop stand','Webcam','Headset','USB hub',
                              'Desk lamp','Notebook','Backpack','Charger','HDMI cable','Mouse pad',
                              'Microphone','Speaker','SSD drive','Router','Phone case','Tablet','Printer'])[g],
                       (5 + g * 2.5)::numeric(10, 2)
                  from generate_series(1, ?) g
                """, PRODUCTS);
        jdbc.update("""
                insert into customer (id, name, email, city)
                select g,
                       'Customer ' || g,
                       'customer' || g || '@example.com',
                       (array['London','Lagos','Lisbon','Lima','Leeds'])[1 + g % 5]
                  from generate_series(1, ?) g
                """, customers);
        jdbc.update("""
                insert into orders (id, customer_id, status, placed_at)
                select g,
                       1 + (g - 1) % ?,
                       (array['NEW','PAID','SHIPPED'])[1 + g % 3],
                       timestamptz '2026-01-01 09:00:00+00' + g * interval '1 minute'
                  from generate_series(1, ?) g
                """, customers, orders);
        jdbc.update("""
                insert into order_item (order_id, product_id, quantity)
                select o, 1 + (o * 7 + k) % ?, 1 + (o + k) % 3
                  from generate_series(1, ?) o
                 cross join generate_series(0, 2) k
                 where k <= o % 3
                 order by o, k
                """, PRODUCTS, orders);
        jdbc.execute("analyze");
    }
}
