package com.amigoscode.transactional.demo;

import static com.amigoscode.transactional.demo.Bank.ALICE;
import static com.amigoscode.transactional.demo.Bank.BOB;
import static com.amigoscode.transactional.demo.Bank.CAROL;

import com.amigoscode.transactional.demo.Scenario.Outcome;
import com.amigoscode.transactional.fee.FeeService;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Starts the real application, puts the bank back to its starting state, runs one scenario and
 * prints what is in the database before and after. Run it with ./demo (or .\demo on Windows).
 * ./demo --help lists the scenarios.
 */
public final class Demo {

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
            run(options.scenario(), context);
        }
    }

    private static void run(Scenario scenario, ConfigurableApplicationContext context) {
        JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
        Bank.reset(jdbc);
        long aliceBefore = Bank.balance(jdbc, ALICE);
        long totalBefore = Bank.total(jdbc);

        Tool.printStory(scenario);
        System.out.println();
        System.out.println("ACCOUNTS BEFORE");
        Bank.printAccounts(jdbc);
        System.out.println();
        System.out.println("CALL");
        System.out.println("  " + scenario.call);

        Outcome outcome = scenario.run(context);

        System.out.println("  " + Tool.describe(outcome));
        if (outcome.table() != null) {
            System.out.println();
            Bank.printRows(outcome.table());
        }
        System.out.println();
        System.out.println("ACCOUNTS AFTER");
        Bank.printAccounts(jdbc);
        System.out.println();
        System.out.println("AUDIT LOG");
        Bank.printAuditLog(jdbc);
        System.out.println();
        System.out.println("RESULT: " + verdict(scenario, outcome, jdbc, aliceBefore, totalBefore));
        System.out.println();
    }

    private static String verdict(Scenario scenario, Outcome outcome, JdbcTemplate jdbc, long aliceBefore, long totalBefore) {
        long alice = Bank.balance(jdbc, ALICE);
        long carol = Bank.balance(jdbc, CAROL);
        long missing = totalBefore - Bank.total(jdbc);
        boolean failed = outcome.thrown() != null || String.valueOf(outcome.returned()).startsWith("returned false");
        return switch (scenario) {
            case TRANSFER, REQUIRES_NEW_TRAP -> moneyVerdict("Transfer", failed, alice, carol, missing);
            case BILL -> moneyVerdict("Bill payment", failed, alice, carol, missing);
            case STANDING_ORDER -> moneyVerdict("Standing order", failed, alice, carol, missing);
            case PAYROLL -> {
                long bob = Bank.balance(jdbc, BOB);
                if (outcome.thrown() != null && alice == aliceBefore && bob == 0) {
                    yield "NOBODY PAID. payEveryone caught the exception and carried on, but the commit was refused: "
                            + outcome.thrown().getClass().getSimpleName() + ". Alice: " + alice + ". Bob: " + bob + ".";
                }
                yield "Alice: " + alice + ". Bob: " + bob + ". " + Tool.describe(outcome) + ".";
            }
            case ATM -> {
                int auditRows = Bank.auditRows(jdbc);
                if (!failed || alice != aliceBefore) {
                    yield "UNEXPECTED. The withdrawal should have been refused. Alice: " + alice + ".";
                }
                yield auditRows == 0
                        ? "AUDIT ROW LOST. The withdrawal was refused and rolled back, and the audit row was rolled back "
                                + "with it. Audit rows: 0."
                        : "OK. The withdrawal was refused and rolled back. The audit row survived. Audit rows: " + auditRows + ".";
            }
            case FEE -> alice == aliceBefore - FeeService.MONTHLY_FEE && !failed
                    ? "OK. The fee was charged. Alice: " + alice + "."
                    : "FEE NOT CHARGED. chargeMonthlyFee "
                            + (outcome.thrown() == null ? "returned normally" : "threw " + outcome.thrown().getClass().getSimpleName())
                            + ". Alice: " + alice + ".";
            case RENAME -> {
                String owner = Bank.owner(jdbc, ALICE);
                yield owner.equals("Alice Smith")
                        ? "OK. The owner is now \"" + owner + "\"."
                        : "CHANGE LOST. rename " + Tool.describe(outcome) + ", but the owner is still \"" + owner + "\".";
            }
            case VISIBILITY -> {
                long inTransaction = outcome.table().stream().filter(row -> row[2].equals("yes")).count();
                yield inTransaction + " of 5 methods ran in a transaction. The other "
                        + (5 - inTransaction) + " carry @Transactional too, and it did nothing.";
            }
        };
    }

    private static String moneyVerdict(String what, boolean failed, long sender, long receiver, long missing) {
        String balances = "Sender balance: " + sender + ". Receiver balance: " + receiver + ". ";
        if (!failed) {
            return "UNEXPECTED. " + what + " should have failed. " + balances;
        }
        return missing == 0
                ? "OK. " + what + " failed and was rolled back. " + balances + "Nothing is missing."
                : "MONEY LOST. " + what + " failed. " + balances + missing + " is missing.";
    }

    private static void printHelp() {
        Tool.printHelp("demo", "Runs one scenario against the real application and shows the accounts before and after.");
    }

    private Demo() {
    }
}
