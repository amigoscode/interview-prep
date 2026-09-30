package com.amigoscode.orders;

import java.time.Instant;

/** The order header every event carries. Stories 3 and 4 need it. */
public record OrderDetails(String restaurantId, String zoneId, Instant placedAt, Instant promisedBy) {}
