package com.amigoscode.pagination.order;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    /** Newest first. */
    static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"));

    private final OrderRepository orders;

    public OrderService(OrderRepository orders) {
        this.orders = orders;
    }

    /** GET /api/orders?page=&size= : every order, with OFFSET. */
    public OffsetPage page(int page, int size) {
        Page<Order> result = orders.findAll(PageRequest.of(page, size, NEWEST_FIRST));
        return new OffsetPage(responses(result.getContent()), page, size, result.hasNext());
    }

    /** GET /api/customers/{id}/orders?page=&size= : the orders of one customer, with OFFSET. */
    public OffsetPage history(long customerId, int page, int size) {
        Page<Order> result = orders.findByCustomerId(customerId, PageRequest.of(page, size, NEWEST_FIRST));
        return new OffsetPage(responses(result.getContent()), page, size, result.hasNext());
    }

    /** GET /api/orders/feed?after=&size= : every order, with a cursor. after is null for the first page. */
    public FeedPage feed(Cursor after, int size) {
        List<Order> rows = after == null
                ? orders.feedFirstPage(size)
                : orders.feedAfter(after.createdAt(), size);
        // A full page means there is probably more.
        boolean hasNext = rows.size() == size;
        String nextCursor = hasNext ? Cursor.after(rows.getLast()).encode() : null;
        return new FeedPage(responses(rows), nextCursor, hasNext);
    }

    private static List<OrderResponse> responses(List<Order> orders) {
        return orders.stream().map(OrderResponse::of).toList();
    }
}
