package com.amigoscode.ds;

/**
 * Task 1: multiplies two ints without the {@code *} operator.
 *
 * <p>Shift-and-add (the "Russian peasant" method): walk the bits of the smaller magnitude, doubling
 * the other operand each step and adding it whenever the current bit is set. That is
 * O(log min(|x|, |y|)) additions instead of the O(n) of a loop of additions.
 *
 * <p>The work happens on {@code long} magnitudes, so {@code Integer.MIN_VALUE} has a positive
 * absolute value and no intermediate step can overflow (|x| and |y| are at most 2^31, so the
 * product is at most 2^62). Overflow of the {@code int} result throws, like
 * {@link Math#multiplyExact(int, int)}.
 */
public final class Multiplier {

    private Multiplier() {
    }

    public static int multiply(int x, int y) {
        long a = Math.abs((long) x);
        long b = Math.abs((long) y);
        if (a < b) {            // iterate over the smaller operand's bits
            long t = a;
            a = b;
            b = t;
        }

        long product = 0;
        while (b != 0) {
            if ((b & 1) == 1) {
                product += a;
            }
            a <<= 1;
            b >>>= 1;
        }

        boolean negative = (x < 0) != (y < 0);
        long signed = negative ? -product : product;
        if (signed < Integer.MIN_VALUE || signed > Integer.MAX_VALUE) {
            throw new ArithmeticException("integer overflow: " + x + " times " + y);
        }
        return (int) signed;
    }
}
