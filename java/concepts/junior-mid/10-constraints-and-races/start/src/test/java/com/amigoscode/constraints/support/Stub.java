package com.amigoscode.constraints.support;

import java.lang.reflect.Proxy;
import java.util.function.Function;

/**
 * A hand-written stand-in for a Spring Data repository, for unit tests without Spring or a
 * database. Every method that returns a boolean ("does it exist?") answers false, and save does
 * whatever the test says. Adding a method to the repository does not break the stub.
 */
public final class Stub {

    @SuppressWarnings("unchecked")
    public static <T> T repository(Class<T> type, Function<Object, Object> save) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (proxy, method, args) -> {
            if (method.getName().equals("save")) {
                return save.apply(args[0]);
            }
            if (method.getReturnType() == boolean.class) {
                return false;
            }
            if (method.getName().equals("toString")) {
                return "stub of " + type.getSimpleName();
            }
            throw new UnsupportedOperationException("The stub does not know " + method.getName());
        });
    }

    private Stub() {
    }
}
