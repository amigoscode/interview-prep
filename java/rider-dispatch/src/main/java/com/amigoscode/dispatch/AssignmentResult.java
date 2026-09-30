package com.amigoscode.dispatch;

/** The outcome of {@link Dispatcher#assign(Order)}. "Nobody free" is a normal answer, not an exception. */
public sealed interface AssignmentResult {

    Order order();

    record Assigned(Order order, Rider rider) implements AssignmentResult {
    }

    record NoRiderAvailable(Order order) implements AssignmentResult {
    }
}
