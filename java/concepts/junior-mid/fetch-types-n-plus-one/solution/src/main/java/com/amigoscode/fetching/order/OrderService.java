package com.amigoscode.fetching.order;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every method is one read-only transaction. The Hibernate session lives exactly as long as the
 * transaction, so everything lazy has to be touched in here, before the method returns.
 */
@Service
public class OrderService {

    private static final Sort BY_ID = Sort.by("id");

    private final OrderRepository orders;

    public OrderService(OrderRepository orders) {
        this.orders = orders;
    }

    /** GET /api/orders. The same list, loaded four different ways. */
    @Transactional(readOnly = true)
    public List<OrderSummary> list(FetchPlan plan) {
        return switch (plan) {
            // The bug. Kept as it is so you can always compare against it: one query for the
            // orders, then OrderSummary.from() loads every customer one at a time.
            case NAIVE -> summaries(orders.findAll(BY_ID));
            case JOIN_FETCH -> summaries(orders.findAllJoinFetchCustomer());
            case ENTITY_GRAPH -> summaries(orders.findAllByOrderById());
            case PROJECTION -> orders.findAllSummaries();
        };
    }

    /** GET /api/orders/report. Orders per status. Never looks at a customer. */
    @Transactional(readOnly = true)
    public Map<OrderStatus, Long> statusReport() {
        return orders.findAll().stream()
                .collect(Collectors.groupingBy(Order::getStatus, TreeMap::new, Collectors.counting()));
    }

    /**
     * GET /api/orders/{id}. The mapping to OrderDetails reads the customer, the items and the
     * products, so it has to happen while the transaction (and with it the session) is open.
     */
    @Transactional(readOnly = true)
    public OrderDetails details(long id) {
        Order order = orders.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
        return OrderDetails.from(order);
    }

    /** GET /api/orders/page. One page of orders with their items. */
    @Transactional(readOnly = true)
    public OrderPage page(int page, int size) {
        Page<Order> result = orders.findPage(PageRequest.of(page, size, BY_ID));
        List<OrderDetails> content = result.getContent().stream().map(OrderDetails::from).toList();
        return new OrderPage(content, page, size, result.getTotalElements());
    }

    private static List<OrderSummary> summaries(List<Order> orders) {
        return orders.stream().map(OrderSummary::from).toList();
    }
}
