package com.amigoscode.strings;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Three string drills reported from Delivery Hero's live-coding round.
 *
 * <p>Every method walks its input once with indexes and allocates little: that is what the
 * interviewer is looking for, more than any clever library call.
 */
public final class StringDrills {

    private StringDrills() {
    }

    /**
     * Story 1. A word is a maximal run of non-whitespace characters that contains at least one
     * letter or digit, so {@code "a - b"} is 2 words and {@code "Hello, world!"} is 2.
     *
     * <p>O(n) time, O(1) space. {@code trim().split("\\s+")} would count {@code ""} as one word.
     */
    public static int countWords(String text) {
        if (text == null) return 0;
        int words = 0;
        boolean inToken = false;
        boolean tokenHasLetterOrDigit = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                if (inToken && tokenHasLetterOrDigit) words++;
                inToken = false;
                tokenHasLetterOrDigit = false;
            } else {
                inToken = true;
                tokenHasLetterOrDigit |= Character.isLetterOrDigit(c);
            }
        }
        if (inToken && tokenHasLetterOrDigit) words++;
        return words;
    }

    /**
     * Story 2 (LeetCode 890). A word matches when a bijection maps the pattern's letters onto the
     * word's letters. Both strings are reduced to their shape, each letter numbered in order of
     * first appearance, and compared; equal shapes check both directions of the mapping at once.
     *
     * <p>O(w * k) time for w words of length k; O(k) extra space per word.
     */
    public static List<String> findAndReplacePattern(List<String> words, String pattern) {
        Objects.requireNonNull(words, "words");
        Objects.requireNonNull(pattern, "pattern");
        int[] shape = shape(pattern);
        return words.stream()
                .filter(Objects::nonNull)
                .filter(word -> word.length() == pattern.length())
                .filter(word -> Arrays.equals(shape(word), shape))
                .toList();
    }

    /** {@code "mnnssnn"} becomes {@code [0, 1, 1, 2, 2, 1, 1]}. */
    private static int[] shape(String s) {
        Map<Character, Integer> firstSeen = new HashMap<>();
        int[] shape = new int[s.length()];
        for (int i = 0; i < s.length(); i++) {
            shape[i] = firstSeen.computeIfAbsent(s.charAt(i), c -> firstSeen.size());
        }
        return shape;
    }

    /**
     * Story 3. A run of digits means that many unknown characters: {@code "A3BCD"} is A, three
     * unknowns, then BCD. Returns whether the two strings could come from the same original.
     *
     * <p>Two pointers plus a counter of pending unknowns per side; the strings are never
     * expanded. When both sides have pending unknowns they cancel in one step, so the loop runs
     * O(n + m) times whatever the counts are. O(1) extra space.
     */
    public static boolean couldBeEqual(String first, String second) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        Cursor a = new Cursor(first);
        Cursor b = new Cursor(second);
        while (true) {
            a.readCountIfAtDigits();
            b.readCountIfAtDigits();

            if (a.pending > 0 && b.pending > 0) {
                long both = Math.min(a.pending, b.pending);
                a.pending -= both;
                b.pending -= both;
            } else if (a.pending > 0) {
                if (!b.swallowLetterInto(a)) return false;
            } else if (b.pending > 0) {
                if (!a.swallowLetterInto(b)) return false;
            } else if (a.atEnd() || b.atEnd()) {
                return a.atEnd() && b.atEnd();
            } else if (a.letter() != b.letter()) {
                return false;
            } else {
                a.index++;
                b.index++;
            }
        }
    }

    /** One side of story 3: where we are, and how many unknowns are still owed. */
    private static final class Cursor {
        private final String s;
        private int index;
        private long pending;

        private Cursor(String s) {
            this.s = s;
        }

        boolean atEnd() {
            return index == s.length();
        }

        char letter() {
            return s.charAt(index);
        }

        /** Parses a whole digit run ("12" is twelve, not one then two) once the last count is spent. */
        void readCountIfAtDigits() {
            while (pending == 0 && !atEnd() && Character.isDigit(letter())) {
                long count = 0;
                while (!atEnd() && Character.isDigit(letter())) {
                    count = Math.addExact(Math.multiplyExact(count, 10), letter() - '0');
                    index++;
                }
                pending = count; // "0" leaves pending at 0 and the loop reads on
            }
        }

        /** Consumes one known letter here to pay one unknown the other side still owes. */
        boolean swallowLetterInto(Cursor other) {
            if (atEnd()) return false;
            index++;
            other.pending--;
            return true;
        }
    }
}
