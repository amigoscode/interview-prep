package com.amigoscode.threadsafety.demo;

import static com.amigoscode.threadsafety.demo.Tool.get;
import static com.amigoscode.threadsafety.demo.Tool.json;

import com.amigoscode.threadsafety.ThreadSafetyApplication;
import com.amigoscode.threadsafety.demo.BeanReport.Row;
import com.amigoscode.threadsafety.demo.BeanReport.Verdict;
import com.amigoscode.threadsafety.demo.Tool.Reply;
import com.amigoscode.threadsafety.demo.Tool.Run;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Shows what you cannot see in the code: which objects are shared between request threads.
 *
 * 1. BEANS: every bean of the application, its scope, and a verdict for each of its fields.
 * 2. THREADS: it sends a few HTTP requests at the same moment and prints, for each one, the thread
 *    that served it and the identity of the objects that thread used.
 *
 * Run it with ./inspect (or .\inspect on Windows). It starts the application by itself.
 */
public final class Inspect {

    public static void main(String[] args) throws Exception {
        int requests = 8;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--requests") && i + 1 < args.length && args[i + 1].matches("[1-9]|[1-4][0-9]|50")) {
                requests = Integer.parseInt(args[++i]);
            } else {
                System.out.println("Usage: ./inspect [--requests <n>]     (Windows: .\\inspect [--requests <n>])");
                System.out.println();
                System.out.println("  --requests <n>   how many requests to send at the same moment, 1 to 50 (default: 8)");
                return;
            }
        }
        try (ConfigurableApplicationContext context = Tool.startApplication()) {
            beans(context);
            threads(context, requests);
            System.out.println();
        }
        System.exit(0);
    }

    // ---- 1. BEANS -----------------------------------------------------------------------------

    private static void beans(ConfigurableApplicationContext context) {
        List<Row> rows = BeanReport.scan(context, ThreadSafetyApplication.class.getPackageName());

        System.out.println();
        System.out.println("BEANS: every bean of this application, its scope, and what each field means for thread safety");
        System.out.println();
        // Worst first. A bean whose fields are all OK gets one line.
        Map<String, List<Row>> byBean = new LinkedHashMap<>();
        for (Row row : rows) {
            byBean.computeIfAbsent(row.bean(), bean -> new ArrayList<>()).add(row);
        }
        List<List<Row>> ordered = new ArrayList<>(byBean.values());
        ordered.sort(Comparator.comparing((List<Row> bean) -> worst(bean)).reversed());

        List<String[]> table = new ArrayList<>();
        table.add(new String[] {"bean", "scope", "field", "type", "verdict", "why"});
        for (List<Row> bean : ordered) {
            Row first = bean.get(0);
            if (worst(bean) == Verdict.OK) {
                String fields = first.field().startsWith("(") ? "(no fields)" : bean.size() == 1 ? "(1 field)" : "(" + bean.size() + " fields)";
                String why = !first.scope().equals("singleton") ? first.why()
                        : first.field().startsWith("(") ? "stateless" : "stateless: only final references and immutable values";
                table.add(new String[] {first.bean(), first.scope(), fields, "", "OK", why});
                continue;
            }
            for (Row row : bean) {
                table.add(new String[] {row == first ? row.bean() : "", row == first ? row.scope() : "", row.field(),
                        row.type(), row.verdict().name(), row.why()});
            }
        }
        printTable(table);

        Set<String> singletons = new LinkedHashSet<>();
        Set<String> mutable = new LinkedHashSet<>();
        Set<String> careful = new LinkedHashSet<>();
        for (Row row : rows) {
            if (!row.scope().equals("singleton")) {
                continue;
            }
            singletons.add(row.beanClass());
            if (row.verdict() == Verdict.MUTABLE) {
                mutable.add(row.beanClass());
            } else if (row.verdict() == Verdict.CAREFUL) {
                careful.add(row.beanClass());
            }
        }
        careful.removeAll(mutable);
        System.out.println();
        System.out.println("  " + singletons.size() + " singleton beans. Each one is a single object, used by every request thread.");
        System.out.println();
        System.out.println("  MUTABLE (" + mutable.size() + "): " + (mutable.isEmpty() ? "none" : String.join(", ", mutable)));
        System.out.println("      A request thread can change this state while another thread is using it.");
        System.out.println("      Only safe if every read and every write holds the same lock.");
        System.out.println("  CAREFUL (" + careful.size() + "): " + (careful.isEmpty() ? "none" : String.join(", ", careful)));
        System.out.println("      Safe only if each update is ONE call (incrementAndGet, merge, computeIfAbsent).");
        System.out.println();
        System.out.println("  This report reads types, not code. It cannot see a check-then-act, and it cannot see a lock.");
    }

    private static Verdict worst(List<Row> bean) {
        return bean.stream().map(Row::verdict).max(Comparator.naturalOrder()).orElse(Verdict.OK);
    }

    // ---- 2. THREADS ---------------------------------------------------------------------------

    private static void threads(ConfigurableApplicationContext context, int requests) throws Exception {
        HttpClient http = Tool.httpClient();
        String baseUrl = Tool.baseUrl(context);
        Tool.warmUp(http, baseUrl);

        // holdMillis keeps each request open for a moment, so all of them are being served at once.
        Run run = Tool.send(http, requests, requests, i -> get(baseUrl + "/api/whoami?holdMillis=200"));

        System.out.println();
        System.out.println("THREADS: " + requests + " requests to GET /api/whoami at the same moment. Who served each one?");
        System.out.println();
        List<String[]> table = new ArrayList<>();
        table.add(new String[] {"request", "thread", "controller (singleton)", "service (singleton)",
                "request-scoped bean", "prototype bean"});
        Set<String> threads = new LinkedHashSet<>();
        Set<String> controllers = new LinkedHashSet<>();
        Set<String> services = new LinkedHashSet<>();
        Set<String> requestScoped = new LinkedHashSet<>();
        Set<String> prototypes = new LinkedHashSet<>();
        for (Reply reply : run.replies()) {
            if (reply.status() != 200) {
                table.add(new String[] {String.valueOf(reply.index() + 1), "failed: " + reply.status(), "", "", "", ""});
                continue;
            }
            String thread = json(reply.body(), "thread").replaceFirst("^http-nio-.*-(exec-\\d+)$", "$1");
            String controller = json(reply.body(), "controller");
            String service = json(reply.body(), "singletonService");
            String requestBean = json(reply.body(), "requestScopedBean");
            String prototype = json(reply.body(), "prototypeBean");
            threads.add(thread);
            controllers.add(controller);
            services.add(service);
            requestScoped.add(requestBean);
            prototypes.add(prototype);
            table.add(new String[] {String.valueOf(reply.index() + 1), thread, controller, service, requestBean, prototype});
        }
        printTable(table);
        System.out.println();
        System.out.println("  " + requests + " requests were served by " + threads.size() + " different threads.");
        System.out.println("  They all used " + controllers.size() + " WhoAmIController and " + services.size()
                + " VisitCounter: the fields of a singleton are shared by every thread.");
        System.out.println("  They used " + requestScoped.size() + " different RequestInfo objects: a request-scoped bean"
                + " is built once per request.");
        System.out.println("  They all used " + prototypes.size() + " PrototypeHelper: a prototype injected into a singleton"
                + " is built once, with the singleton.");
    }

    // ---- shared -------------------------------------------------------------------------------

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
