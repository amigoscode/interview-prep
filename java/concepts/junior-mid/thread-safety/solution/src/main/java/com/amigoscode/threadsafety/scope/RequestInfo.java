package com.amigoscode.threadsafety.scope;

import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/**
 * EXHIBIT for ./inspect. A request-scoped bean: Spring creates a new one for every HTTP request,
 * so its fields belong to one request only. Compare with the singletons everywhere else.
 */
@Component
@RequestScope
public class RequestInfo {

    private static final AtomicInteger CREATED = new AtomicInteger();

    private final int number = CREATED.incrementAndGet();

    public String name() {
        return "RequestInfo #" + number;
    }
}
