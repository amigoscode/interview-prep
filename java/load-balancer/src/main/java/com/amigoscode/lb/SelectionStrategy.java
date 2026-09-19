package com.amigoscode.lb;

import java.util.List;

/** Story 4. Given the current addresses (never empty), pick one. */
public interface SelectionStrategy {
    String select(List<String> addresses);
}
