package com.amigoscode.threadsafety.demo;

import com.amigoscode.threadsafety.ThreadSafetyApplication;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.function.IntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

/** What the demo and inspect tools share: starting the application quietly and sending HTTP requests at it. */
final class Tool {

    /** One answer from the application. Status -1 means the request itself failed (see body). */
    record Reply(int index, int status, String body) {
    }

    record Run(List<Reply> replies, long millis) {

        int count(int status) {
            return (int) replies.stream().filter(reply -> reply.status() == status).count();
        }

        List<Reply> failures() {
            return replies.stream().filter(reply -> reply.status() != 200).toList();
        }
    }

    private Tool() {
    }

    /** Starts the real application on a free port, with the logging turned down. */
    static ConfigurableApplicationContext startApplication() {
        SpringApplication app = new SpringApplication(ThreadSafetyApplication.class);
        app.setBannerMode(Banner.Mode.OFF);
        app.setLogStartupInfo(false);
        return app.run(
                "--server.port=0",
                // Do not wait for requests that will never finish (see the "pages" scenario).
                "--server.shutdown=immediate",
                "--logging.level.root=WARN",
                // A 500 is counted and shown as one line instead of a full stack trace per request.
                "--logging.level.org.apache.catalina.core.ContainerBase=OFF",
                "--spring.web.error.include-message=always");
    }

    static String baseUrl(ConfigurableApplicationContext context) {
        return "http://localhost:" + context.getEnvironment().getProperty("local.server.port");
    }

    static HttpClient httpClient() {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /** A request that has no answer after this long counts as stuck (status -1). */
    private static final Duration PATIENCE = Duration.ofSeconds(15);

    static HttpRequest get(String url) {
        return HttpRequest.newBuilder(URI.create(url)).timeout(PATIENCE).GET().build();
    }

    static HttpRequest post(String url) {
        return HttpRequest.newBuilder(URI.create(url)).timeout(PATIENCE)
                .POST(HttpRequest.BodyPublishers.noBody()).build();
    }

    /** A few requests nobody counts, so the first real request is not slowed down by startup work. */
    static void warmUp(HttpClient http, String baseUrl) throws Exception {
        for (int i = 0; i < 20; i++) {
            http.send(get(baseUrl + "/api/visits"), HttpResponse.BodyHandlers.discarding());
        }
    }

    /**
     * Sends {@code requests} HTTP requests, at most {@code atOnce} of them in flight at the same
     * time. Every sender waits behind one latch, so they all start together.
     */
    static Run send(HttpClient http, int requests, int atOnce, IntFunction<HttpRequest> request) throws Exception {
        CountDownLatch go = new CountDownLatch(1);
        Semaphore inFlight = new Semaphore(atOnce);
        List<Future<Reply>> futures = new ArrayList<>(requests);
        long startedAt;
        try (ExecutorService senders = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < requests; i++) {
                int index = i;
                futures.add(senders.submit(() -> {
                    go.await();
                    inFlight.acquire();
                    try {
                        HttpResponse<String> response = http.send(request.apply(index), HttpResponse.BodyHandlers.ofString());
                        return new Reply(index, response.statusCode(), response.body());
                    } catch (Exception e) {
                        return new Reply(index, -1, e.getClass().getSimpleName() + ": " + e.getMessage());
                    } finally {
                        inFlight.release();
                    }
                }));
            }
            startedAt = System.nanoTime();
            go.countDown();
        }
        long millis = (System.nanoTime() - startedAt) / 1_000_000;
        List<Reply> replies = new ArrayList<>(requests);
        for (Future<Reply> future : futures) {
            replies.add(future.get());
        }
        return new Run(replies, millis);
    }

    /** The value of one field in a small JSON answer, or null. Good enough for the flat answers of this app. */
    static String json(String body, String field) {
        Matcher matcher = Pattern.compile("\"" + field + "\"\\s*:\\s*(\"([^\"]*)\"|[-0-9.]+)").matcher(body == null ? "" : body);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(2) != null ? matcher.group(2) : matcher.group(1);
    }

    /** 20000 -> "20,000". */
    static String n(long number) {
        return String.format(Locale.ROOT, "%,d", number);
    }

    static void line(String label, Object value) {
        System.out.printf(Locale.ROOT, "%-34s %s%n", label, value);
    }
}
