package com.amigoscode.pagination.order;

import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    /**
     * Newest first. Many orders share one created_at, so created_at alone does not say which of them
     * comes first. The id breaks the tie: it is unique, so the order is always the same.
     */
    static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final OrderRepository orders;

    public OrderService(OrderRepository orders) {
        this.orders = orders;
    }

    /** GET /api/orders?page=&size= : every order, with OFFSET. */
    public OffsetPage page(int page, int size) {
        Slice<Order> result = orders.findAllBy(PageRequest.of(page, size, NEWEST_FIRST));
        return new OffsetPage(responses(result.getContent()), page, size, result.hasNext());
    }

    /** GET /api/customers/{id}/orders?page=&size= : the orders of one customer, with OFFSET. */
    public OffsetPage history(long customerId, int page, int size) {
        Slice<Order> result = orders.findByCustomerId(customerId, PageRequest.of(page, size, NEWEST_FIRST));
        return new OffsetPage(responses(result.getContent()), page, size, result.hasNext());
    }

    /** GET /api/orders/feed?after=&size= : every order, with a cursor. after is null for the first page. */
    public FeedPage feed(Cursor after, int size) {
        // One row more than the page: if it comes back, there is a next page. No COUNT needed.
        List<Order> rows = after == null
                ? orders.feedFirstPage(size + 1)
                : orders.feedAfter(after.createdAt(), after.id(), size + 1);
        boolean hasNext = rows.size() > size;
        List<Order> items = hasNext ? rows.subList(0, size) : rows;
        String nextCursor = hasNext ? Cursor.after(items.getLast()).encode() : null;
        return new FeedPage(responses(items), nextCursor, hasNext);
    }

    private static List<OrderResponse> responses(List<Order> orders) {
        return orders.stream().map(OrderResponse::of).toList();
    }
}
