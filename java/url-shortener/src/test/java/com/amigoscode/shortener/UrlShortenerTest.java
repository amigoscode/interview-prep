package com.amigoscode.shortener;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UrlShortenerTest {

    @Nested
    class Story1ShortenAndExpand {

        @Test
        void expandsWhatItShortened() {
            UrlShortener s = new UrlShortener();
            String code = s.shorten("https://amigoscode.com/courses");
            assertThat(code).isNotBlank();
            assertThat(s.expand(code)).contains("https://amigoscode.com/courses");
        }

        @Test
        void unknownCodeIsEmpty() {
            assertThat(new UrlShortener().expand("nope")).isEmpty();
            assertThat(new UrlShortener().expand(null)).isEmpty();
        }

        @Test
        void differentUrlsGetDifferentCodes() {
            UrlShortener s = new UrlShortener();
            assertThat(s.shorten("https://a.com")).isNotEqualTo(s.shorten("https://b.com"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   ", "not a url", "ftp://files.example.com", "amigoscode.com", "http://"})
        void rejectsInvalidInput(String input) {
            assertThatThrownBy(() -> new UrlShortener().shorten(input)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejectsNull() {
            assertThatThrownBy(() -> new UrlShortener().shorten(null)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class Story2Idempotent {

        @Test
        void sameUrlSameCode() {
            UrlShortener s = new UrlShortener();
            assertThat(s.shorten("https://amigoscode.com")).isEqualTo(s.shorten("https://amigoscode.com"));
        }

        @Test
        void surroundingWhitespaceDoesNotCreateASecondCode() {
            UrlShortener s = new UrlShortener();
            assertThat(s.shorten("https://amigoscode.com")).isEqualTo(s.shorten("  https://amigoscode.com "));
        }
    }

    @Nested
    class Story3Strategies {

        @Test
        void base62EncodesLikeAnOdometer() {
            assertThat(Base62CounterGenerator.encode(0)).isEqualTo("0");
            assertThat(Base62CounterGenerator.encode(61)).isEqualTo("Z");
            assertThat(Base62CounterGenerator.encode(62)).isEqualTo("10");
            assertThat(Base62CounterGenerator.encode(3_521_614_606_207L)).isEqualTo("ZZZZZZZ"); // 62^7 - 1
        }

        @Test
        void counterStrategyProducesSequentialCodes() {
            UrlShortener s = new UrlShortener(new Base62CounterGenerator(100));
            assertThat(s.shorten("https://a.com")).isEqualTo("1C");
            assertThat(s.shorten("https://b.com")).isEqualTo("1D");
        }

        @Test
        void randomStrategyProducesEightCharacterCodes() {
            UrlShortener s = new UrlShortener(new RandomCodeGenerator());
            assertThat(s.shorten("https://a.com")).hasSize(8).matches("[0-9a-zA-Z]+");
        }

        @Test
        void collidingRandomCodesAreRetriedNotOverwritten() {
            // A seeded Random with a 1-character alphabet position space collides constantly.
            UrlShortener s = new UrlShortener(new RandomCodeGenerator(new Random(42), 1));
            for (int i = 0; i < 20; i++) s.shorten("https://site" + i + ".com");
            for (int i = 0; i < 20; i++) {
                String code = s.shorten("https://site" + i + ".com");
                assertThat(s.expand(code)).contains("https://site" + i + ".com");
            }
        }
    }
}
