package com.amigoscode.transactional;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectPackage;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.discovery.ClassNameFilter;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.TagFilter;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

/**
 * Runs the tests that belong to one task and prints one line per acceptance criterion.
 * ./check 2 checks task 2, ./check checks everything. On Windows: .\check 2
 * (./mvnw verify runs the same tests the usual Maven way.)
 */
public final class Check {

    /** Tasks that have tests. Tasks 1 and 10 have none: you run things and answer questions. */
    private static final Map<String, String> TASKS = new LinkedHashMap<>();

    static {
        TASKS.put("baseline", "Baseline: the rules of an account (passes from the start, must stay green)");
        TASKS.put("2", "Task 2: Fix the self-invocation");
        TASKS.put("3", "Task 3: Inspect the proxy (passes from the start: it documents how Spring behaves)");
        TASKS.put("4", "Task 4: Fix the rollback rules");
        TASKS.put("5", "Task 5: Inspect commit and rollback (passes from the start: it documents how Spring behaves)");
        TASKS.put("6", "Task 6: Fix the propagation");
        TASKS.put("7", "Task 7: Inspect REQUIRED and REQUIRES_NEW (passes from the start: it documents how Spring behaves)");
        TASKS.put("8", "Task 8: Fix the annotations that do nothing");
        TASKS.put("9", "Task 9: Inspect what a proxy cannot do (passes from the start: it documents how Spring behaves)");
    }

    public static void main(String[] args) {
        if (args.length > 1 || (args.length == 1 && !args[0].matches("[1-9]|10"))) {
            System.out.println("Usage: ./check [task]     task is a number from 1 to 10. No number checks everything.");
            System.exit(2);
        }
        if (args.length == 1 && !TASKS.containsKey(args[0])) {
            System.out.println();
            System.out.println("Task " + args[0] + " has no tests. There is no code to write:");
            System.out.println("follow its steps in INTERVIEW_TASKS.md and answer the questions.");
            System.out.println();
            return;
        }

        boolean allGreen = true;
        int passed = 0;
        int total = 0;
        Launcher launcher = LauncherFactory.create();
        for (Map.Entry<String, String> task : TASKS.entrySet()) {
            if (args.length == 1 && !task.getKey().equals(args[0])) {
                continue;
            }
            String tag = task.getKey().equals("baseline") ? "baseline" : "task" + task.getKey();
            LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                    .selectors(selectPackage(Check.class.getPackageName()))
                    .filters(ClassNameFilter.includeClassNamePatterns(".*"), TagFilter.includeTags(tag))
                    .build();
            System.out.println();
            System.out.println(task.getValue());
            Results results = new Results();
            launcher.execute(request, results);
            System.out.println("  " + results.passed + " of " + results.total + " pass");
            allGreen &= results.passed == results.total && results.total > 0;
            passed += results.passed;
            total += results.total;
        }
        if (args.length == 0) {
            System.out.println();
            System.out.println("All tasks: " + passed + " of " + total + " pass");
        }
        System.out.println();
        System.exit(allGreen ? 0 : 1);
    }

    private static final class Results implements TestExecutionListener {

        private int total;
        private int passed;

        @Override
        public void executionFinished(TestIdentifier test, TestExecutionResult result) {
            boolean ok = result.getStatus() == TestExecutionResult.Status.SUCCESSFUL;
            if (!test.isTest()) {
                // A container that fails (for example the Spring context cannot start) takes its tests with it.
                if (!ok) {
                    total++;
                    System.out.println("  [FAIL] " + test.getDisplayName() + " could not start");
                    result.getThrowable().ifPresent(Results::printReason);
                }
                return;
            }
            total++;
            if (ok) {
                passed++;
                System.out.println("  [PASS] " + test.getDisplayName());
            } else {
                System.out.println("  [FAIL] " + test.getDisplayName());
                result.getThrowable().ifPresent(Results::printReason);
            }
        }

        private static void printReason(Throwable throwable) {
            Throwable root = throwable;
            while (root.getCause() != null && root.getCause() != root && root.getMessage() == null) {
                root = root.getCause();
            }
            String message = root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
            message.strip().lines()
                    .map(String::strip)
                    .filter(line -> !line.isEmpty())
                    .limit(6)
                    .forEach(line -> System.out.println("           " + line));
        }
    }

    private Check() {
    }
}
