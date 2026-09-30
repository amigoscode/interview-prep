package com.amigoscode.ds;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DinnerPlatesTest {

    @Test
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

    @Test
    void rejectsNonPositiveCapacity() {
        assertThatThrownBy(() -> new DinnerPlates(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Nested
    class Push {

        @Test
        void fillsLeftToRight() {
            DinnerPlates plates = new DinnerPlates(2);
            plates.push(1);
            plates.push(2);
            plates.push(3);
            assertThat(plates.stackCount()).isEqualTo(2);
            assertThat(plates.popAtStack(0)).hasValue(2);
            assertThat(plates.popAtStack(1)).hasValue(3);
        }

        @Test
        void goesToTheLeftmostGapEvenWhenThereAreSeveral() {
            DinnerPlates plates = new DinnerPlates(1);
            for (int v = 0; v < 5; v++) {
                plates.push(v);                         // [0] [1] [2] [3] [4]
            }
            plates.popAtStack(3);
            plates.popAtStack(1);                       // gaps at 1 and 3
            plates.push(100);
            plates.push(200);
            plates.push(300);
            assertThat(plates.popAtStack(1)).hasValue(100);
            assertThat(plates.popAtStack(3)).hasValue(200);
            assertThat(plates.popAtStack(5)).hasValue(300);
        }

        @Test
        void capacityOneMakesOneStackPerPlate() {
            DinnerPlates plates = new DinnerPlates(1);
            plates.push(7);
            plates.push(8);
            assertThat(plates.stackCount()).isEqualTo(2);
        }
    }

    @Nested
    class Pop {

        @Test
        void onEmptyIsEmpty() {
            assertThat(new DinnerPlates(3).pop()).isEmpty();
        }

        @Test
        void skipsEmptiedStacksOnTheRight() {
            DinnerPlates plates = new DinnerPlates(1);
            plates.push(1);
            plates.push(2);
            plates.push(3);
            plates.popAtStack(2);
            plates.popAtStack(1);
            assertThat(plates.stackCount()).isEqualTo(1);
            assertThat(plates.pop()).hasValue(1);
            assertThat(plates.pop()).isEmpty();
        }

        @Test
        void negativeOneIsAnOrdinaryPlate() {
            DinnerPlates plates = new DinnerPlates(2);
            plates.push(-1);
            assertThat(plates.pop()).isEqualTo(OptionalInt.of(-1));
        }

        @Test
        void refillsAfterDrainingCompletely() {
            DinnerPlates plates = new DinnerPlates(2);
            plates.push(1);
            plates.pop();
            plates.push(2);
            plates.push(3);
            plates.push(4);
            assertThat(plates.stackCount()).isEqualTo(2);
            assertThat(plates.pop()).hasValue(4);
        }
    }

    @Nested
    class PopAtStack {

        @Test
        void outOfRangeOrEmptyStackIsEmpty() {
            DinnerPlates plates = new DinnerPlates(2);
            assertThat(plates.popAtStack(0)).isEmpty();
            plates.push(1);
            assertThat(plates.popAtStack(-1)).isEmpty();
            assertThat(plates.popAtStack(1)).isEmpty();
            assertThat(plates.popAtStack(99)).isEmpty();
        }

        @Test
        void anEmptiedMiddleStackReturnsEmptyButKeepsItsNeighbours() {
            DinnerPlates plates = new DinnerPlates(1);
            plates.push(1);
            plates.push(2);
            plates.push(3);
            assertThat(plates.popAtStack(1)).hasValue(2);
            assertThat(plates.popAtStack(1)).isEmpty();
            assertThat(plates.stackCount()).isEqualTo(3);
            assertThat(plates.pop()).hasValue(3);
            assertThat(plates.pop()).hasValue(1);
        }

        @Test
        void popsTheTopOfThatStack() {
            DinnerPlates plates = new DinnerPlates(3);
            plates.push(1);
            plates.push(2);
            plates.push(3);
            assertThat(plates.popAtStack(0)).hasValue(3);
            assertThat(plates.popAtStack(0)).hasValue(2);
        }
    }
}
