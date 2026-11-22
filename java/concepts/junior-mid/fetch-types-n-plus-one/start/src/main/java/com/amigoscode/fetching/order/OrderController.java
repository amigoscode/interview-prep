package com.amigoscode.fetching.order;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public List<OrderSummary> list(@RequestParam(defaultValue = "naive") String fetch) {
        return orderService.list(FetchPlan.from(fetch));
    }

    @GetMapping("/report")
    public Map<OrderStatus, Long> report() {
        return orderService.statusReport();
    }

    @GetMapping("/page")
    public OrderPage page(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("page must be 0 or more, size must be from 1 to 100");
        }
        return orderService.page(page, size);
    }

    @GetMapping("/{id}")
    public OrderDetails details(@PathVariable long id) {
        return orderService.details(id);
    }
}
