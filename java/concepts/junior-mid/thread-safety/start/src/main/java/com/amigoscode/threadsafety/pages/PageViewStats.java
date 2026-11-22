package com.amigoscode.threadsafety.pages;

import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Counts views per page: "/products/ps5" -> 1,204. One instance, shared by every request thread. */
@Service
public class PageViewStats {

    // TODO Task 4, step 1: HashMap is not thread-safe. Concurrent put() calls lose entries.
    private final Map<String, Integer> viewsByPage = new HashMap<>();

    public void record(String page) {
        // TODO Task 4, step 2: get-then-put is two calls. Another thread can get in between.
        Integer views = viewsByPage.get(page);                    // READ
        if (views == null) {                                      // CHECK
            viewsByPage.put(page, 1);                             // WRITE
        } else {
            viewsByPage.put(page, views + 1);                     // WRITE a value computed from a stale read
        }
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
