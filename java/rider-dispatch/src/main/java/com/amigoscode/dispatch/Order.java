package com.amigoscode.dispatch;

/** An order waiting for a rider, picked up at the restaurant. */
public record Order(String id, Location restaurant) {
}
