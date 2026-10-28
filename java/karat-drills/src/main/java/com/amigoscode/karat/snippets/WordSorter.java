package com.amigoscode.karat.snippets;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Theory question: "Sort the list of strings based on their length using a lambda expression."
 * The original used an anonymous {@code Comparator}.
 */
public final class WordSorter {

    private WordSorter() {
    }

    /**
     * Returns the words ordered by length, shortest first, words of equal length in their original
     * order. The list passed in is not changed.
     */
    public static List<String> sortedByLength(List<String> words) {
        Collections.sort(words, new Comparator<String>() {
            @Override
            public int compare(String s1, String s2) {
                return s1.length() - s2.length();
            }
        });
        return words;
    }
}
