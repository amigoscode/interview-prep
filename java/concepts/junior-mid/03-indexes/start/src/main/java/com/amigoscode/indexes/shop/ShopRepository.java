package com.amigoscode.indexes.shop;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Runs the queries from ShopQueries. Nothing to change here. */
@Repository
public class ShopRepository {

    private final JdbcTemplate jdbc;

    public ShopRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Map<String, Object>> trackOrder(String trackingNumber) {
        return rows(ShopQueries.trackOrder(trackingNumber)).stream().findFirst();
    }

    public List<Map<String, Object>> latestOrders(long customerId) {
        return rows(ShopQueries.latestOrders(customerId));
    }

    public Optional<Map<String, Object>> customerByEmail(String email) {
        return rows(ShopQueries.customerByEmail(email)).stream().findFirst();
    }

    public Map<String, Object> dailySales(LocalDate day) {
        return rows(ShopQueries.dailySales(day)).getFirst();
    }

    public List<Map<String, Object>> pendingQueue() {
        return rows(ShopQueries.pendingQueue());
    }

    public Map<String, Object> customerSpend(long customerId) {
        return rows(ShopQueries.customerSpend(customerId)).getFirst();
    }

    public Map<String, Object> salesByStatus(String status) {
        return rows(ShopQueries.salesByStatus(status)).getFirst();
    }

    private List<Map<String, Object>> rows(Query query) {
        return jdbc.queryForList(query.sql(), query.args().toArray());
    }
}
