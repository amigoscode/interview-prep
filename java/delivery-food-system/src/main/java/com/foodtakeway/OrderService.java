package com.foodtakeway;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
class OrderService {
    List<Order> orders = new ArrayList<>();
    Random random = new Random();

    // Receive order and persist
    public void placeOrder(double amount, String userEmail) {
        String orderId = "ORD" + random.nextInt(1000);
        Order order = new Order(orderId, amount, userEmail);
        orders.add(order);
        System.out.println("Order placed: " + orderId + " Amount: " + amount + " user: " + userEmail);
        processOrder(order);
        notifyEvent("OrderProcessed", order.orderId);
    }

    /**
     * TODO placeOrder() and processOrder() are tightly coupled. What can we do to improve this coupling,
     *  considering that process can be a long running task and be a bottleneck?
     **/
    // Process order (imagine it can be invoicing, notification, stock change, etc.)
    private void processOrder(Order order) {
        if (order.amount > 100) {
            order.amount *= 0.9; // 10% discount for orders above 100
        }
        order.longRunningOrderProcess();
        order.isProcessed = true;
        System.out.println("Order processed: " + order.orderId + " Final Amount: " + order.amount + " user: " + order.userEmail);
    }


    private void notifyEvent(String event, String data) {
        System.out.println("Event Dispatched: " + event + " -> " + data);
    }
}
