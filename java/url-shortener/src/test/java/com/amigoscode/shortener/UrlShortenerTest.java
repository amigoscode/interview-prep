package com.amigoscode.shortener;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UrlShortenerTest {

    @Test
    void projectIsWiredUp() {
        assertThat(new UrlShortener()).isNotNull();
    }

    @Test
    @Disabled("story 1: remove this line to begin")
    void expandsWhatItShortened() {
        UrlShortener s = new UrlShortener();
        String code = s.shorten("https://amigoscode.com/courses");
        assertThat(s.expand(code)).contains("https://amigoscode.com/courses");
    }
}
