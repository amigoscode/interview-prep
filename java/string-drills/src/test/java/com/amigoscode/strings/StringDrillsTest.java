package com.amigoscode.strings;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StringDrillsTest {

    @Test
    void projectIsWiredUp() {
        assertThat(StringDrills.class).isNotNull();
    }

    @Test
    @Disabled("story 1: remove this line to begin")
    void countsWordsSeparatedBySingleSpaces() {
        assertThat(StringDrills.countWords("the quick brown fox")).isEqualTo(4);
    }
}
