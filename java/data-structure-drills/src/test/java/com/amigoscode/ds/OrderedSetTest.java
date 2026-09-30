package com.amigoscode.ds;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderedSetTest {

    @Test
    @Disabled("story 2: remove this line to begin")
    void popReturnsTheLastInsertedValue() {
        OrderedSet<String> set = new OrderedSet<>();
        set.push("a");
        set.push("b");
        set.push("c");
        assertThat(set.pop()).isEqualTo("c");
        assertThat(set.pop()).isEqualTo("b");
        assertThat(set.orderedValues()).containsExactly("a");
    }
}
