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
     * TODO Task 2: the report shows 4 customers and the shop has 8. Nothing fails. Find out why
     * the LEFT JOIN does not keep the customers without a PAID order, and fix the query.
     */
    public static Query paidOrdersPerCustomer() {
        return new Query("""
                SELECT c.id, c.name, COUNT(o.id) AS paid_orders
                  FROM customers c
                  LEFT JOIN orders o ON o.customer_id = c.id
                 WHERE o.status = 'PAID'
                 GROUP BY c.id, c.name
                 ORDER BY c.id
                """);
    }

    /**
     * GET /api/reports/never-ordered: the customers who have never placed an order.
     *
     * TODO Task 4: the report is empty, and two customers have never ordered. It worked until the
     * shop allowed guest checkout (an order whose customer_id is NULL). Rewrite it so that a NULL
     * in orders.customer_id cannot change the answer.
     */
    public static Query customersWhoNeverOrdered() {
        return new Query("""
                SELECT c.id, c.name
                  FROM customers c
                 WHERE c.id NOT IN (SELECT o.customer_id
                                      FROM orders o)
                 ORDER BY c.id
                """);
    }

    /**
     * GET /api/reports/revenue: per customer, how many PAID orders, the revenue and the units bought.
     *
     * TODO Task 5: the report says 820.00 of revenue. The PAID orders of these customers add up to
     * 480.00. The orders column is too high as well. Only units is right. Fix the query so that
     * every order is counted once.
     */
    public static Query revenuePerCustomer() {
        return new Query("""
                SELECT c.id, c.name,
                       COUNT(*)           AS orders,
                       SUM(o.total_cents) AS revenue_cents,
                       SUM(i.quantity)    AS units
                  FROM customers c
                  JOIN orders o ON o.customer_id = c.id
                  JOIN order_items i ON i.order_id = o.id
                 WHERE o.status = 'PAID'
                 GROUP BY c.id, c.name
                 ORDER BY c.id
                """);
    }

    /**
     * GET /api/reports/big-spenders?min=10000: customers whose PAID orders add up to more than min.
     *
     * TODO Task 5: Barbara spent 120.00 in three orders of 40.00 and is not in the report. Ada is
     * in it with 150.00, and she spent 180.00. The condition is tested at the wrong moment.
     */
    public static Query bigSpenders(long minCents) {
        return new Query("""
                SELECT c.id, c.name, SUM(o.total_cents) AS spent_cents
                  FROM customers c
                  JOIN orders o ON o.customer_id = c.id
                 WHERE o.status = 'PAID'
                   AND o.total_cents > ?
                 GROUP BY c.id, c.name
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
     * TODO Task 7: the rows are right and the report takes seconds. Find the step of the plan
     * where the time goes, then rewrite the query so that the orders table is read once. Keep
     * the customers who never paid in the report, with 0.
     */
    public static Query countrySpend(String country) {
        return new Query("""
                SELECT c.id, c.name,
                       (SELECT COUNT(*)
                          FROM orders o
                         WHERE o.customer_id = c.id
                           AND o.status = 'PAID') AS paid_orders,
                       (SELECT COALESCE(SUM(o.total_cents), 0)
                          FROM orders o
                         WHERE o.customer_id = c.id
                           AND o.status = 'PAID') AS spent_cents
                  FROM customers c
                 WHERE c.country = ?
                 ORDER BY c.id
                """, country);
    }

    /**
     * GET /api/orders/recent?size=20: the newest orders, for the first page of the admin screen.
     *
     * The screen shows 5 columns: id, customer_id, status, total_cents, created_at.
     *
     * TODO Task 8: pageSize is ignored. The query returns every order with every column, and the
     * application throws away all but the first 20. Fetch the 5 columns and one page, and never
     * more than MAX_PAGE_SIZE rows, whatever the client asks for.
     */
    public static Query recentOrders(int pageSize) {
        return new Query("""
                SELECT *
                  FROM orders
                 ORDER BY created_at DESC
                """);
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
