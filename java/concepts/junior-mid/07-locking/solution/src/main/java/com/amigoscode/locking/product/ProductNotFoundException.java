package com.amigoscode.locking.product;

public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(long productId) {
        super("Product " + productId + " does not exist");
    }
}
