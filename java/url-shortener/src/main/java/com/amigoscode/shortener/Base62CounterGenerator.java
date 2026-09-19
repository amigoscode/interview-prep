package com.amigoscode.shortener;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Sequential ids encoded in base62: 62^7 is about 3.5 trillion codes in 7 characters. Unique
 * by construction on one machine; across servers you would hand out id ranges or use a
 * database sequence, which is the follow-up question.
 */
public final class Base62CounterGenerator implements CodeGenerator {

    private static final String ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private final AtomicLong counter;

    public Base62CounterGenerator() {
        this(1);
    }

    public Base62CounterGenerator(long start) {
        this.counter = new AtomicLong(start);
    }

    @Override
    public String next() {
        return encode(counter.getAndIncrement());
    }

    static String encode(long value) {
        if (value == 0) return "0";
        StringBuilder sb = new StringBuilder();
        while (value > 0) {
            sb.append(ALPHABET.charAt((int) (value % 62)));
            value /= 62;
        }
        return sb.reverse().toString();
    }
}
