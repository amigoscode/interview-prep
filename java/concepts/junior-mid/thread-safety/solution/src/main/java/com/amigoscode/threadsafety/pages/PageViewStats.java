package com.amigoscode.threadsafety.pages;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/** Counts views per page: "/products/ps5" -> 1,204. One instance, shared by every request thread. */
@Service
public class PageViewStats {

    // Step 1: a map that survives concurrent put() calls.
    private final Map<String, Integer> viewsByPage = new ConcurrentHashMap<>();

    public void record(String page) {
        // Step 2: read, add and write in ONE call. merge() does not let another thread in between.
        viewsByPage.merge(page, 1, Integer::sum);
    }

    public int views(String page) {
        Integer views = viewsByPage.get(page);
        return views == null ? 0 : views;
    }

    /** How many different pages have been viewed. */
    public int pages() {
        return viewsByPage.size();
    }

    public void reset() {
        viewsByPage.clear();
    }
}
