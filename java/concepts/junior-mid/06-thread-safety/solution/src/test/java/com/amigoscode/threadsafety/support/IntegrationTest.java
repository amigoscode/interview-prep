package com.amigoscode.threadsafety.support;

import com.amigoscode.threadsafety.pages.PageViewStats;
import com.amigoscode.threadsafety.pricing.PriceCache;
import com.amigoscode.threadsafety.sales.SalesStats;
import com.amigoscode.threadsafety.visits.VisitCounter;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/**
 * Base class for the *IT tests. They start the real application, with Tomcat on a free port, and
 * use the beans Spring built: the same singletons every request thread would get. Only the two
 * slow collaborators are replaced, by stubs the tests can control. One application is shared by
 * every test class.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "spring.main.banner-mode=off")
@Import(IntegrationTest.TestBeans.class)
public abstract class IntegrationTest {

    @TestConfiguration
    static class TestBeans {

        @Bean
        @Primary
        ControllablePriceSource controllablePriceSource() {
            return new ControllablePriceSource();
        }

        @Bean
        @Primary
        ControllableLoyaltyClient controllableLoyaltyClient() {
            return new ControllableLoyaltyClient();
        }
    }

    private static final HttpClient HTTP = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    @Value("${local.server.port}")
    private int port;

    @Autowired
    protected ApplicationContext context;

    @Autowired
    protected ControllablePriceSource priceSource;

    @Autowired
    protected ControllableLoyaltyClient loyaltyClient;

    @BeforeEach
    void cleanState() {
        priceSource.reset();
        loyaltyClient.reset();
        context.getBean(VisitCounter.class).reset();
        context.getBean(PageViewStats.class).reset();
        context.getBean(PriceCache.class).reset();
        context.getBean(SalesStats.class).reset();
    }

    protected HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return HTTP.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    protected HttpResponse<String> post(String path) throws IOException, InterruptedException {
        return HTTP.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
    }
}
