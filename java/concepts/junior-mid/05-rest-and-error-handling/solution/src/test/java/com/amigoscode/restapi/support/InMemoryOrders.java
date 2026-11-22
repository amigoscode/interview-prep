package com.amigoscode.restapi.support;

import com.amigoscode.restapi.order.Order;
import com.amigoscode.restapi.order.OrderRepository;
import com.amigoscode.restapi.order.OrderStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** A hand-written stand-in for the database, so the unit tests need no Spring and no Postgres. */
public final class InMemoryOrders implements OrderRepository {

    private final Map<Long, Order> rows = new TreeMap<>();
    private long nextId = 1;

    @Override
    public List<Order> findAllByOrderById() {
        return new ArrayList<>(rows.values());
    }

    @Override
    public List<Order> findByStatusOrderById(OrderStatus status) {
        return rows.values().stream().filter(order -> order.getStatus() == status).toList();
    }

    @Override
    public Optional<Order> findById(Long id) {
        return Optional.ofNullable(rows.get(id));
    }

    @Override
    public Order save(Order order) {
        if (order.getId() == null) {
            order.setId(nextId++);
        }
        rows.put(order.getId(), order);
        return order;
    }

    @Override
    public void delete(Order order) {
        rows.remove(order.getId());
    }

    public int size() {
        return rows.size();
    }
}
