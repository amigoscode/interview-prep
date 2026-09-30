package com.amigoscode.arrays;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LargestElementsTest {

    @Test
    void projectIsWiredUp() {
        assertThat(LargestElements.class).isNotNull();
    }

    @Test
    @Disabled("story 1: remove this line to begin")
    void findsTheLargestElement() {
        assertThat(LargestElements.largest(new int[] {3, 9, -2, 7})).isEqualTo(9);
    }
}
