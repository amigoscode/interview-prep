package com.amigoscode.orderapi;

import java.util.UUID;

/** Request bodies for the HTTP tests. Plain JSON text so the tests read like the curl calls. */
public final class OrderJson {

    private OrderJson() {
    }

    public static String newCustomer() {
        return "customer-" + UUID.randomUUID();
    }

    /** Two pizzas at 9.50 and one cola at 2.50: total 2150 cents. */
    public static String order(String customerId) {
        return """
                {
                  "customerId": "%s",
                  "restaurantId": "rest-42",
                  "items": [
                    { "name": "Margherita", "quantity": 2, "priceCents": 950 },
                    { "name": "Cola", "quantity": 1, "priceCents": 250 }
                  ]
                }
                """.formatted(customerId);
    }

    public static String orderWithItems(String customerId, String itemsJson) {
        return """
                { "customerId": "%s", "restaurantId": "rest-42", "items": %s }
                """.formatted(customerId, itemsJson);
    }
}
