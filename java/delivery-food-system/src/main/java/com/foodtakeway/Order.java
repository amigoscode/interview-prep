package com.foodtakeway;

import org.springframework.data.mongodb.core.mapping.Document;

@Document
class Order {
    String orderId;
    double amount;
    String userEmail;
    boolean isProcessed;

    public Order(String orderId, double amount, String userEmail) {
        this.orderId = orderId;
        this.amount = amount;
        this.isProcessed = false;
        this.userEmail = userEmail;
    }

    // This method should stay as it is. Just pretend it is a long time operation we need to do.
    public void longRunningOrderProcess() {
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
