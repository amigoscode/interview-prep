package com.amigoscode.strings;

import java.util.List;

/** Story 1 starts here. One static method per story. */
public final class StringDrills {

    private StringDrills() {
    }

    /** Story 1: how many words are in {@code text}. */
    public static int countWords(String text) {
        throw new UnsupportedOperationException("story 1");
    }

    /** Story 2: the words that match {@code pattern} letter for letter, in input order. */
    public static List<String> findAndReplacePattern(List<String> words, String pattern) {
        throw new UnsupportedOperationException("story 2");
    }

    /** Story 3: could two OCR-damaged strings ("A3BCD" = A, 3 unknowns, BCD) be the same? */
    public static boolean couldBeEqual(String first, String second) {
        throw new UnsupportedOperationException("story 3");
    }

    /** Story 4: is {@code abbr} a valid abbreviation of {@code word}? ("s10n" abbreviates "substitution") */
    public static boolean isValidAbbreviation(String word, String abbr) {
        throw new UnsupportedOperationException("story 4");
    }

    /** Story 5: is {@code text} a palindrome, ignoring case and anything not a letter or digit? */
    public static boolean isPalindrome(String text) {
        throw new UnsupportedOperationException("story 5");
    }

    /** Story 5 follow-up: the same check, rewritten with streams and lambdas. */
    public static boolean isPalindromeWithStreams(String text) {
        throw new UnsupportedOperationException("story 5");
    }
}
