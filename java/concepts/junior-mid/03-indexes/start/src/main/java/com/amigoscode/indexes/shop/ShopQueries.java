package com.amigoscode.indexes.shop;

import java.time.LocalDate;

/**
 * Every query the shop runs, in one place. The application, ./demo and the tests all take their
 * SQL from here, so the plan you see in the demo is the plan of the real query.
 */
public final class ShopQueries {

    /** GET /api/orders/track/{trackingNumber}: "where is my parcel?" */
    public static Query trackOrder(String trackingNumber) {
        return new Query("""
                SELECT id, customer_id, status, total_cents, created_at
                  FROM orders
                 WHERE tracking_number = ?
                """, trackingNumber);
    }

    /** GET /api/customers/{id}/orders: the customer's 5 newest orders, for the account page. */
    public static Query latestOrders(long customerId) {
        return new Query("""
                SELECT id, status, total_cents, created_at
                  FROM orders
                 WHERE customer_id = ?
                 ORDER BY created_at DESC
                 LIMIT 5
                """, customerId);
    }

    /** GET /api/customers?email=...: log in by email, whatever case the user typed it in. */
    public static Query customerByEmail(String email) {
        return new Query("""
                SELECT id, email, name
                  FROM customers
                 WHERE lower(email) = lower(?)
                """, email);
    }

    /**
     * GET /api/reports/daily-sales?day=2025-06-15: number of orders and revenue for one day.
     *
     * TODO Task 6: created_at is the first column of an index, and this query is still a Seq Scan.
     */
    public static Query dailySales(LocalDate day) {
        return new Query("""
                SELECT count(*) AS orders, coalesce(sum(total_cents), 0) AS revenue_cents
                  FROM orders
                 WHERE date(created_at) = ?
                """, day);
    }

    /** GET /api/orders/pending: the warehouse queue, the 50 oldest orders still waiting. */
    public static Query pendingQueue() {
        return new Query("""
                SELECT id, customer_id, total_cents, created_at
                  FROM orders
                 WHERE status = 'PENDING'
                 ORDER BY created_at
                 LIMIT 50
                """);
    }

    /** GET /api/customers/{id}/spend: how many orders a customer placed and what they spent. */
    public static Query customerSpend(long customerId) {
        return new Query("""
                SELECT count(*) AS orders, coalesce(sum(total_cents), 0) AS spent_cents
                  FROM orders
                 WHERE customer_id = ?
                """, customerId);
    }

    /** GET /api/reports/by-status?status=DELIVERED: revenue of all orders in one status. */
    public static Query salesByStatus(String status) {
        return new Query("""
                SELECT count(*) AS orders, coalesce(sum(total_cents), 0) AS revenue_cents
                  FROM orders
                 WHERE status = ?
                """, status);
    }

    private ShopQueries() {
    }
}
