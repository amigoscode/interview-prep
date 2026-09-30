package com.amigoscode.fixit;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * An amount of euros held as a whole number of cents, so sums are exact. Never use double for money:
 * 0.1 + 0.2 is not 0.3, and {@code Math.floor(2.9665 * 100)} silently drops a cent.
 */
public record Money(long cents) implements Comparable<Money> {

    public static final Money ZERO = new Money(0);

    /** Parses a decimal amount such as {@code "2.49"}. More than two decimal places is rejected. */
    public static Money euros(String amount) {
        BigDecimal value = new BigDecimal(amount);
        if (value.scale() > 2) {
            throw new IllegalArgumentException("More than two decimal places: " + amount);
        }
        return new Money(value.movePointRight(2).longValueExact());
    }

    public Money plus(Money other) {
        return new Money(Math.addExact(cents, other.cents));
    }

    public Money times(long factor) {
        return new Money(Math.multiplyExact(cents, factor));
    }

    /** Subtracts, but never goes below zero. A discount cannot turn a fee into a refund. */
    public Money minusFloorAtZero(Money other) {
        return new Money(Math.max(0, cents - other.cents));
    }

    /** Takes {@code percent}% off, rounding the result half up to the cent. */
    public Money percentOff(int percent) {
        if (percent < 0 || percent > 100) {
            throw new IllegalArgumentException("Percent must be 0..100, was " + percent);
        }
        long discounted = BigDecimal.valueOf(cents)
                .multiply(BigDecimal.valueOf(100 - percent))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                .longValueExact();
        return new Money(discounted);
    }

    public boolean isLessThan(Money other) {
        return compareTo(other) < 0;
    }

    @Override
    public int compareTo(Money other) {
        return Long.compare(cents, other.cents);
    }

    @Override
    public String toString() {
        return BigDecimal.valueOf(cents, 2).toPlainString();
    }
}
