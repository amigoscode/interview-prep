package com.amigoscode.transactional.demo;

import com.amigoscode.transactional.TransactionalApplication;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

/** What ./demo and ./inspect have in common: the flags, and starting the real application. */
final class Tool {

    record Options(Scenario scenario, boolean sql) {
    }

    private Tool() {
    }

    /** Returns null when the help was asked for. Throws IllegalArgumentException for a bad flag. */
    static Options parse(String[] args) {
        Scenario scenario = Scenario.TRANSFER;
        boolean sql = false;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--help", "-h" -> {
                    return null;
                }
                case "--scenario" -> {
                    if (++i >= args.length) {
                        throw new IllegalArgumentException("--scenario needs a value: " + Scenario.names());
                    }
                    scenario = Scenario.from(args[i]);
                }
                case "--sql" -> sql = true;
                default -> throw new IllegalArgumentException("Unknown option: " + arg);
            }
        }
        return new Options(scenario, sql);
    }

    static void printHelp(String tool, String what) {
        System.out.println("Usage: ./" + tool + " [options]        (Windows: .\\" + tool + " [options])");
        System.out.println();
        System.out.println(what);
        System.out.println();
        System.out.println("  --scenario <name>   which pitfall to run (default: transfer)");
        System.out.println("  --sql               print every SQL statement as it is sent to the database");
        System.out.println("  --help              this text");
        System.out.println();
        System.out.println("Scenarios:");
        for (Scenario scenario : Scenario.values()) {
            System.out.printf("  %-18s %s%n", scenario.flag, scenario.teaches);
        }
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  ./" + tool);
        System.out.println("  ./" + tool + " --scenario atm");
        System.out.println("  ./" + tool + " --scenario transfer --sql");
    }

    /** Starts the real Spring Boot application, quietly. The caller closes it. */
    static ConfigurableApplicationContext startApplication(boolean sql) {
        List<String> springArgs = new ArrayList<>(List.of("--logging.level.root=WARN"));
        if (sql) {
            springArgs.add("--logging.level.org.hibernate.SQL=DEBUG");
            springArgs.add("--logging.pattern.console=        sql> %msg%n");
        }
        SpringApplication app = new SpringApplication(TransactionalApplication.class);
        app.setBannerMode(Banner.Mode.OFF);
        app.setLogStartupInfo(false);
        return app.run(springArgs.toArray(String[]::new));
    }

    static void printStory(Scenario scenario) {
        System.out.println();
        System.out.println("Scenario: " + scenario.flag + " (" + scenario.teaches + ")");
        scenario.story.forEach(System.out::println);
    }

    static String describe(Scenario.Outcome outcome) {
        if (outcome.thrown() == null) {
            return outcome.returned();
        }
        String message = String.valueOf(outcome.thrown().getMessage()).lines().findFirst().orElse("");
        if (message.length() > 160) {
            message = message.substring(0, 160) + "...";
        }
        return "threw " + outcome.thrown().getClass().getSimpleName() + ": " + message;
    }
}
