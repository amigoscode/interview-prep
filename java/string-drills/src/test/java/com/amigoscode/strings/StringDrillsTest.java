package com.amigoscode.strings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static com.amigoscode.strings.StringDrills.couldBeEqual;
import static com.amigoscode.strings.StringDrills.countWords;
import static com.amigoscode.strings.StringDrills.findAndReplacePattern;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StringDrillsTest {

    @Test
    void projectIsWiredUp() {
        assertThat(StringDrills.class).isNotNull();
    }

    @Nested
    class Story1CountWords {

        @Test
        void countsWordsSeparatedBySingleSpaces() {
            assertThat(countWords("the quick brown fox")).isEqualTo(4);
        }

        @Test
        void singleWord() {
            assertThat(countWords("hello")).isEqualTo(1);
            assertThat(countWords("a")).isEqualTo(1);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "     ", "\t", "\n", " \t\r\n "})
        void nullEmptyAndBlankHaveNoWords(String text) {
            assertThat(countWords(text)).isZero();
        }

        @Test
        void multipleSpacesBetweenWordsCountOnce() {
            assertThat(countWords("a   b")).isEqualTo(2);
            assertThat(countWords("one  two     three")).isEqualTo(3);
        }

        @Test
        void leadingAndTrailingWhitespaceIsIgnored() {
            assertThat(countWords("   padded words   ")).isEqualTo(2);
            assertThat(countWords("\n\tpadded\n")).isEqualTo(1);
        }

        @Test
        void tabsAndNewlinesSeparateWords() {
            assertThat(countWords("one\ttwo\nthree\r\nfour")).isEqualTo(4);
            assertThat(countWords("line one\n\nline two")).isEqualTo(4);
        }

        @Test
        void punctuationAttachedToAWordDoesNotSplitIt() {
            assertThat(countWords("Hello, world!")).isEqualTo(2);
            assertThat(countWords("don't stop")).isEqualTo(2);
        }

        @Test
        void standalonePunctuationIsNotAWord() {
            assertThat(countWords("a - b")).isEqualTo(2);
            assertThat(countWords("... !!! ?")).isZero();
            assertThat(countWords("wait ... what")).isEqualTo(2);
        }

        @Test
        void digitsCountAsWords() {
            assertThat(countWords("route 66")).isEqualTo(2);
        }
    }

    @Nested
    class Story2FindAndReplacePattern {

        @Test
        void reportedExample() {
            List<String> words = List.of("aabbcc", "aakkff", "akkffkk", "tjjiijj", "deftg");
            assertThat(findAndReplacePattern(words, "mnnssnn")).containsExactly("akkffkk", "tjjiijj");
        }

        @Test
        void keepsInputOrder() {
            List<String> words = List.of("zyy", "abb", "mee", "xyz");
            assertThat(findAndReplacePattern(words, "abb")).containsExactly("zyy", "abb", "mee");
        }

        @Test
        void differentLengthsNeverMatch() {
            assertThat(findAndReplacePattern(List.of("ab", "abcd", "abc"), "xyz")).containsExactly("abc");
        }

        @Test
        void twoPatternLettersMayNotMapToTheSameWordLetter() {
            assertThat(findAndReplacePattern(List.of("xyy", "xxx"), "abb")).containsExactly("xyy");
            assertThat(findAndReplacePattern(List.of("xxx"), "abc")).isEmpty();
        }

        @Test
        void oneLetterMayNotMapToTwo() {
            assertThat(findAndReplacePattern(List.of("abc", "aaa"), "xxx")).containsExactly("aaa");
        }

        @Test
        void aWordMatchesItself() {
            assertThat(findAndReplacePattern(List.of("abcabc"), "abcabc")).containsExactly("abcabc");
        }

        @Test
        void emptyInputs() {
            assertThat(findAndReplacePattern(List.of(), "abb")).isEmpty();
            assertThat(findAndReplacePattern(List.of("", "a"), "")).containsExactly("");
        }

        @Test
        void duplicatesAreKept() {
            assertThat(findAndReplacePattern(List.of("mee", "mee"), "abb")).containsExactly("mee", "mee");
        }

        @Test
        void rejectsNullArguments() {
            assertThatThrownBy(() -> findAndReplacePattern(null, "a")).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> findAndReplacePattern(List.of("a"), null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        void resultIsNotAViewOfTheInput() {
            List<String> words = new ArrayList<>(List.of("abb"));
            List<String> result = findAndReplacePattern(words, "xyy");
            words.clear();
            assertThat(result).containsExactly("abb");
        }
    }

    @Nested
    class Story3OcrCompare {

        @ParameterizedTest(name = "{0} could equal {1}")
        @CsvSource({
                "A3BCD, A6",
                "A3BCD, A3BC1",
                "A6,    A3BC1",
        })
        void reportedExamplesCanAllBeEqual(String first, String second) {
            assertThat(couldBeEqual(first, second)).isTrue();
            assertThat(couldBeEqual(second, first)).isTrue();
        }

        @Test
        void identicalPlainStrings() {
            assertThat(couldBeEqual("ABC", "ABC")).isTrue();
            assertThat(couldBeEqual("", "")).isTrue();
        }

        @Test
        void differentKnownLettersAtTheSamePosition() {
            assertThat(couldBeEqual("ABC", "ABD")).isFalse();
            assertThat(couldBeEqual("A1C", "ABD")).isFalse();
        }

        @Test
        void comparisonIsCaseSensitive() {
            assertThat(couldBeEqual("abc", "ABC")).isFalse();
        }

        @Test
        void unknownsMatchLetters() {
            assertThat(couldBeEqual("A2D", "ABCD")).isTrue();
            assertThat(couldBeEqual("4", "ABCD")).isTrue();
            assertThat(couldBeEqual("1B1", "ABC")).isTrue();
        }

        @Test
        void unknownsOnBothSidesMatchEachOther() {
            assertThat(couldBeEqual("2", "2")).isTrue();
            assertThat(couldBeEqual("A1", "1B")).isTrue();   // A? vs ?B
            assertThat(couldBeEqual("1X2", "3Y")).isTrue();  // ?X?? vs ???Y
            assertThat(couldBeEqual("2B3", "4E1")).isTrue(); // ??B??? vs ????E?
        }

        @Test
        void knownLettersStillClashAroundUnknowns() {
            assertThat(couldBeEqual("1BC", "2D")).isFalse(); // ?BC vs ??D: C vs D
            assertThat(couldBeEqual("2B", "1AC")).isFalse(); // ??B vs ?AC: B vs C
        }

        @Test
        void multiDigitCounts() {
            assertThat(couldBeEqual("a12", "a" + "x".repeat(12))).isTrue();
            assertThat(couldBeEqual("a12", "a" + "x".repeat(11))).isFalse();
            assertThat(couldBeEqual("a12", "a1b")).isFalse();  // 12 unknowns, not 1 then 2
            assertThat(couldBeEqual("10b", "5xxxxxb")).isTrue();
        }

        @Test
        void aZeroCountMeansNoUnknowns() {
            assertThat(couldBeEqual("A0B", "AB")).isTrue();
            assertThat(couldBeEqual("A0", "A")).isTrue();
        }

        @Test
        void lengthMismatchIsNeverEqual() {
            assertThat(couldBeEqual("A2", "ABCD")).isFalse();
            assertThat(couldBeEqual("ABCD", "A2")).isFalse();
            assertThat(couldBeEqual("A", "")).isFalse();
            assertThat(couldBeEqual("3", "2")).isFalse();
            assertThat(couldBeEqual("A3BCD", "A3BC")).isFalse();
        }

        @Test
        @Timeout(1)
        void hugeCountsAreNeverExpanded() {
            assertThat(couldBeEqual("x999999999", "x999999998y")).isTrue();
            assertThat(couldBeEqual("x1000000000", "x999999999")).isFalse();
        }

        @Test
        void rejectsNullArguments() {
            assertThatThrownBy(() -> couldBeEqual(null, "A")).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> couldBeEqual("A", null)).isInstanceOf(NullPointerException.class);
        }

        /**
         * Property-based check against an oracle: for small random inputs, expand both strings with a
         * wildcard marker and compare position by position. The oracle is too slow for big counts,
         * which is exactly why the real solution does not do it.
         */
        @Test
        void agreesWithTheExpandingOracleOnRandomSmallInputs() {
            Random random = new Random(42);
            int agreedTrue = 0;
            for (int i = 0; i < 20_000; i++) {
                String first = randomDamaged(random);
                String second = randomDamaged(random);
                boolean expected = oracle(first, second);
                assertThat(couldBeEqual(first, second))
                        .as("couldBeEqual(\"%s\", \"%s\")", first, second)
                        .isEqualTo(expected);
                if (expected) agreedTrue++;
            }
            assertThat(agreedTrue).as("the generator must also produce matching pairs").isGreaterThan(500);
        }

        private static String randomDamaged(Random random) {
            StringBuilder sb = new StringBuilder();
            int parts = random.nextInt(5);
            for (int p = 0; p < parts; p++) {
                if (random.nextInt(3) == 0) {
                    sb.append(random.nextInt(4));      // a count, 0..3 (two in a row read as one count)
                } else {
                    sb.append("ab".charAt(random.nextInt(2)));
                }
            }
            return sb.toString();
        }

        private static final char UNKNOWN = '\0';

        private static boolean oracle(String first, String second) {
            char[] a = expand(first);
            char[] b = expand(second);
            if (a.length != b.length) return false;
            for (int i = 0; i < a.length; i++) {
                if (a[i] != UNKNOWN && b[i] != UNKNOWN && a[i] != b[i]) return false;
            }
            return true;
        }

        private static char[] expand(String s) {
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < s.length(); ) {
                if (Character.isDigit(s.charAt(i))) {
                    int start = i;
                    while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
                    char[] unknowns = new char[Integer.parseInt(s.substring(start, i))];
                    Arrays.fill(unknowns, UNKNOWN);
                    out.append(unknowns);
                } else {
                    out.append(s.charAt(i++));
                }
            }
            return out.toString().toCharArray();
        }
    }
}
