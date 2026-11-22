package com.amigoscode.sqltraps.shop;

import java.time.LocalDate;

/**
 * Every query the shop runs, in one place. The application, ./demo and the tests all take their
 * SQL from here, so what you see in the demo is the real query.
 *
 * Money is stored in cents: 10000 is 100.00.
 */
public final class ShopQueries {

    /** The most rows the "recent orders" page may ask for, whatever the client sends. */
    public static final int MAX_PAGE_SIZE = 100;

    // ---------------------------------------------------------------------------------------
    // Part A: reports. A report that is wrong does not fail. It just shows the wrong numbers.
    // ---------------------------------------------------------------------------------------

    /**
     * GET /api/reports/paid-orders: every customer, with the number of PAID orders they have.
     * A customer with no PAID order must be in the report, with 0.
     *
     * The status condition is part of the ON clause: it decides which orders are matched to a
     * customer. A customer with no matching order still comes out of the LEFT JOIN, with NULLs
     * in the order columns, and COUNT(o.id) counts those NULLs as 0.
     */
    public static Query paidOrdersPerCustomer() {
        return new Query("""
                SELECT c.id, c.name, COUNT(o.id) AS paid_orders
                  FROM customers c
                  LEFT JOIN orders o ON o.customer_id = c.id
                                    AND o.status = 'PAID'
                 GROUP BY c.id, c.name
                 ORDER BY c.id
                """);
    }

    /**
     * GET /api/reports/never-ordered: the customers who have never placed an order.
     *
     * NOT EXISTS asks one yes-or-no question per customer: "is there an order of this customer?".
     * A guest order with customer_id NULL matches nobody, so it cannot disturb the answer.
     */
    public static Query customersWhoNeverOrdered() {
        return new Query("""
                SELECT c.id, c.name
                  FROM customers c
                 WHERE NOT EXISTS (SELECT 1
                                     FROM orders o
                                    WHERE o.customer_id = c.id)
                 ORDER BY c.id
                """);
    }

    /**
     * GET /api/reports/revenue: per customer, how many PAID orders, the revenue and the units bought.
     *
     * The order lines are added up per order first, in the subquery. After that the join finds
     * exactly one row per order, so every order total is added once.
     */
    public static Query revenuePerCustomer() {
        return new Query("""
                SELECT c.id, c.name,
                       COUNT(o.id)        AS orders,
                       SUM(o.total_cents) AS revenue_cents,
                       SUM(i.units)       AS units
                  FROM customers c
                  JOIN orders o ON o.customer_id = c.id
                  JOIN (SELECT order_id, SUM(quantity) AS units
                          FROM order_items
                         GROUP BY order_id) i ON i.order_id = o.id
                 WHERE o.status = 'PAID'
                 GROUP BY c.id, c.name
                 ORDER BY c.id
                """);
    }

    /**
     * GET /api/reports/big-spenders?min=10000: customers whose PAID orders add up to more than min.
     *
     * WHERE chooses the rows that go into the groups. HAVING chooses the groups, after the totals
     * are known. "Add up to more than" is a question about the total, so it belongs in HAVING.
     */
    public static Query bigSpenders(long minCents) {
        return new Query("""
                SELECT c.id, c.name, SUM(o.total_cents) AS spent_cents
                  FROM customers c
                  JOIN orders o ON o.customer_id = c.id
                 WHERE o.status = 'PAID'
                 GROUP BY c.id, c.name
                HAVING SUM(o.total_cents) > ?
                 ORDER BY c.id
                """, minCents);
    }

    // ---------------------------------------------------------------------------------------
    // Part B: queries that return the right rows, slowly.
    // ---------------------------------------------------------------------------------------

    /**
     * GET /api/reports/country-spend?country=IE: every customer of one country, with the number of
     * PAID orders and what they spent.
     *
     * One pass over orders: the join matches every PAID order to its customer, then GROUP BY adds
     * them up. (The LEFT JOIN with the status in ON keeps the customers who never paid, as in
     * paidOrdersPerCustomer.)
     */
    public static Query countrySpend(String country) {
        return new Query("""
                SELECT c.id, c.name,
                       COUNT(o.id)                     AS paid_orders,
                       COALESCE(SUM(o.total_cents), 0) AS spent_cents
                  FROM customers c
                  LEFT JOIN orders o ON o.customer_id = c.id
                                    AND o.status = 'PAID'
                 WHERE c.country = ?
                 GROUP BY c.id, c.name
                 ORDER BY c.id
                """, country);
    }

    /**
     * GET /api/orders/recent?size=20: the newest orders, for the first page of the admin screen.
     *
     * Only the columns the screen shows, and only one page of rows. The page size is capped: a
     * client that asks for a million rows gets MAX_PAGE_SIZE.
     */
    public static Query recentOrders(int pageSize) {
        return new Query("""
                SELECT id, customer_id, status, total_cents, created_at
                  FROM orders
                 ORDER BY created_at DESC
                 LIMIT ?
                """, Math.clamp(pageSize, 1, MAX_PAGE_SIZE));
    }

    /**
     * GET /api/orders/expired: orders that timed out unpaid, with the customer to remind.
     * Nothing is wrong with this SQL. Task 9 shows what happens to it when the statistics are stale.
     */
    public static Query expiredOrders() {
        return new Query("""
                SELECT o.id, o.total_cents, c.name, c.email
                  FROM orders o
                  JOIN customers c ON c.id = o.customer_id
                 WHERE o.status = 'EXPIRED'
                """);
    }

    /** GET /api/customers/{id}/orders: the orders of one customer. There is no index on customer_id. */
    public static Query ordersOfCustomer(long customerId) {
        return new Query("""
                SELECT id, status, total_cents, created_at
                  FROM orders
                 WHERE customer_id = ?
                """, customerId);
    }

    /** GET /api/orders?day=2023-02-01: the orders of one day. There is an index on created_at. */
    public static Query ordersOfDay(LocalDate day) {
        return new Query("""
                SELECT id, status, total_cents, created_at
                  FROM orders
                 WHERE created_at >= ?
                   AND created_at < ?
                """, day.atStartOfDay(), day.plusDays(1).atStartOfDay());
    }

    private ShopQueries() {
    }
}
