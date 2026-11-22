package com.amigoscode.threadsafety.scope;

import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * EXHIBIT for ./inspect. A prototype bean: Spring creates a new one every time somebody ASKS the
 * container for it. A singleton asks once, when it is built, and keeps that one for ever.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class PrototypeHelper {

    private static final AtomicInteger CREATED = new AtomicInteger();

    private final int number = CREATED.incrementAndGet();

    public String name() {
        return "PrototypeHelper #" + number;
    }
}
