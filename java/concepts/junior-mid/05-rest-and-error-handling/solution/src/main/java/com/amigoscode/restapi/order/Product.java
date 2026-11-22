package com.amigoscode.restapi.order;

import java.math.BigDecimal;

/** The shop sells three things. The price comes from here, never from the client. */
public enum Product {

    KEYBOARD("50.00"),
    MOUSE("20.00"),
    MONITOR("200.00");

    private final BigDecimal price;

    Product(String price) {
        this.price = new BigDecimal(price);
    }

    public BigDecimal price() {
        return price;
    }
}
