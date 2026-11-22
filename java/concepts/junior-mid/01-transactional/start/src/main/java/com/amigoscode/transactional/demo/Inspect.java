package com.amigoscode.transactional.demo;

import com.amigoscode.transactional.TransactionalApplication;
import com.amigoscode.transactional.demo.Scenario.Outcome;
import com.amigoscode.transactional.trace.TransactionTrace;
import java.util.ArrayList;
import java.util.List;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.repository.Repository;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Makes the invisible visible. Starts the real application and shows (1) which beans Spring
 * wrapped in a proxy, and (2) for one scenario, every transaction that was really opened,
 * committed or rolled back, and whether each service method ran inside one.
 * Run it with ./inspect (or .\inspect on Windows). ./inspect --help lists the scenarios.
 */
public final class Inspect {

    private static final String BASE_PACKAGE = TransactionalApplication.class.getPackageName();

    public static void main(String[] args) {
        Tool.Options options;
        try {
            options = Tool.parse(args);
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
        try (ConfigurableApplicationContext context = Tool.startApplication(options.sql())) {
            printBeans(context);
            printTimeline(options.scenario(), context);
        }
    }

    private static void printBeans(ConfigurableApplicationContext context) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {"bean", "proxy?", "class of the object Spring hands out", "the class behind it"});
        for (String name : context.getBeanDefinitionNames()) {
            Object bean = context.getBean(name);
            Class<?> real = AopUtils.getTargetClass(bean);
            boolean ours = real.getPackageName().startsWith(BASE_PACKAGE + ".") && !real.getPackageName().endsWith(".trace");
            if (!ours && !(bean instanceof Repository)) {
                continue;
            }
            String proxy = AopUtils.isCglibProxy(bean) ? "yes, subclass" : AopUtils.isJdkDynamicProxy(bean) ? "yes, interface" : "no";
            rows.add(new String[] {name, proxy, bean.getClass().getSimpleName(), real.getSimpleName()});
        }
        System.out.println();
        System.out.println("BEANS");
        Bank.printRows(rows);
        System.out.println();
        System.out.println("  proxy? = yes: other beans do not hold your object. They hold a wrapper Spring generated,");
        System.out.println("  and the wrapper opens and closes the transaction around each call that goes through it.");
    }

    private static void printTimeline(Scenario scenario, ConfigurableApplicationContext context) {
        JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
        Bank.reset(jdbc);

        Tool.printStory(scenario);
        System.out.println();
        System.out.println("TIMELINE of " + scenario.call);
        TransactionTrace.clear();
        TransactionTrace.printAsItHappens(true);
        Outcome outcome = scenario.run(context);
        TransactionTrace.printAsItHappens(false);
        System.out.println("  " + Tool.describe(outcome));
        if (outcome.table() != null) {
            System.out.println();
            Bank.printRows(outcome.table());
        }
        System.out.println();
        System.out.println("  BEGIN / COMMIT / ROLLBACK = a real database transaction, named after the method that opened it.");
        System.out.println("  inside X   transaction: Y = method X was entered while transaction Y was open (NONE = no transaction).");
        System.out.println();
        System.out.println("ACCOUNTS AFTER");
        Bank.printAccounts(jdbc);
        System.out.println();
        System.out.println("AUDIT LOG");
        Bank.printAuditLog(jdbc);
        System.out.println();
    }

    private static void printHelp() {
        Tool.printHelp("inspect", "Shows which beans are proxies, then runs one scenario and prints every transaction it opened.");
    }

    private Inspect() {
    }
}
