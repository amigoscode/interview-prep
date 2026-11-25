package com.amigoscode.constraints.settings;

/** One row of user_settings. {@code created} is true when this call inserted the row. */
public record Settings(long userId, String theme, boolean created) {
}
