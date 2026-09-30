package com.amigoscode.orderapi.order;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * The HTTP contract. Every endpoint answers 501 Not Implemented until you build it. Change the
 * signatures freely (cancel could be DELETE instead: story 2 asks you to choose).
 */
@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orders;

    public OrderController(OrderService orders) {
        this.orders = orders;
    }

    /** Story 1: 201 Created, a Location header and the order. Story 4 adds the header, story 5 the limit. */
    @PostMapping
    public ResponseEntity<Order> create(
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody CreateOrderRequest request) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "story 1");
    }

    /** Story 1: the order, or 404. */
    @GetMapping("/{id}")
    public Order get(@PathVariable UUID id) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "story 1");
    }

    /** Story 2: one page of a customer's orders. */
    @GetMapping
    public OrderPage list(@RequestParam String customerId,
                          @RequestParam(defaultValue = "0") int page,
                          @RequestParam(defaultValue = "20") int size) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "story 2");
    }

    /** Story 2: replace the items while the order is still CREATED. */
    @PutMapping("/{id}")
    public Order updateItems(@PathVariable UUID id, @RequestBody UpdateOrderItemsRequest request) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "story 2");
    }

    /** Story 2: cancel the order. */
    @PostMapping("/{id}/cancel")
    public Order cancel(@PathVariable UUID id) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "story 2");
    }
}
