package com.amigoscode.karat.snippets;

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
        // Bug was Collections.sort(words, ...): it sorted the caller's list in place. Stream to a new
        // list instead; sorted() is stable, and comparingInt cannot overflow like s1.length() - s2.length().
        return words.stream()
                .sorted(Comparator.comparingInt(String::length))
                .toList();
    }
}
