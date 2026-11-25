package com.amigoscode.threadsafety.demo;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/** What ./demo can show. One scenario per way a singleton bean goes wrong. */
enum Scenario {

    COUNTER("counter", "1, 2, 3", 1_000, "count++ on an int field"),
    VOLATILE("volatile", "3", 1_000, "count++ on a volatile int field (exhibit: stays broken)"),
    PAGES("pages", "4, 5", 5_000, "every request views a different page: a HashMap loses entries"),
    HOT_PAGE("hot-page", "4, 5", 1_000, "every request views the same page: get-then-put loses counts"),
    PRICE_CACHE("price-cache", "4, 5", 50, "every request asks for the same price: check-then-act"),
    RECEIPT("receipt", "6, 7", 100, "request data kept in a field: customers get each other's receipt"),
    LABELS("labels", "6, 7", 2_000, "one SimpleDateFormat shared by every thread: wrong dates"),
    SALES("sales", "8, 9", 20_000, "two fields that must agree: a reader sees one without the other"),
    WRONG_LOCK("wrong-lock", "9", 2_000, "synchronized on the wrong object (exhibit: stays broken)"),
    LOCKED_CACHE("locked-cache", "9", 20, "a lock held during a slow call: correct, and one at a time (exhibit)");

    private final String flag;
    private final String tasks;
    private final int defaultRequests;
    private final String summary;

    Scenario(String flag, String tasks, int defaultRequests, String summary) {
        this.flag = flag;
        this.tasks = tasks;
        this.defaultRequests = defaultRequests;
        this.summary = summary;
    }

    String flag() {
        return flag;
    }

    String tasks() {
        return tasks;
    }

    int defaultRequests() {
        return defaultRequests;
    }

    String summary() {
        return summary;
    }

    static Scenario from(String value) {
        String wanted = value.toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(scenario -> scenario.flag.equals(wanted))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown scenario: " + value + ". Use one of: " + names()));
    }

    static String names() {
        return Arrays.stream(values()).map(Scenario::flag).collect(Collectors.joining(" | "));
    }
}
