package com.amigoscode.restapi.demo;

import com.amigoscode.restapi.demo.Http.Reply;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Starts the real application, resets the database to six known orders, sends real HTTP requests
 * and prints each request, each response and what changed in the database.
 * Run it with ./demo (or .\demo on Windows). ./demo --help lists the scenarios.
 */
public final class Demo {

    private interface Scenario {
        void run(App app) throws Exception;
    }

    private record Entry(String task, String about, Scenario scenario) {
    }

    private static final Map<String, Entry> SCENARIOS = new LinkedHashMap<>();

    static {
        SCENARIOS.put("prefetch", new Entry("Tasks 1-2", "a link checker visits every link on the admin page (the default)", Demo::prefetch));
        SCENARIOS.put("cancel", new Entry("Task 3", "cancel an order, cancel it again, cancel a shipped one", Demo::cancel));
        SCENARIOS.put("errors", new Entry("Tasks 4, 6", "five client mistakes: which status code and body come back?", Demo::errors));
        SCENARIOS.put("validation", new Entry("Tasks 5-6", "create an order with a negative quantity and no customer", Demo::validation));
        SCENARIOS.put("mass-assignment", new Entry("Tasks 7, 9", "the client sends status, total and internalNote in a PUT", Demo::massAssignment));
        SCENARIOS.put("put", new Entry("Tasks 8-9", "change the address with a PUT that leaves two fields out", Demo::put));
        SCENARIOS.put("patch", new Entry("Tasks 8-9", "change the address with a PATCH that sends one field", Demo::patch));
        SCENARIOS.put("patch-null", new Entry("Task 9", "try to clear the note with a PATCH that sends \"notes\": null", Demo::patchNull));
        SCENARIOS.put("status-codes", new Entry("Tasks 10-11", "create an order, delete an order, delete it again", Demo::statusCodes));
    }

    public static void main(String[] args) throws Exception {
        String name = "prefetch";
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--help", "-h" -> {
                    printHelp();
                    return;
                }
                case "--scenario" -> {
                    if (++i >= args.length) {
                        System.out.println("--scenario needs a name");
                        System.out.println();
                        printHelp();
                        return;
                    }
                    name = args[i];
                }
                default -> {
                    System.out.println("Unknown option: " + args[i]);
                    System.out.println();
                    printHelp();
                    return;
                }
            }
        }
        Entry entry = SCENARIOS.get(name);
        if (entry == null) {
            System.out.println("Unknown scenario: " + name);
            System.out.println();
            printHelp();
            return;
        }
        if (!Db.isUp()) {
            return;
        }
        try (App app = App.start()) {
            System.out.println();
            System.out.println("Scenario: " + name + " | the database was reset to six orders: 1-3 PENDING, 4 PAID, 5 SHIPPED, 6 CANCELLED");
            entry.scenario().run(app);
            System.out.println();
        }
    }

    // ---------------------------------------------------------------- prefetch (Tasks 1 and 2)

    private static void prefetch(App app) throws Exception {
        System.out.println("A member of staff opens the \"pending orders\" page. A link checker then requests every");
        System.out.println("link on that page, to see if any is broken. A browser that prefetches links, a chat app");
        System.out.println("that builds a preview and a search crawler do the same. They only ever send GET.");
        System.out.println();

        Map<Long, Map<String, String>> before = app.snapshot();
        Http.printRequest("GET", "/admin/pending-orders", null);
        Reply page = app.http.send("GET", "/admin/pending-orders", null);
        System.out.println("  <- " + page.statusLine());
        System.out.println("     Content-Type: " + page.header("Content-Type"));

        List<String> links = new ArrayList<>();
        Matcher href = Pattern.compile("href=\"([^\"]+)\"").matcher(page.body());
        while (href.find()) {
            links.add(href.group(1));
        }
        int forms = page.body().split("<form", -1).length - 1;
        System.out.println("     Links on the page (<a href>):    " + links.size());
        links.forEach(link -> System.out.println("       " + link));
        System.out.println("     Buttons on the page (<form>):    " + forms);
        System.out.println();

        if (links.isEmpty()) {
            System.out.println("  Nothing to follow: a link checker requests links, it does not press buttons.");
            System.out.println("  It still has the old cancel URLs from its last visit, so it requests those:");
            for (long id = 1; id <= 3; id++) {
                links.add("/api/orders/" + id + "/cancel");
            }
        } else {
            System.out.println("  The link checker requests each link:");
        }
        System.out.println();
        for (String link : links) {
            app.http.showShort("GET", link, null);
        }

        Map<Long, Map<String, String>> after = app.snapshot();
        Db.printChanges(before, after);
        long cancelled = after.values().stream().filter(row -> row.get("status").equals("CANCELLED")).count()
                - before.values().stream().filter(row -> row.get("status").equals("CANCELLED")).count();
        int changed = Db.changes(before, after).size();

        System.out.println("GET requests sent by the link checker:  " + links.size());
        System.out.println("Orders cancelled:                       " + cancelled);
        System.out.println();
        if (changed == 0) {
            System.out.println("RESULT: OK. " + links.size() + " GET requests. 0 orders changed.");
        } else {
            System.out.println("RESULT: " + links.size() + " GET requests. " + cancelled + " orders cancelled. Nobody clicked anything.");
        }
    }

    // ---------------------------------------------------------------- cancel (Task 3)

    private static void cancel(App app) throws Exception {
        String method = app.cancelMethod();
        System.out.println("This application cancels with: " + method + " /api/orders/{id}/cancel");
        System.out.println("A client cancels order 1. The answer gets lost, so the client sends the same request again.");
        System.out.println("Then somebody tries to cancel order 5, which has already been shipped.");
        System.out.println();

        Map<Long, Map<String, String>> before = app.snapshot();
        Reply first = app.http.showShort(method, "/api/orders/1/cancel", null);
        Map<Long, Map<String, String>> afterFirst = app.snapshot();
        Db.printChanges(before, afterFirst);

        Reply second = app.http.showShort(method, "/api/orders/1/cancel", null);
        Map<Long, Map<String, String>> afterSecond = app.snapshot();
        Db.printChanges(afterFirst, afterSecond);

        Reply shipped = app.http.show(method, "/api/orders/5/cancel", null);
        Map<Long, Map<String, String>> afterShipped = app.snapshot();
        Db.printChanges(afterSecond, afterShipped);

        System.out.println("Cancel order 1:            " + first.statusLine());
        System.out.println("Cancel order 1 again:      " + second.statusLine() + "   (changed in the database: "
                + (Db.changes(afterFirst, afterSecond).isEmpty() ? "nothing" : "something") + ")");
        System.out.println("Cancel shipped order 5:    " + shipped.statusLine() + "   (should be 409 Conflict)");
        System.out.println();
        List<String> wrong = new ArrayList<>();
        if (method.equals("GET")) {
            wrong.add("Cancel is still a GET (Task 2)");
        }
        if (first.status() != 200 || second.status() != 200) {
            wrong.add("Cancelling twice should be 200 both times");
        }
        if (shipped.status() != 409) {
            wrong.add("The shipped order answered " + shipped.status() + ", not 409 (Task 4)");
        }
        if (wrong.isEmpty()) {
            System.out.println("RESULT: OK. The repeat changed nothing and still got a 200. The shipped order was refused with a 409.");
        } else {
            System.out.println("RESULT: NOT THERE YET. " + String.join(". ", wrong) + ".");
        }
    }

    // ---------------------------------------------------------------- errors (Task 4)

    private static void errors(App app) throws Exception {
        System.out.println("Five requests. In every one the CLIENT made the mistake, so every answer should be a 4xx.");
        System.out.println();

        record Mistake(String title, String method, String path, String body, int shouldBe) {
        }
        List<Mistake> mistakes = List.of(
                new Mistake("1. An order that does not exist", "GET", "/api/orders/999", null, 404),
                new Mistake("2. A body that is not JSON (it stops halfway)", "POST", "/api/orders",
                        "{\"customer\": \"Nina Patel\", \"product\": ", 400),
                new Mistake("3. A product the shop does not sell", "POST", "/api/orders",
                        "{\"customer\": \"Nina Patel\", \"product\": \"PLAYSTATION\", \"quantity\": 1, \"shippingAddress\": \"8 Queen Square, Bath\"}", 400),
                new Mistake("4. A status that does not exist, in the filter", "GET", "/api/orders?status=DONE", null, 400),
                new Mistake("5. Cancelling an order that was already shipped", app.cancelMethod(), "/api/orders/5/cancel", null, 409));

        Map<Long, Map<String, String>> before = app.snapshot();
        List<String> summary = new ArrayList<>();
        int wrongStatus = 0;
        int leaks = 0;
        int notProblemJson = 0;
        for (Mistake mistake : mistakes) {
            System.out.println(mistake.title());
            Reply reply = app.http.show(mistake.method(), mistake.path(), mistake.body());
            boolean leak = Json.leaksInternals(reply.body());
            boolean statusOk = reply.status() == mistake.shouldBe();
            wrongStatus += statusOk ? 0 : 1;
            leaks += leak ? 1 : 0;
            notProblemJson += reply.contentType().equals("application/problem+json") ? 0 : 1;
            summary.add(String.format("  %-48s got %d   should be %d   %-5s  %s",
                    mistake.title(), reply.status(), mistake.shouldBe(), statusOk ? "ok" : "WRONG",
                    leak ? "LEAKS INTERNALS" : "no leak"));
        }
        Db.printChanges(before, app.snapshot());
        summary.forEach(System.out::println);
        System.out.println();
        if (wrongStatus == 0 && leaks == 0 && notProblemJson == 0) {
            System.out.println("RESULT: OK. 5 client mistakes, 5 answers in the 4xx range, all application/problem+json, no internals.");
        } else {
            System.out.println("RESULT: 5 client mistakes. " + wrongStatus + " got the wrong status code. " + leaks
                    + " bodies show class names, stack traces or SQL. " + notProblemJson + " are not application/problem+json.");
        }
    }

    // ---------------------------------------------------------------- validation (Task 5)

    private static void validation(App app) throws Exception {
        System.out.println("A client creates an order with no customer name and a quantity of -5.");
        System.out.println("OrderRequest says: @NotBlank customer, @Positive quantity.");
        System.out.println();

        Map<Long, Map<String, String>> before = app.snapshot();
        Reply reply = app.http.show("POST", "/api/orders", """
                {
                  "customer": "",
                  "product": "KEYBOARD",
                  "quantity": -5,
                  "shippingAddress": "4 Park Row, Bristol"
                }
                """);
        Map<Long, Map<String, String>> after = app.snapshot();
        Db.printChanges(before, after);

        boolean saved = after.size() > before.size();
        boolean namesFields = reply.body().contains("\"customer\"") && reply.body().contains("\"quantity\"");
        if (saved) {
            Map<String, String> row = after.get(after.keySet().stream().max(Long::compare).orElseThrow());
            System.out.println("RESULT: INVALID ORDER SAVED. Status " + reply.status() + ". The shop now has an order for "
                    + row.get("quantity") + " keyboards, for nobody, with a total of " + row.get("total") + ".");
        } else if (reply.status() == 400 && namesFields) {
            System.out.println("RESULT: OK. 400, nothing saved, and the body names both invalid fields: customer and quantity.");
        } else {
            System.out.println("RESULT: NOT THERE YET. Nothing was saved, but the answer is " + reply.status()
                    + (namesFields ? "." : " and the body does not say which fields are invalid."));
        }
    }

    // ---------------------------------------------------------------- mass assignment (Task 7)

    private static void massAssignment(App app) throws Exception {
        System.out.println("Order 1 is PENDING, total 200.00. Its customer sends a PUT with the usual fields, and adds");
        System.out.println("three that no customer should be able to set: status, total and internalNote.");
        System.out.println();

        Map<Long, Map<String, String>> before = app.snapshot();
        Reply put = app.http.show("PUT", "/api/orders/1", """
                {
                  "customer": "Maria Jones",
                  "product": "MONITOR",
                  "quantity": 1,
                  "shippingAddress": "12 Mill Lane, Leeds",
                  "notes": "Ring the bell twice",
                  "contactPhone": "0113 496 0001",
                  "status": "PAID",
                  "total": 0.01,
                  "internalNote": "VIP. Never check this customer"
                }
                """);
        Map<Long, Map<String, String>> after = app.snapshot();
        Db.printChanges(before, after);

        System.out.println("Then anybody reads order 4:");
        System.out.println();
        Reply get = app.http.show("GET", "/api/orders/4", null);

        List<String> assigned = new ArrayList<>();
        for (String column : List.of("status", "total", "internal_note")) {
            if (after.containsKey(1L) && !before.get(1L).get(column).equals(after.get(1L).get(column))) {
                assigned.add(column);
            }
        }
        boolean leak = get.body().contains("internalNote") || put.body().contains("internalNote");
        if (put.status() != 200) {
            System.out.println("RESULT: NOT THERE YET. The PUT answered " + put.status() + ", it should be a 200.");
        }
        if (!assigned.isEmpty()) {
            System.out.println("RESULT: MASS ASSIGNMENT. The client changed " + String.join(", ", assigned)
                    + ". Order 1 is " + after.get(1L).get("status") + " for " + after.get(1L).get("total")
                    + " and nobody paid.");
        }
        if (leak) {
            System.out.println("RESULT: LEAK. The response shows internalNote, a note written by staff for staff.");
        }
        if (put.status() == 200 && assigned.isEmpty() && !leak) {
            System.out.println("RESULT: OK. The client sent status, total and internalNote. The server ignored all three,");
            System.out.println("        and no response shows internalNote.");
        }
    }

    // ---------------------------------------------------------------- put and patch (Task 8)

    private static final String NEW_ADDRESS = "1 New Street, Cardiff";

    private static void put(App app) throws Exception {
        System.out.println("Order 4 has a note for the courier and a phone number. An older version of the mobile app");
        System.out.println("does not know those two fields. Its \"change address\" screen sends a PUT with the fields it knows.");
        System.out.println();

        Map<Long, Map<String, String>> before = app.snapshot();
        Reply reply = app.http.show("PUT", "/api/orders/4", """
                {
                  "customer": "Liam Brown",
                  "product": "MONITOR",
                  "quantity": 2,
                  "shippingAddress": "%s"
                }
                """.formatted(NEW_ADDRESS));
        Map<Long, Map<String, String>> after = app.snapshot();
        Db.printChanges(before, after);
        resultOfAddressChange("PUT", reply, before.get(4L), after.get(4L));
    }

    private static void patch(App app) throws Exception {
        System.out.println("The same change of address for order 4, sent as a PATCH with the one field that changes.");
        System.out.println();

        Map<Long, Map<String, String>> before = app.snapshot();
        Reply reply = app.http.show("PATCH", "/api/orders/4", """
                {
                  "shippingAddress": "%s"
                }
                """.formatted(NEW_ADDRESS));
        Map<Long, Map<String, String>> after = app.snapshot();
        Db.printChanges(before, after);
        resultOfAddressChange("PATCH", reply, before.get(4L), after.get(4L));
    }

    private static void patchNull(App app) throws Exception {
        System.out.println("Order 4 has the note \"Gift. No invoice in the box\". The client wants to remove it,");
        System.out.println("and sends a PATCH with \"notes\": null.");
        System.out.println();

        Map<Long, Map<String, String>> before = app.snapshot();
        Reply reply = app.http.show("PATCH", "/api/orders/4", """
                {
                  "notes": null
                }
                """);
        Map<Long, Map<String, String>> after = app.snapshot();
        Db.printChanges(before, after);
        String notes = after.get(4L).get("notes");
        if (reply.status() != 200) {
            System.out.println("RESULT: NOT THERE YET. PATCH /api/orders/4 answered " + reply.status() + ".");
        } else if (notes.equals(before.get(4L).get("notes"))) {
            System.out.println("RESULT: NOTHING CHANGED. 200 OK, and the note is still there. In this API a null in a PATCH");
            System.out.println("        means \"not sent\", so a PATCH cannot clear a field. A PUT can.");
        } else {
            System.out.println("RESULT: CLEARED. In this API a null in a PATCH means \"remove it\". notes is now " + notes + ".");
        }
    }

    private static void resultOfAddressChange(String method, Reply reply, Map<String, String> before, Map<String, String> after) {
        List<String> lost = new ArrayList<>();
        for (Map.Entry<String, String> column : before.entrySet()) {
            boolean changed = !column.getValue().equals(after.get(column.getKey()));
            if (changed && !column.getKey().equals("shipping_address")) {
                lost.add(column.getKey());
            }
        }
        boolean addressChanged = NEW_ADDRESS.equals(after.get("shipping_address"));
        System.out.println("Fields the client wanted to change:   1  (shippingAddress)");
        System.out.println("Columns that changed in the database: " + (lost.size() + (addressChanged ? 1 : 0)));
        System.out.println();
        if (reply.status() != 200 || !addressChanged) {
            System.out.println("RESULT: NOT THERE YET. " + method + " /api/orders/4 answered " + reply.status()
                    + " and the address did not change.");
        } else if (lost.isEmpty()) {
            System.out.println("RESULT: OK. " + method + " changed the address and nothing else.");
        } else if (method.equals("PUT")) {
            System.out.println("RESULT: DATA LOST. PUT replaced the whole order. Not in the body, changed anyway: " + String.join(", ", lost) + ".");
        } else {
            System.out.println("RESULT: DATA LOST. PATCH changed more than it was sent: " + String.join(", ", lost) + ".");
        }
    }

    // ---------------------------------------------------------------- status codes (Task 10)

    private static void statusCodes(App app) throws Exception {
        System.out.println("Create an order, delete an order, delete it again. What does each status line tell the client?");
        System.out.println();

        Map<Long, Map<String, String>> before = app.snapshot();
        System.out.println("1. Create an order");
        Reply create = app.http.show("POST", "/api/orders", """
                {
                  "customer": "Nina Patel",
                  "product": "MOUSE",
                  "quantity": 2,
                  "shippingAddress": "8 Queen Square, Bath"
                }
                """);
        System.out.println("2. Delete order 6");
        Reply delete = app.http.show("DELETE", "/api/orders/6", null);
        System.out.println("3. Delete order 6 again");
        Reply again = app.http.show("DELETE", "/api/orders/6", null);
        Db.printChanges(before, app.snapshot());

        String location = create.header("Location");
        boolean createOk = create.status() == 201 && location != null;
        boolean deleteOk = delete.status() == 204 && delete.body().isBlank();
        boolean againOk = again.status() == 404;
        System.out.printf("  %-14s got %d, %-24s should be 201 with a Location header   %s%n", "Create:", create.status(),
                location == null ? "no Location header" : "with a Location header", createOk ? "ok" : "WRONG");
        System.out.printf("  %-14s got %d, %-24s should be 204 with no body             %s%n", "Delete:", delete.status(),
                delete.body().isBlank() ? "no body" : "with a body", deleteOk ? "ok" : "WRONG");
        System.out.printf("  %-14s got %d  %-24s should be 404: it is already gone      %s%n", "Delete again:", again.status(),
                "", againOk ? "ok" : "WRONG");
        System.out.println();
        int wrong = (createOk ? 0 : 1) + (deleteOk ? 0 : 1) + (againOk ? 0 : 1);
        if (wrong == 0) {
            System.out.println("RESULT: OK. 201 with a Location, 204 with no body, 404 for what is already gone.");
        } else {
            System.out.println("RESULT: WRONG STATUS CODES. " + wrong + " of 3 answers tell the client the wrong thing.");
        }
    }

    private static void printHelp() {
        System.out.println("Usage: ./demo [--scenario <name>]        (Windows: .\\demo [--scenario <name>])");
        System.out.println();
        System.out.println("Every run starts the application, resets the database to the same six orders,");
        System.out.println("sends real HTTP requests and prints what came back and what changed.");
        System.out.println();
        System.out.println("Scenarios:");
        SCENARIOS.forEach((name, entry) ->
                System.out.printf("  %-16s %-12s %s%n", name, entry.task(), entry.about()));
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  ./demo");
        System.out.println("  ./demo --scenario errors");
        System.out.println("  ./demo --scenario put");
    }

    private Demo() {
    }
}
