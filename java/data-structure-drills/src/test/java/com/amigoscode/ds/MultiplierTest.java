package com.amigoscode.ds;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static com.amigoscode.ds.Multiplier.multiply;
import static org.assertj.core.api.Assertions.assertThat;

class MultiplierTest {

    @Test
    void projectIsWiredUp() {
        assertThat(Multiplier.class).isNotNull();
    }

    @Test
    @Disabled("story 1: remove this line to begin")
    void multipliesTwoPositiveNumbers() {
        assertThat(multiply(6, 7)).isEqualTo(42);
    }
}
