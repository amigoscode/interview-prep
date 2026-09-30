package com.amigoscode.fixit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void parsesAndPrintsEuros() {
        assertThat(Money.euros("2.49").cents()).isEqualTo(249);
        assertThat(Money.euros("30").cents()).isEqualTo(3000);
        assertThat(Money.euros("2.49")).hasToString("2.49");
        assertThat(Money.ZERO).hasToString("0.00");
    }

    @Test
    void rejectsFractionsOfACent() {
        assertThatThrownBy(() -> Money.euros("2.495")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sumsAreExactWhereDoublesAreNot() {
        assertThat(0.1 + 0.2).isNotEqualTo(0.3);
        assertThat(Money.euros("0.10").plus(Money.euros("0.20"))).isEqualTo(Money.euros("0.30"));
    }

    @Test
    void percentOffRoundsHalfUp() {
        assertThat(Money.euros("3.49").percentOff(15)).isEqualTo(Money.euros("2.97")); // 2.9665
        assertThat(Money.euros("0.10").percentOff(15)).isEqualTo(Money.euros("0.09")); // 0.085
        assertThat(Money.euros("0.10").percentOff(100)).isEqualTo(Money.ZERO);
        assertThatThrownBy(() -> Money.euros("1.00").percentOff(101)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void subtractionStopsAtZero() {
        assertThat(Money.euros("2.49").minusFloorAtZero(Money.euros("1.00"))).isEqualTo(Money.euros("1.49"));
        assertThat(Money.euros("0.40").minusFloorAtZero(Money.euros("1.00"))).isEqualTo(Money.ZERO);
    }

    @Test
    void timesAndCompare() {
        assertThat(Money.euros("0.50").times(12)).isEqualTo(Money.euros("6.00"));
        assertThat(Money.euros("9.99").isLessThan(Money.euros("10.00"))).isTrue();
        assertThat(Money.euros("10.00").isLessThan(Money.euros("10.00"))).isFalse();
    }
}
