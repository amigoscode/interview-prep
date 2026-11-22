package com.amigoscode.locking.order;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    public record PlaceOrderRequest(long productId, int quantity) {
    }

    public record OrderResponse(long orderId, long productId, int quantity) {
    }

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse placeOrder(@RequestParam(defaultValue = "naive") String strategy,
                                    @RequestBody PlaceOrderRequest request) {
        if (request.quantity() < 1) {
            throw new IllegalArgumentException("quantity must be at least 1");
        }
        Order order = orderService.placeOrder(Strategy.from(strategy), request.productId(), request.quantity());
        return new OrderResponse(order.getId(), order.getProductId(), order.getQuantity());
    }
}
