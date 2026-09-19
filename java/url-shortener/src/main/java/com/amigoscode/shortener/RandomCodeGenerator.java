package com.amigoscode.shortener;

import java.security.SecureRandom;
import java.util.Random;

/** Random 8-character codes. Collisions are possible in theory; the shortener retries. */
public final class RandomCodeGenerator implements CodeGenerator {

    private static final String ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private final Random random;
    private final int length;

    public RandomCodeGenerator() {
        this(new SecureRandom(), 8);
    }

    public RandomCodeGenerator(Random random, int length) {
        this.random = random;
        this.length = length;
    }

    @Override
    public String next() {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        return sb.toString();
    }
}
