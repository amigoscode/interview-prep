package com.amigoscode.pagination;

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

    /** Tasks that have tests. Tasks 7 and 9 are inspection tasks: you answer questions, no code. */
    private static final Map<String, String> TASKS = new LinkedHashMap<>();

    static {
        TASKS.put("baseline", "Baseline: the tools and the HTTP contract (passes from the start, must stay green)");
        TASKS.put("1", "Task 1: See the deep OFFSET");
        TASKS.put("2", "Task 2: Fix the unbounded page size");
        TASKS.put("3", "Task 3: Fix the unstable order");
        TASKS.put("4", "Task 4: Inspect what OFFSET cannot fix");
        TASKS.put("5", "Task 5: Fix it with keyset pagination");
        TASKS.put("6", "Task 6: Fix the cursor: opaque and validated");
        TASKS.put("8", "Task 8: Fix the hidden COUNT(*)");
    }

    public static void main(String[] args) {
        if (args.length > 1 || (args.length == 1 && !args[0].matches("[1-9]"))) {
            System.out.println("Usage: ./check [task]     task is a number from 1 to 9. No number checks everything.");
            System.exit(2);
        }
        if (args.length == 1 && !TASKS.containsKey(args[0])) {
            System.out.println();
            System.out.println("Task " + args[0] + " is an inspection task. There is no code to write and no test to pass:");
            System.out.println("follow its steps in INTERVIEW_TASKS.md and answer the questions.");
            System.out.println();
            return;
        }

        boolean allGreen = true;
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
