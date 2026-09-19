package com.foodtakeway;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("order")
public class OrderApi {

    @Autowired
    OrderService orderService;

    @GetMapping("hello")
    public ResponseEntity<String> hello() {
        return new ResponseEntity<>("Hello World!", HttpStatus.OK);
    }

    @PostMapping
    public ResponseEntity<String> placeOrder(@RequestBody OrderRequest orderRequest) {
        System.out.println("Order received: " + orderRequest);
        orderService.placeOrder(orderRequest.amount, orderRequest.userEmail);

        return new ResponseEntity<>("Order Placed!", HttpStatus.OK);
    }
}

class OrderRequest {
    String userEmail;
    double amount;

    public OrderRequest(String userEmail, double amount) {
        this.userEmail = userEmail;
        this.amount = amount;
    }

    public String toString() {
        return "OrderRequest [userEmail=" + userEmail + ", amount=" + amount + "]";
    }
}
