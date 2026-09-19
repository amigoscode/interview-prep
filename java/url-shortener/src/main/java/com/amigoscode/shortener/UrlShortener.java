package com.amigoscode.shortener;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Two maps, one atomic operation.
 *
 * <p>{@code codeByUrl.computeIfAbsent(url, ...)} is the whole concurrency story: twenty threads
 * shortening the same URL enter it together, exactly one runs the mapping function, and the
 * rest receive its result. A {@code get} followed by a {@code put} would let all twenty
 * create a code. The reverse map is written inside the same function so it can never lag.
 */
public final class UrlShortener {

    private final CodeGenerator generator;
    private final Map<String, String> codeByUrl = new ConcurrentHashMap<>();
    private final Map<String, String> urlByCode = new ConcurrentHashMap<>();

    public UrlShortener() {
        this(new Base62CounterGenerator());
    }

    public UrlShortener(CodeGenerator generator) {
        this.generator = Objects.requireNonNull(generator, "generator");
    }

    public String shorten(String longUrl) {
        String url = normalise(longUrl);
        return codeByUrl.computeIfAbsent(url, u -> {
            while (true) {
                String code = generator.next();
                if (urlByCode.putIfAbsent(code, u) == null) { // claim the code atomically
                    return code;
                }
                // a random generator collided; try again
            }
        });
    }

    public Optional<String> expand(String code) {
        if (code == null || code.isBlank()) return Optional.empty();
        return Optional.ofNullable(urlByCode.get(code));
    }

    /** Valid means: absolute http(s) URL with a host. Agreed with the interviewer, written down. */
    private static String normalise(String longUrl) {
        if (longUrl == null || longUrl.isBlank()) {
            throw new IllegalArgumentException("url must not be blank");
        }
        String trimmed = longUrl.trim();
        try {
            URI uri = new URI(trimmed);
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equals("http") || scheme.equals("https")) || uri.getHost() == null) {
                throw new IllegalArgumentException("not an absolute http(s) url: " + longUrl);
            }
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("not a valid url: " + longUrl, e);
        }
        return trimmed;
    }
}
