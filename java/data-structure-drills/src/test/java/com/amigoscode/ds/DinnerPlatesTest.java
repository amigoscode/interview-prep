package com.amigoscode.ds;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DinnerPlatesTest {

    @Test
    @Disabled("story 4: remove this line to begin")
    void leetCodeExample() {
        DinnerPlates plates = new DinnerPlates(2);
        for (int v = 1; v <= 5; v++) {
            plates.push(v);                             // [1,2] [3,4] [5]
        }
        assertThat(plates.popAtStack(0)).hasValue(2);   // [1] [3,4] [5]
        plates.push(20);                                // [1,20] [3,4] [5]
        plates.push(21);                                // [1,20] [3,4] [5,21]
        assertThat(plates.popAtStack(0)).hasValue(20);  // [1] [3,4] [5,21]
        assertThat(plates.popAtStack(2)).hasValue(21);  // [1] [3,4] [5]
        assertThat(plates.pop()).hasValue(5);
        assertThat(plates.pop()).hasValue(4);
        assertThat(plates.pop()).hasValue(3);
        assertThat(plates.pop()).hasValue(1);
        assertThat(plates.pop()).isEmpty();
    }
}
