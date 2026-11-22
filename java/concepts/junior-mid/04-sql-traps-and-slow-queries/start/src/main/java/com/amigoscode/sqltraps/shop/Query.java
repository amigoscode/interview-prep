package com.amigoscode.sqltraps.shop;

import java.util.List;

/** One SQL statement and the values for its ? placeholders. */
public record Query(String sql, List<Object> args) {

    public Query(String sql, Object... args) {
        this(sql.strip(), List.of(args));
    }
}
