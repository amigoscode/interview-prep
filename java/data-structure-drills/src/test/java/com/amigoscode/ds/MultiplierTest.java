package com.amigoscode.ds;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.concurrent.ThreadLocalRandom;

import static com.amigoscode.ds.Multiplier.multiply;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MultiplierTest {

    @Test
    void multipliesTwoPositiveNumbers() {
        assertThat(multiply(6, 7)).isEqualTo(42);
    }

    @ParameterizedTest(name = "{0} x {1} = {2}")
    @CsvSource({
            "0, 0, 0",
            "0, 123, 0",
            "-123, 0, 0",
            "1, 99, 99",
            "99, 1, 99",
            "-1, 99, -99",
            "-6, 7, -42",
            "6, -7, -42",
            "-6, -7, 42",
            "13, 11, 143",
            "46340, 46340, 2147395600",
            "65536, 32767, 2147418112",
            "-65536, 32768, -2147483648",
            "2147483647, 1, 2147483647",
            "-2147483648, 1, -2147483648",
            "2147483647, -1, -2147483647",
    })
    void coversSignsZeroAndBoundaries(int x, int y, int expected) {
        assertThat(multiply(x, y)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} x {1} overflows")
    @CsvSource({
            "2147483647, 2",
            "65536, 32768",
            "46341, 46341",
            "-2147483648, -1",
            "-2147483648, 2",
            "-2147483648, -2147483648",
            "2147483647, 2147483647",
    })
    void overflowThrowsLikeMultiplyExact(int x, int y) {
        assertThatThrownBy(() -> multiply(x, y))
                .isInstanceOf(ArithmeticException.class)
                .hasMessageContaining("overflow");
    }

    @Test
    void isCommutative() {
        assertThat(multiply(3, 1_000_000)).isEqualTo(multiply(1_000_000, 3)).isEqualTo(3_000_000);
    }

    @Test
    void agreesWithMathMultiplyExactOnRandomInputs() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < 10_000; i++) {
            int x = random.nextInt();
            int y = random.nextInt(-70_000, 70_000);
            Integer expected;
            try {
                expected = Math.multiplyExact(x, y);
            } catch (ArithmeticException overflow) {
                expected = null;
            }
            if (expected == null) {
                assertThatThrownBy(() -> multiply(x, y)).isInstanceOf(ArithmeticException.class);
            } else {
                assertThat(multiply(x, y)).as("%d x %d", x, y).isEqualTo(expected);
            }
        }
    }
}
