package com.amigoscode.pagination.order;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    /** Every order, newest first, with OFFSET: page 0 is the first page. */
    @GetMapping("/api/orders")
    public OffsetPage page(@RequestParam(required = false) Integer page,
                           @RequestParam(required = false) Integer size) {
        return service.page(Paging.page(page), Paging.size(size));
    }

    /** The orders of one customer, newest first, with OFFSET. */
    @GetMapping("/api/customers/{customerId}/orders")
    public OffsetPage history(@PathVariable long customerId,
                              @RequestParam(required = false) Integer page,
                              @RequestParam(required = false) Integer size) {
        return service.history(customerId, Paging.page(page), Paging.size(size));
    }

    /** Every order, newest first, with a cursor: send the nextCursor of one page as "after" to get the next. */
    @GetMapping("/api/orders/feed")
    public FeedPage feed(@RequestParam(required = false) String after,
                         @RequestParam(required = false) Integer size) {
        int pageSize = Paging.size(size);
        Cursor cursor = after == null || after.isEmpty() ? null : Cursor.decode(after);
        return service.feed(cursor, pageSize);
    }
}
