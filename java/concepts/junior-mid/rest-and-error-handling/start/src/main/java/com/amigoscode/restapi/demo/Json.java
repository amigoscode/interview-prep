package com.amigoscode.restapi.demo;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Just enough JSON for the tools: indent a body for printing, and pull one value out of it. */
final class Json {

    private Json() {
    }

    /** Re-indents a JSON document, two spaces per level. Text that is not JSON is returned as it is. */
    static String pretty(String json) {
        String text = json.strip();
        if (!text.startsWith("{") && !text.startsWith("[")) {
            return text;
        }
        StringBuilder out = new StringBuilder();
        int depth = 0;
        boolean inString = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                out.append(c);
                if (c == '\\' && i + 1 < text.length()) {
                    out.append(text.charAt(++i));
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            switch (c) {
                case '"' -> {
                    inString = true;
                    out.append(c);
                }
                case '{', '[' -> {
                    char closing = c == '{' ? '}' : ']';
                    if (i + 1 < text.length() && text.charAt(i + 1) == closing) {
                        out.append(c).append(closing);
                        i++;
                    } else {
                        out.append(c).append('\n').append("  ".repeat(++depth));
                    }
                }
                case '}', ']' -> out.append('\n').append("  ".repeat(--depth)).append(c);
                case ',' -> out.append(c).append('\n').append("  ".repeat(depth));
                case ':' -> out.append(": ");
                case ' ', '\n', '\r', '\t' -> {
                }
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    /** The value of a top-level-ish field as text, or null. Good enough for "status" or "id". */
    static String field(String json, String name) {
        Matcher matcher = Pattern.compile("\"" + name + "\"\\s*:\\s*(\"([^\"]*)\"|[^,}\\]\\s]+)").matcher(json);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(2) != null ? matcher.group(2) : matcher.group(1);
    }

    /** True when an error body shows the client something from inside the server. */
    static boolean leaksInternals(String body) {
        String text = body.toLowerCase();
        return text.contains("exception") || text.contains("java.") || text.contains("com.amigoscode")
                || text.contains("org.springframework") || text.contains("\"trace\"")
                || text.contains("insert into") || text.contains("update orders");
    }
}
