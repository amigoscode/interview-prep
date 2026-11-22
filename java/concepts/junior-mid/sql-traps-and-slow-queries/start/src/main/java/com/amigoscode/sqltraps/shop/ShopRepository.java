package com.amigoscode.sqltraps.shop;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Runs the queries from ShopQueries. Nothing to change here. */
@Repository
public class ShopRepository {

    private final JdbcClient jdbc;

    public ShopRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> paidOrdersPerCustomer() {
        return rows(ShopQueries.paidOrdersPerCustomer());
    }

    public List<Map<String, Object>> customersWhoNeverOrdered() {
        return rows(ShopQueries.customersWhoNeverOrdered());
    }

    public List<Map<String, Object>> revenuePerCustomer() {
        return rows(ShopQueries.revenuePerCustomer());
    }

    public List<Map<String, Object>> bigSpenders(long minCents) {
        return rows(ShopQueries.bigSpenders(minCents));
    }

    public List<Map<String, Object>> countrySpend(String country) {
        return rows(ShopQueries.countrySpend(country));
    }

    public List<Map<String, Object>> recentOrders(int pageSize) {
        return rows(ShopQueries.recentOrders(pageSize));
    }

    public List<Map<String, Object>> expiredOrders() {
        return rows(ShopQueries.expiredOrders());
    }

    public List<Map<String, Object>> ordersOfCustomer(long customerId) {
        return rows(ShopQueries.ordersOfCustomer(customerId));
    }

    public List<Map<String, Object>> ordersOfDay(LocalDate day) {
        return rows(ShopQueries.ordersOfDay(day));
    }

    /** One map per row: column name to value, in the order of the SELECT list. */
    public List<Map<String, Object>> rows(Query query) {
        return jdbc.sql(query.sql()).params(query.args()).query().listOfRows();
    }
}
