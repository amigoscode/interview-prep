package com.amigoscode.fixit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Converts map keys from snake_case to camelCase, recursively.
 *
 * <ul>
 *   <li>Underscores split words; empty words (leading, trailing or double underscores) are dropped.</li>
 *   <li>The first word is kept as written; later words get an upper-case first letter. So a key that
 *       is already camelCase is unchanged.</li>
 *   <li>Nested maps are converted; so are maps inside lists. Every other value is kept as is.</li>
 *   <li>Two keys that convert to the same name ({@code order_id} and {@code orderId}) are an error:
 *       silently dropping one would lose data.</li>
 *   <li>The input is never modified. The result is an unmodifiable copy that keeps key order.</li>
 * </ul>
 */
public final class KeyCaseConverter {

    private KeyCaseConverter() {
    }

    public static Map<String, Object> toCamelCase(Map<String, Object> input) {
        Objects.requireNonNull(input, "input");
        return convertMap(input);
    }

    static String toCamelCase(String key) {
        Objects.requireNonNull(key, "Map keys cannot be null");
        StringBuilder camel = new StringBuilder(key.length());
        for (String word : key.split("_")) {
            if (word.isEmpty()) {
                continue;
            }
            if (camel.isEmpty()) {
                camel.append(word);
            } else {
                camel.append(Character.toUpperCase(word.charAt(0))).append(word, 1, word.length());
            }
        }
        if (camel.isEmpty()) {
            throw new IllegalArgumentException("Key has no letters to keep: '" + key + "'");
        }
        return camel.toString();
    }

    private static Map<String, Object> convertMap(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, String> originalKeys = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new IllegalArgumentException("Keys must be Strings, found: " + entry.getKey());
            }
            String camelKey = toCamelCase(key);
            String clash = originalKeys.putIfAbsent(camelKey, key);
            if (clash != null) {
                throw new IllegalArgumentException(
                        "Keys '" + clash + "' and '" + key + "' both become '" + camelKey + "'");
            }
            result.put(camelKey, convertValue(entry.getValue()));
        }
        return Collections.unmodifiableMap(result);
    }

    private static Object convertValue(Object value) {
        return switch (value) {
            case Map<?, ?> nested -> convertMap(nested);
            case List<?> list -> convertList(list);
            case null, default -> value;
        };
    }

    private static List<Object> convertList(List<?> list) {
        List<Object> converted = new ArrayList<>(list.size());
        for (Object element : list) {
            converted.add(convertValue(element));
        }
        return Collections.unmodifiableList(converted);
    }
}
