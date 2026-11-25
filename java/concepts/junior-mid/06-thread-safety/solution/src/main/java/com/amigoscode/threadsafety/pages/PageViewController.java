package com.amigoscode.threadsafety.pages;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pages")
public class PageViewController {

    public record PageViews(String page, int views) {
    }

    private final PageViewStats stats;

    public PageViewController(PageViewStats stats) {
        this.stats = stats;
    }

    @PostMapping("/{page}/views")
    public void view(@PathVariable String page) {
        stats.record(page);
    }

    @GetMapping("/{page}/views")
    public PageViews views(@PathVariable String page) {
        return new PageViews(page, stats.views(page));
    }
}
