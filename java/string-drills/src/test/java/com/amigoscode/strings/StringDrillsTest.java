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
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static com.amigoscode.strings.StringDrills.couldBeEqual;
import static com.amigoscode.strings.StringDrills.countWords;
import static com.amigoscode.strings.StringDrills.findAndReplacePattern;
import static com.amigoscode.strings.StringDrills.isPalindrome;
import static com.amigoscode.strings.StringDrills.isPalindromeWithStreams;
import static com.amigoscode.strings.StringDrills.isValidAbbreviation;
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

    @Nested
    class Story4ValidWordAbbreviation {

        private static final String WORD = "substitution";

        @ParameterizedTest(name = "\"{0}\" abbreviates substitution")
        @ValueSource(strings = {"s10n", "su3i1u2on", "12", "substitution", "sub4u3n", "1ubstitutio1"})
        void reportedAndOtherValidAbbreviations(String abbr) {
            assertThat(isValidAbbreviation(WORD, abbr)).isTrue();
        }

        @Test
        void leadingZerosAreInvalid() {
            assertThat(isValidAbbreviation(WORD, "s010n")).isFalse();
            assertThat(isValidAbbreviation(WORD, "012")).isFalse();
        }

        @Test
        void aZeroLengthSkipIsInvalid() {
            assertThat(isValidAbbreviation(WORD, "s0ubstitution")).isFalse();
            assertThat(isValidAbbreviation(WORD, "substitution0")).isFalse();
            assertThat(isValidAbbreviation("", "0")).isFalse();
        }

        @Test
        void aCountThatOverrunsTheWordIsInvalid() {
            assertThat(isValidAbbreviation(WORD, "13")).isFalse();
            assertThat(isValidAbbreviation(WORD, "s11n")).isFalse();
            assertThat(isValidAbbreviation(WORD, "s11")).isTrue();
        }

        @Test
        void anAbbreviationThatStopsShortIsInvalid() {
            assertThat(isValidAbbreviation(WORD, "11")).isFalse();
            assertThat(isValidAbbreviation(WORD, "s9n")).isFalse();
            assertThat(isValidAbbreviation(WORD, "")).isFalse();
        }

        @Test
        void lettersMustMatchExactly() {
            assertThat(isValidAbbreviation(WORD, "s10m")).isFalse();
            assertThat(isValidAbbreviation(WORD, "S10n")).isFalse();
            assertThat(isValidAbbreviation(WORD, "substitutionx")).isFalse();
        }

        @Test
        void adjacentDigitsAreOneCount() {
            // "s55n" reads as s, 55, n, not s, 5, 5, n
            assertThat(isValidAbbreviation(WORD, "s55n")).isFalse();
            assertThat(isValidAbbreviation("a" + "x".repeat(55) + "b", "a55b")).isTrue();
        }

        @Test
        void emptyWord() {
            assertThat(isValidAbbreviation("", "")).isTrue();
            assertThat(isValidAbbreviation("", "1")).isFalse();
            assertThat(isValidAbbreviation("", "a")).isFalse();
        }

        @Test
        @Timeout(1)
        void hugeCountsDoNotOverflowOrLoop() {
            assertThat(isValidAbbreviation(WORD, "99999999999999999999")).isFalse();
            assertThat(isValidAbbreviation(WORD, "s4294967306n")).isFalse(); // 2^32 + 10 would wrap an int to 10
        }

        @Test
        void rejectsNullArguments() {
            assertThatThrownBy(() -> isValidAbbreviation(null, "a")).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> isValidAbbreviation("a", null)).isInstanceOf(NullPointerException.class);
        }

        /**
         * Oracle: every valid abbreviation of a short word is found by choosing, for each maximal
         * run of characters, whether to keep it or replace it with its length. Random candidate
         * strings must be accepted exactly when they are in that set.
         */
        @Test
        void agreesWithTheEnumeratingOracleOnRandomSmallInputs() {
            Random random = new Random(408);
            int agreedTrue = 0;
            for (int i = 0; i < 2_000; i++) {
                String word = randomWord(random, random.nextInt(7));
                Set<String> valid = allAbbreviations(word);
                for (String abbr : valid) {
                    assertThat(isValidAbbreviation(word, abbr)).as("\"%s\" abbreviates \"%s\"", abbr, word).isTrue();
                }
                for (int j = 0; j < 20; j++) {
                    String abbr = randomAbbreviationLike(random);
                    boolean expected = valid.contains(abbr);
                    assertThat(isValidAbbreviation(word, abbr))
                            .as("isValidAbbreviation(\"%s\", \"%s\")", word, abbr)
                            .isEqualTo(expected);
                    if (expected) agreedTrue++;
                }
            }
            assertThat(agreedTrue).as("the generator must also hit valid abbreviations").isGreaterThan(200);
        }

        /** Each character is either kept or hidden; each maximal hidden run becomes its length. */
        private static Set<String> allAbbreviations(String word) {
            Set<String> result = new HashSet<>();
            int n = word.length();
            for (int mask = 0; mask < (1 << n); mask++) {
                StringBuilder sb = new StringBuilder();
                int hidden = 0;
                for (int k = 0; k < n; k++) {
                    if ((mask & (1 << k)) != 0) {
                        hidden++;
                    } else {
                        if (hidden > 0) sb.append(hidden);
                        hidden = 0;
                        sb.append(word.charAt(k));
                    }
                }
                if (hidden > 0) sb.append(hidden);
                result.add(sb.toString());
            }
            return result;
        }

        private static String randomWord(Random random, int length) {
            StringBuilder sb = new StringBuilder();
            for (int k = 0; k < length; k++) sb.append("ab".charAt(random.nextInt(2)));
            return sb.toString();
        }

        private static String randomAbbreviationLike(Random random) {
            StringBuilder sb = new StringBuilder();
            int length = random.nextInt(6);
            for (int k = 0; k < length; k++) {
                sb.append(random.nextInt(3) == 0 ? (char) ('0' + random.nextInt(5)) : "ab".charAt(random.nextInt(2)));
            }
            return sb.toString();
        }
    }

    @Nested
    class Story5Palindrome {

        @ParameterizedTest(name = "\"{0}\" is a palindrome")
        @ValueSource(strings = {
                "racecar", "A man, a plan, a canal: Panama", "No 'x' in Nixon", "Was it a car or a cat I saw?",
                "a", "aa", "abba", "12321", "1a2A1", "", " ", ".,!?", "Aa"
        })
        void palindromes(String text) {
            assertThat(isPalindrome(text)).isTrue();
            assertThat(isPalindromeWithStreams(text)).isTrue();
        }

        @ParameterizedTest(name = "\"{0}\" is not a palindrome")
        @ValueSource(strings = {"ab", "race a car", "abca", "0P", "12 3 1", "hello, olleh!x"})
        void notPalindromes(String text) {
            assertThat(isPalindrome(text)).isFalse();
            assertThat(isPalindromeWithStreams(text)).isFalse();
        }

        @Test
        void punctuationOnOneSideOnlyIsSkipped() {
            assertThat(isPalindrome("!!!abc,,cba")).isTrue();
            assertThat(isPalindrome("ab...A")).isTrue();
        }

        @Test
        void rejectsNull() {
            assertThatThrownBy(() -> isPalindrome(null)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> isPalindromeWithStreams(null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        @Timeout(2)
        void longInput() {
            String half = "Ab1, ".repeat(200_000);
            String text = half + new StringBuilder(half).reverse();
            assertThat(isPalindrome(text)).isTrue();
            assertThat(isPalindromeWithStreams(text)).isTrue();
            assertThat(isPalindrome(text + "x")).isFalse();
        }

        /** Oracle: clean the text the slow, obvious way and compare it with its reverse. */
        @Test
        void bothVersionsAgreeWithTheReversingOracleOnRandomInputs() {
            Random random = new Random(125);
            int palindromes = 0;
            for (int i = 0; i < 20_000; i++) {
                String text = randomText(random);
                String cleaned = text.replaceAll("[^A-Za-z0-9]", "").toLowerCase();
                boolean expected = cleaned.equals(new StringBuilder(cleaned).reverse().toString());
                assertThat(isPalindrome(text)).as("isPalindrome(\"%s\")", text).isEqualTo(expected);
                assertThat(isPalindromeWithStreams(text)).as("isPalindromeWithStreams(\"%s\")", text).isEqualTo(expected);
                if (expected) palindromes++;
            }
            assertThat(palindromes).as("the generator must also produce palindromes").isGreaterThan(1_000);
        }

        private static String randomText(Random random) {
            String alphabet = "aAb1 ,!";
            StringBuilder sb = new StringBuilder();
            int length = random.nextInt(7);
            for (int k = 0; k < length; k++) sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
            return sb.toString();
        }
    }
}
