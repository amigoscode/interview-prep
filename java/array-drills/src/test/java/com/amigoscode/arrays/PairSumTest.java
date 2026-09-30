package com.amigoscode.arrays;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.amigoscode.arrays.PairSum.countPairs;
import static com.amigoscode.arrays.PairSum.pairsWithSum;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PairSumTest {

    private static final int[] REPORTED = {1, 1, 2, 23, 4, 9, 13, 6, 9};

    private static IndexPair p(int i, int j) {
        return new IndexPair(i, j);
    }

    @Nested
    class Pairs {

        @Test
        void reportedExampleReturnsEveryPairNotJustTheTwoInTheReport() {
            // The report said [[0,8],[1,5]]. Under our spec (every pair) the full answer is:
            assertThat(pairsWithSum(REPORTED, 10))
                    .containsExactly(p(0, 5), p(0, 8), p(1, 5), p(1, 8), p(4, 7));
        }

        @Test
        void resultIsSortedByIThenJ() {
            assertThat(pairsWithSum(new int[] {6, 4, 4, 6, 5, 5}, 10))
                    .containsExactly(p(0, 1), p(0, 2), p(1, 3), p(2, 3), p(4, 5));
        }

        @Test
        void equalValuesPairWithEachOtherButNeverWithThemselves() {
            assertThat(pairsWithSum(new int[] {5}, 10)).isEmpty();
            assertThat(pairsWithSum(new int[] {5, 5}, 10)).containsExactly(p(0, 1));
            assertThat(pairsWithSum(new int[] {3, 3, 3}, 6)).containsExactly(p(0, 1), p(0, 2), p(1, 2));
        }

        @Test
        void noPairsAndEmptyInputGiveAnEmptyList() {
            assertThat(pairsWithSum(new int[] {1, 2, 3}, 100)).isEmpty();
            assertThat(pairsWithSum(new int[0], 0)).isEmpty();
        }

        @Test
        void handlesNegativesAndZero() {
            assertThat(pairsWithSum(new int[] {-3, 0, 3, 0}, 0)).containsExactly(p(0, 2), p(1, 3));
        }

        @Test
        void complementDoesNotOverflowIntoAFalseMatch() {
            // In int arithmetic, MIN_VALUE - MAX_VALUE wraps round to 1, which is in the array.
            assertThat(pairsWithSum(new int[] {Integer.MAX_VALUE, 1}, Integer.MIN_VALUE)).isEmpty();
            assertThat(pairsWithSum(new int[] {Integer.MAX_VALUE, Integer.MIN_VALUE}, -1)).containsExactly(p(0, 1));
        }

        @Test
        void everyIndexPairIsCorrect() {
            int[] nums = {2, 7, 11, 15, -4, 13, 7, 2};
            assertThat(pairsWithSum(nums, 9))
                    .allSatisfy(pair -> {
                        assertThat(pair.i()).isLessThan(pair.j());
                        assertThat(nums[pair.i()] + nums[pair.j()]).isEqualTo(9);
                    })
                    .containsExactly(p(0, 1), p(0, 6), p(1, 7), p(4, 5), p(6, 7));
        }

        @Test
        void resultIsUnmodifiable() {
            assertThatThrownBy(() -> pairsWithSum(REPORTED, 10).clear())
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void rejectsNull() {
            assertThatThrownBy(() -> pairsWithSum(null, 10)).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class StretchCount {

        @Test
        void countsWithoutBuildingThePairs() {
            assertThat(countPairs(REPORTED, 10)).isEqualTo(5);
            assertThat(countPairs(new int[] {3, 3, 3}, 6)).isEqualTo(3);
            assertThat(countPairs(new int[0], 6)).isZero();
        }

        @Test
        void agreesWithTheListVersion() {
            int[] nums = {2, 7, 11, 15, -4, 13, 7, 2, 0, 9, 9};
            assertThat(countPairs(nums, 9)).isEqualTo(pairsWithSum(nums, 9).size());
        }

        @Test
        void countIsALongBecauseEqualElementsGiveNSquaredOverTwoPairs() {
            int[] zeros = new int[100_000];
            assertThat(countPairs(zeros, 0)).isEqualTo(100_000L * 99_999 / 2);
        }

        @Test
        void complementDoesNotOverflow() {
            assertThat(countPairs(new int[] {Integer.MAX_VALUE, 1}, Integer.MIN_VALUE)).isZero();
        }
    }
}
