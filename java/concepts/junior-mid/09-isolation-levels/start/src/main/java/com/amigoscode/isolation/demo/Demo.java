package com.amigoscode.isolation.demo;

import com.amigoscode.isolation.demo.Outcome.Verdict;
import java.util.ArrayList;
import java.util.List;

/**
 * Two sessions, one fixed order of steps, printed as a timeline that ends in a RESULT line.
 * Without --level it runs the application's own code. With --level it runs the same anomaly as
 * plain SQL at the level you choose. Run it with ./demo (or .\demo on Windows). ./demo --help
 * lists the flags.
 */
public final class Demo {

    static final int PAUSE_SECONDS = 15;

    private record Options(Anomaly anomaly, Level level, boolean matrix, boolean forUpdate, boolean pause) {
    }

    public static void main(String[] args) throws Exception {
        Options options;
        try {
            options = parse(args);
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
            System.out.println();
            printHelp();
            return;
        }
        if (options == null) {
            printHelp();
            return;
        }
        if (!Db.isUp()) {
            return;
        }
        if (options.matrix()) {
            matrix();
        } else if (options.level() != null) {
            sql(options);
        } else {
            AppDemo.run(options.anomaly(), options.pause() ? PAUSE_SECONDS : 0);
        }
    }

    /** One anomaly as plain SQL at one level. */
    private static void sql(Options options) {
        System.out.println();
        System.out.println("Anomaly: " + options.anomaly().flag() + " | level: " + options.level().sql()
                + (options.forUpdate() ? " + SELECT ... FOR UPDATE" : "") + " | plain SQL in two sessions");
        System.out.println(story(options.anomaly()));
        System.out.println("Before: alice has two accounts, current (id 1) = 500 and savings (id 2) = 500.");
        System.out.println();
        Lab lab = new Lab(Db.URL, Db.USER, Db.PASSWORD, System.out);
        Outcome outcome = lab.run(options.anomaly(), options.level(),
                new Lab.Options(options.forUpdate(), options.pause() ? PAUSE_SECONDS : 0));
        System.out.println();
        System.out.println("RESULT: " + outcome.result());
        System.out.println();
    }

    static String story(Anomaly anomaly) {
        return switch (anomaly) {
            case DIRTY_READ -> "Session B changes a balance and has NOT committed. Session A reads that balance.";
            case NON_REPEATABLE_READ -> "Session A reads one balance twice. Session B deposits 100 and commits in between.";
            case PHANTOM -> "Session A counts alice's accounts twice. Session B opens a new account and commits in between.";
            case LOST_UPDATE -> "Both sessions withdraw 100 from account 1: read the balance, subtract, write it back.";
            case WRITE_SKEW -> "A withdraws 800 from account 1, B withdraws 800 from account 2. "
                    + "The rule: both accounts together must stay at 0 or above.";
        };
    }

    /** Every anomaly at every level. */
    private static void matrix() {
        Lab lab = new Lab(Db.URL, Db.USER, Db.PASSWORD, null);
        System.out.println();
        System.out.println("Every anomaly at every isolation level: plain SQL in two sessions, "
                + Anomaly.values().length * Level.values().length + " runs.");
        System.out.println();
        StringBuilder header = new StringBuilder(String.format("  %-21s", ""));
        for (Level level : Level.values()) {
            header.append(String.format("%-18s", level.flag()));
        }
        System.out.println(header.toString().stripTrailing());
        List<List<String>> columns = new ArrayList<>();
        for (Level ignored : Level.values()) {
            columns.add(new ArrayList<>());
        }
        for (Anomaly anomaly : Anomaly.values()) {
            StringBuilder row = new StringBuilder(String.format("  %-21s", anomaly.flag()));
            for (Level level : Level.values()) {
                Outcome outcome = lab.run(anomaly, level);
                String cell = outcome.verdict() == Verdict.HAPPENED ? "HAPPENS"
                        : outcome.verdict() == Verdict.SERIALIZATION_FAILURE ? outcome.sqlState() : "no";
                columns.get(level.ordinal()).add(cell);
                row.append(String.format("%-18s", cell));
            }
            System.out.println(row.toString().stripTrailing());
        }
        System.out.println();
        System.out.println("  HAPPENS = the anomaly happened and nobody was told");
        System.out.println("  no      = it did not happen");
        System.out.println("  40001   = it did not happen, because Postgres cancelled one transaction with");
        System.out.println("            SQLSTATE 40001 (serialization failure). The application has to retry it");
        System.out.println();
        boolean sameAsReadCommitted = columns.get(Level.READ_UNCOMMITTED.ordinal())
                .equals(columns.get(Level.READ_COMMITTED.ordinal()));
        List<String> clean = new ArrayList<>();
        for (Level level : Level.values()) {
            if (!columns.get(level.ordinal()).contains("HAPPENS")) {
                clean.add(level.flag());
            }
        }
        System.out.println("RESULT: read-uncommitted and read-committed "
                + (sameAsReadCommitted ? "behave the same" : "DIFFER") + ". Levels with no anomaly left: "
                + (clean.isEmpty() ? "none" : String.join(", ", clean)) + ".");
        System.out.println();
    }

    private static Options parse(String[] args) {
        Anomaly anomaly = Anomaly.NON_REPEATABLE_READ;
        Level level = null;
        boolean matrix = false;
        boolean forUpdate = false;
        boolean pause = false;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--help", "-h" -> {
                    return null;
                }
                case "--anomaly" -> anomaly = Anomaly.from(value(args, ++i, arg));
                case "--level" -> level = Level.from(value(args, ++i, arg));
                case "--matrix" -> matrix = true;
                case "--for-update" -> forUpdate = true;
                case "--pause" -> pause = true;
                default -> throw new IllegalArgumentException("Unknown option: " + arg);
            }
        }
        if (forUpdate && anomaly != Anomaly.WRITE_SKEW) {
            throw new IllegalArgumentException("--for-update only works with --anomaly write-skew");
        }
        if (level == null && forUpdate) {
            level = Level.READ_COMMITTED;
        }
        if (level == null && anomaly == Anomaly.DIRTY_READ) {
            // The application has no code that could show a dirty read, so this one is always plain SQL.
            level = Level.READ_UNCOMMITTED;
        }
        return new Options(anomaly, level, matrix, forUpdate, pause);
    }

    private static String value(String[] args, int index, String option) {
        if (index >= args.length) {
            throw new IllegalArgumentException(option + " needs a value");
        }
        return args[index];
    }

    private static void printHelp() {
        System.out.println("Usage: ./demo [options]        (Windows: .\\demo [options])");
        System.out.println();
        System.out.println("  --anomaly <name>   " + Anomaly.names());
        System.out.println("                     (default: non-repeatable-read)");
        System.out.println("  --level <name>     " + Level.names());
        System.out.println("                     Runs the anomaly as plain SQL at this level.");
        System.out.println("                     Without --level the demo runs the APPLICATION's code, so it shows");
        System.out.println("                     what your @Transactional settings do. (dirty-read is always plain SQL.)");
        System.out.println("  --for-update       write-skew only, plain SQL: the read locks the rows (SELECT ... FOR UPDATE)");
        System.out.println("  --matrix           every anomaly at every level, as one table");
        System.out.println("  --pause            hold the transactions open for " + PAUSE_SECONDS
                + " seconds in the middle, so you can run ./inspect");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  ./demo");
        System.out.println("  ./demo --anomaly phantom");
        System.out.println("  ./demo --anomaly write-skew --level serializable");
        System.out.println("  ./demo --anomaly dirty-read --level read-uncommitted");
        System.out.println("  ./demo --matrix");
    }

    private Demo() {
    }
}
