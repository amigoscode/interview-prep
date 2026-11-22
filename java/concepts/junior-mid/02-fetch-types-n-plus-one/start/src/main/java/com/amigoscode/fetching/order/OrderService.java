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
 * Loads orders and turns them into the DTOs the controller returns. The Hibernate session lives
 * exactly as long as the transaction: with spring.jpa.open-in-view=false, nothing can be loaded
 * after a @Transactional method has returned.
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
            // The bug. Do not fix this one: it stays naive for ever, so you can always compare
            // against it. One query for the orders, then OrderSummary.from() reads every customer.
            case NAIVE -> summaries(orders.findAll(BY_ID));
            // TODO Task 6: load the orders with a JOIN FETCH query instead of findAll.
            case JOIN_FETCH -> summaries(orders.findAll(BY_ID));
            // TODO Task 6: load the orders with an @EntityGraph method instead of findAll.
            case ENTITY_GRAPH -> summaries(orders.findAll(BY_ID));
            // TODO Task 7: ask the repository for List<OrderSummary> directly. No entities, no summaries().
            case PROJECTION -> summaries(orders.findAll(BY_ID));
        };
    }

    /** GET /api/orders/report. Orders per status. Never looks at a customer. */
    @Transactional(readOnly = true)
    public Map<OrderStatus, Long> statusReport() {
        return orders.findAll().stream()
                .collect(Collectors.groupingBy(Order::getStatus, TreeMap::new, Collectors.counting()));
    }

    /**
     * GET /api/orders/{id}. OrderDetails.from() reads the customer, the items and the products.
     *
     * TODO Task 4: this request answers 500. Run ./demo --endpoint order, read the message, and
     *      work out where the session ended. Do not turn open-in-view on.
     */
    public OrderDetails details(long id) {
        Order order = orders.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
        return OrderDetails.from(order);
    }

    /**
     * GET /api/orders/page. One page of orders with their items.
     *
     * TODO Task 9: the fix is in OrderRepository.findPage and in the mapping (Order, Product).
     */
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
