package com.amigoscode.fetching.support;

import com.jayway.jsonpath.JsonPath;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/** One HTTP request and the SQL statements it ran. */
public record Measured(String url, int status, String body, List<String> statements) {

    public int statementCount() {
        return statements.size();
    }

    /** Reads a value out of the JSON response, for example json("$.length()") or json("$[0].customerName"). */
    public <T> T json(String path) {
        return JsonPath.read(body, path);
    }

    /** The statements that read from the given table, for example "orders". */
    public List<String> statementsFrom(String table) {
        return statements.stream()
                .filter(sql -> sql.toLowerCase(Locale.ROOT).contains(" from " + table + " "))
                .toList();
    }

    /** "GET /api/orders ran 201 SQL statements: 1 x select ..., 200 x select ..." for failure messages. */
    public String describe() {
        Map<String, Integer> shapes = new LinkedHashMap<>();
        for (String sql : statements) {
            shapes.merge(sql.length() > 110 ? sql.substring(0, 110) + "..." : sql, 1, Integer::sum);
        }
        return "GET " + url + " ran " + statements.size() + " SQL statements:\n"
                + shapes.entrySet().stream()
                        .map(shape -> shape.getValue() + " x " + shape.getKey())
                        .collect(Collectors.joining("\n"));
    }
}
