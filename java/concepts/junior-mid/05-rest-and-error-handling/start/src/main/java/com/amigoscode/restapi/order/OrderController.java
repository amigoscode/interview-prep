package com.amigoscode.restapi.order;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    // TODO Task 7: every method here returns the entity. Which of its fields should a client never see?
    // TODO Task 4: "DONE" is not a status. What does valueOf do with it, and whose mistake is that?
    @GetMapping
    public List<Order> list(@RequestParam(required = false) String status) {
        return service.list(status == null ? null : OrderStatus.valueOf(status));
    }

    @GetMapping("/{id}")
    public Order get(@PathVariable long id) {
        return service.get(id);
    }

    // TODO Task 5: OrderRequest has @NotBlank and @Positive. Is anybody checking them?
    // TODO Task 10: something new was created. Which status code says so, and where does the client find it?
    @PostMapping
    public Order create(@RequestBody OrderRequest request) {
        return service.create(request);
    }

    // TODO Task 7: the request body is the entity, so the client can set every column.
    @PutMapping("/{id}")
    public Order update(@PathVariable long id, @RequestBody Order order) {
        return service.update(id, order);
    }

    // TODO Task 8: PUT replaces the whole order. Add a PATCH that changes only what was sent.

    // TODO Task 2: this changes data. Which HTTP methods are allowed to do that?
    @GetMapping("/{id}/cancel")
    public Order cancel(@PathVariable long id) {
        return service.cancel(id);
    }

    // TODO Task 10: there is nothing left to show. Which status code says "done, and no body"?
    @DeleteMapping("/{id}")
    public Map<String, String> delete(@PathVariable long id) {
        service.delete(id);
        return Map.of("message", "Order " + id + " deleted");
    }
}
