package com.amigoscode.restapi.demo;

import com.amigoscode.restapi.demo.Http.Reply;
import jakarta.persistence.Entity;
import jakarta.validation.Constraint;
import jakarta.validation.Valid;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.springframework.core.MethodParameter;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

/**
 * Makes the HTTP contract of this application visible. Two reports:
 *
 * ENDPOINTS: every endpoint Spring has registered (read from RequestMappingHandlerMapping), with
 * what is wrong with it. Each GET is really called, and the database is compared before and after.
 *
 * STATUS CODES: a fixed set of probe requests, the status code each one got and the one it should get.
 *
 * Run it with ./inspect (or .\inspect on Windows). It resets the database, like ./demo.
 */
public final class Inspect {

    public static void main(String[] args) throws Exception {
        boolean endpoints = true;
        boolean probes = true;
        boolean bodies = false;
        for (String arg : args) {
            switch (arg) {
                case "--endpoints" -> probes = false;
                case "--probes" -> endpoints = false;
                case "--bodies" -> bodies = true;
                default -> {
                    System.out.println("Usage: ./inspect [--endpoints | --probes] [--bodies]     (Windows: .\\inspect ...)");
                    System.out.println();
                    System.out.println("  (no option)   both reports");
                    System.out.println("  --endpoints   only the table of endpoints and what is wrong with each");
                    System.out.println("  --probes      only the status-code table");
                    System.out.println("  --bodies      also print the body of every probe response");
                    return;
                }
            }
        }
        if (!Db.isUp()) {
            return;
        }
        try (App app = App.start()) {
            if (endpoints) {
                endpoints(app);
            }
            if (probes) {
                probes(app, bodies);
            }
            app.reset();
            System.out.println();
        }
    }

    // ---------------------------------------------------------------- endpoints

    private record Endpoint(String method, String path, HandlerMethod handler) {
    }

    private static final List<String> METHOD_ORDER = List.of("GET", "POST", "PUT", "PATCH", "DELETE");

    private static void endpoints(App app) throws Exception {
        List<Endpoint> endpoints = new ArrayList<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : app.endpoints().entrySet()) {
            HandlerMethod handler = entry.getValue();
            if (!handler.getBeanType().getPackageName().startsWith("com.amigoscode.restapi")
                    || entry.getKey().getPathPatternsCondition() == null) {
                continue;
            }
            for (String path : entry.getKey().getPathPatternsCondition().getPatternValues()) {
                for (RequestMethod method : entry.getKey().getMethodsCondition().getMethods()) {
                    endpoints.add(new Endpoint(method.name(), path, handler));
                }
            }
        }
        endpoints.sort(Comparator.comparing(Endpoint::path)
                .thenComparing(endpoint -> METHOD_ORDER.indexOf(endpoint.method())));

        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {"METHOD", "PATH", "HANDLER", "FINDINGS"});
        int findingsTotal = 0;
        for (Endpoint endpoint : endpoints) {
            List<String> findings = findings(app, endpoint);
            findingsTotal += (int) findings.stream().filter(Inspect::isProblem).count();
            String handler = endpoint.handler().getBeanType().getSimpleName() + "." + endpoint.handler().getMethod().getName();
            for (int i = 0; i < findings.size(); i++) {
                rows.add(i == 0
                        ? new String[] {endpoint.method(), endpoint.path(), handler, findings.get(i)}
                        : new String[] {"", "", "", findings.get(i)});
            }
        }

        System.out.println();
        System.out.println("ENDPOINTS (from Spring's RequestMappingHandlerMapping. Every GET was called once,");
        System.out.println("and the orders table was compared before and after)");
        System.out.println();
        printTable(rows);
        System.out.println();
        System.out.println(findingsTotal == 0
                ? "  " + endpoints.size() + " endpoints, no findings."
                : "  " + endpoints.size() + " endpoints, " + findingsTotal + " findings.");
    }

    private static boolean isProblem(String finding) {
        return Character.isUpperCase(finding.charAt(0)) && Character.isUpperCase(finding.charAt(1));
    }

    /** What is wrong with one endpoint. Problems start with capital letters. */
    private static List<String> findings(App app, Endpoint endpoint) throws Exception {
        List<String> findings = new ArrayList<>();

        if (endpoint.method().equals("GET")) {
            app.reset();
            Map<Long, Map<String, String>> before = app.snapshot();
            app.http.send("GET", endpoint.path().replaceAll("\\{[^}]+}", "1"), null);
            List<String> changes = Db.changes(before, app.snapshot());
            app.reset();
            findings.add(changes.isEmpty()
                    ? "safe: the GET changed nothing"
                    : "GET CHANGES DATA, NOT SAFE: " + changes.get(0).replaceAll("\\s+", " "));
        }

        for (MethodParameter parameter : endpoint.handler().getMethodParameters()) {
            if (!parameter.hasParameterAnnotation(RequestBody.class)) {
                continue;
            }
            Class<?> body = parameter.getParameterType();
            if (body.isAnnotationPresent(Entity.class)) {
                findings.add("BODY IS THE ENTITY " + body.getSimpleName() + ": the client can set every column");
            }
            boolean validated = parameter.hasParameterAnnotation(Valid.class) || parameter.hasParameterAnnotation(Validated.class);
            if (!validated && hasConstraints(body)) {
                findings.add("NO @Valid: the rules on " + body.getSimpleName() + " are never checked");
            }
        }

        Class<?> entity = find(endpoint.handler().getMethod().getGenericReturnType(), type -> type.isAnnotationPresent(Entity.class));
        if (entity != null) {
            findings.add("RETURNS THE ENTITY " + entity.getSimpleName() + ": every column goes to the client");
        }

        if (findings.isEmpty()) {
            findings.add("ok");
        }
        return findings;
    }

    /** True when a field of the class carries a Bean Validation rule such as @NotBlank. */
    private static boolean hasConstraints(Class<?> type) {
        List<AnnotatedElement> elements = new ArrayList<>(Arrays.asList(type.getDeclaredFields()));
        if (type.isRecord()) {
            elements.addAll(Arrays.asList(type.getRecordComponents()));
        }
        for (AnnotatedElement element : elements) {
            if (element instanceof Field field && field.isSynthetic()) {
                continue;
            }
            for (Annotation annotation : element.getAnnotations()) {
                if (annotation.annotationType().isAnnotationPresent(Constraint.class)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Looks through List<X>, ResponseEntity<X>... for a class that matches. */
    private static Class<?> find(Type type, Predicate<Class<?>> matches) {
        if (type instanceof Class<?> clazz) {
            return matches.test(clazz) ? clazz : null;
        }
        if (type instanceof ParameterizedType parameterized) {
            for (Type argument : parameterized.getActualTypeArguments()) {
                Class<?> found = find(argument, matches);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- status codes

    /** Extra things a probe can require besides the status code. Returns a complaint, or null. */
    private interface Extra {
        String check(App app, Reply reply) throws Exception;
    }

    private record Probe(String what, String method, String path, String body, String contentType,
                         int shouldBe, String prepare, Extra extra) {

        Probe(String what, String method, String path, String body, int shouldBe) {
            this(what, method, path, body, "application/json", shouldBe, null, null);
        }

        Probe with(Extra extra) {
            return new Probe(what, method, path, body, contentType, shouldBe, prepare, extra);
        }
    }

    private static final String VALID_ORDER =
            "{\"customer\":\"Nina Patel\",\"product\":\"MOUSE\",\"quantity\":2,\"shippingAddress\":\"8 Queen Square, Bath\"}";

    private static void probes(App app, boolean bodies) throws Exception {
        String cancel = app.cancelMethod();
        List<Probe> probes = List.of(
                new Probe("reads an order that does not exist", "GET", "/api/orders/999", null, 404),
                new Probe("uses an id that is not a number", "GET", "/api/orders/abc", null, 400),
                new Probe("asks for a URL that does not exist", "GET", "/api/nope", null, 404),
                new Probe("uses a method the URL does not have", "DELETE", "/api/orders", null, 405)
                        .with((a, reply) -> reply.header("Allow") == null ? "no Allow header" : null),
                new Probe("sends a body that is not JSON", "POST", "/api/orders", "{\"customer\": \"Nina\", ", 400),
                new Probe("sends JSON as text/plain", "POST", "/api/orders", VALID_ORDER, "text/plain", 415, null, null),
                new Probe("orders a product that does not exist", "POST", "/api/orders",
                        VALID_ORDER.replace("MOUSE", "PLAYSTATION"), 400),
                new Probe("orders a quantity of -5", "POST", "/api/orders", VALID_ORDER.replace(":2,", ":-5,"), 400)
                        .with((a, reply) -> reply.body().contains("\"quantity\"") ? null : "does not name the field"),
                new Probe("filters by a status that does not exist", "GET", "/api/orders?status=DONE", null, 400),
                new Probe("cancels with a GET", "GET", "/api/orders/2/cancel", null, 405),
                new Probe("cancels a shipped order", cancel, "/api/orders/5/cancel", null, 409),
                new Probe("creates an order", "POST", "/api/orders", VALID_ORDER, 201)
                        .with((a, reply) -> reply.header("Location") == null ? "no Location header" : null),
                new Probe("replaces an order (PUT)", "PUT", "/api/orders/2", VALID_ORDER, 200),
                new Probe("sets status and total in a PUT", "PUT", "/api/orders/2",
                        VALID_ORDER.replace("}", ",\"status\":\"PAID\",\"total\":0.01}"), 200)
                        .with((a, reply) -> {
                            Map<String, String> row = a.snapshot().get(2L);
                            return row.get("status").equals("PENDING") && !row.get("total").equals("0.01")
                                    ? null : "the client changed status and total";
                        }),
                new Probe("reads an order that has an internal note", "GET", "/api/orders/4", null, 200)
                        .with((a, reply) -> reply.body().contains("internalNote") ? "shows internalNote" : null),
                new Probe("changes one field (PATCH)", "PATCH", "/api/orders/4", "{\"shippingAddress\":\"1 New Street, Cardiff\"}", 200)
                        .with((a, reply) -> "Gift. No invoice in the box".equals(a.snapshot().get(4L).get("notes"))
                                ? null : "other fields changed"),
                new Probe("deletes an order", "DELETE", "/api/orders/6", null, 204)
                        .with((a, reply) -> reply.body().isBlank() ? null : "has a body"),
                new Probe("deletes the same order again", "DELETE", "/api/orders/6", null, "application/json", 404,
                        "/api/orders/6", null));

        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {"#", "THE CLIENT...", "REQUEST", "GOT", "SHOULD BE", "BODY TYPE", "VERDICT"});
        List<String> printedBodies = new ArrayList<>();
        int right = 0;
        for (int i = 0; i < probes.size(); i++) {
            Probe probe = probes.get(i);
            app.reset();
            if (probe.prepare() != null) {
                app.http.send(probe.method(), probe.prepare(), null);
            }
            Reply reply = app.http.send(probe.method(), probe.path(), probe.body(), probe.contentType());

            List<String> complaints = new ArrayList<>();
            if (reply.status() != probe.shouldBe()) {
                complaints.add(reply.status() >= 500 ? "a 500 blames the server" : "wrong status");
            }
            if (reply.status() >= 400 && Json.leaksInternals(reply.body())) {
                complaints.add("body shows internals");
            }
            if (reply.status() >= 400 && !reply.contentType().equals("application/problem+json")) {
                complaints.add("not problem+json");
            }
            if (probe.extra() != null) {
                String complaint = probe.extra().check(app, reply);
                if (complaint != null) {
                    complaints.add(complaint);
                }
            }
            right += complaints.isEmpty() ? 1 : 0;
            rows.add(new String[] {
                    String.valueOf(i + 1), probe.what(), probe.method() + " " + probe.path(),
                    String.valueOf(reply.status()), String.valueOf(probe.shouldBe()),
                    reply.body().isBlank() ? "(no body)" : reply.contentType().replace("application/", ""),
                    complaints.isEmpty() ? "ok" : "WRONG: " + String.join("; ", complaints)});
            printedBodies.add("  " + (i + 1) + ". " + probe.method() + " " + probe.path() + "  ->  " + reply.statusLine() + "\n"
                    + (reply.body().isBlank() ? "     (no body)" : Json.pretty(reply.body()).indent(5).stripTrailing()));
        }

        System.out.println();
        System.out.println("STATUS CODES (" + probes.size() + " probe requests. The database is reset before each one)");
        System.out.println();
        printTable(rows);
        System.out.println();
        System.out.println("  " + right + " of " + probes.size() + " answers are right.");
        if (bodies) {
            System.out.println();
            System.out.println("RESPONSE BODIES");
            System.out.println();
            printedBodies.forEach(body -> {
                System.out.println(body);
                System.out.println();
            });
        }
    }

    private static void printTable(List<String[]> rows) {
        int[] widths = new int[rows.get(0).length];
        for (String[] row : rows) {
            for (int c = 0; c < row.length; c++) {
                widths[c] = Math.max(widths[c], row[c].length());
            }
        }
        for (int r = 0; r < rows.size(); r++) {
            StringBuilder line = new StringBuilder("  ");
            for (int c = 0; c < widths.length; c++) {
                line.append(String.format("%-" + widths[c] + "s", rows.get(r)[c])).append("  ");
            }
            System.out.println(line.toString().stripTrailing());
            if (r == 0) {
                StringBuilder rule = new StringBuilder("  ");
                for (int width : widths) {
                    rule.append("-".repeat(width)).append("  ");
                }
                System.out.println(rule.toString().stripTrailing());
            }
        }
    }

    private Inspect() {
    }
}
