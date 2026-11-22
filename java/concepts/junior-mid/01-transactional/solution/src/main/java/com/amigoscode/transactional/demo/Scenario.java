package com.amigoscode.transactional.demo;

import static com.amigoscode.transactional.demo.Bank.ACME;
import static com.amigoscode.transactional.demo.Bank.ALICE;
import static com.amigoscode.transactional.demo.Bank.BOB;
import static com.amigoscode.transactional.demo.Bank.CAROL;

import com.amigoscode.transactional.account.AccountService;
import com.amigoscode.transactional.atm.AtmService;
import com.amigoscode.transactional.bill.BillService;
import com.amigoscode.transactional.exhibit.VisibilityCaller;
import com.amigoscode.transactional.exhibit.VisibilityExhibit.Observation;
import com.amigoscode.transactional.fee.FeeService;
import com.amigoscode.transactional.payroll.PayrollService;
import com.amigoscode.transactional.standingorder.StandingOrderJob;
import com.amigoscode.transactional.transfer.TransferService;
import com.amigoscode.transactional.trap.SplitTransferService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationContext;

/** One scenario per pitfall. ./demo and ./inspect both take --scenario <name>. */
enum Scenario {

    TRANSFER("transfer", "Tasks 1 to 3: self-invocation",
            "transferService.transfer(ALICE, CAROL, 50)",
            "Alice sends 50 to Carol. Carol's account is frozen, so the deposit fails",
            "after the money has left Alice's account."),

    BILL("bill", "Tasks 4 and 5: a checked exception",
            "billService.payBill(ALICE, CAROL, 50)",
            "Alice pays a bill of 50 to Carol. Carol's account is frozen, so the payment is",
            "rejected with BillRejectedException, a CHECKED exception, after Alice was debited."),

    STANDING_ORDER("standing-order", "Tasks 4 and 5: a swallowed exception",
            "standingOrderJob.run(ALICE, CAROL, 50)",
            "The nightly job runs Alice's standing order of 50 to Carol. Carol's account is",
            "frozen. The job must not crash, so the failure is caught and reported as false."),

    PAYROLL("payroll", "Task 5: rollback-only",
            "payrollService.payEveryone(ACME, [ALICE, BOB, CAROL], 100)",
            "Acme Ltd pays a salary of 100 to Alice, Bob and Carol. Carol's account is frozen.",
            "payEveryone catches that exception and carries on, so Alice and Bob should be paid."),

    ATM("atm", "Tasks 6 and 7: propagation",
            "atmService.withdraw(ALICE, 500)",
            "Alice tries to withdraw 500 from a cash machine. She only has 100, so it is refused.",
            "Security wants every attempt in the audit log, including the refused ones."),

    REQUIRES_NEW_TRAP("requires-new-trap", "Task 7: REQUIRES_NEW in the wrong place",
            "splitTransferService.transfer(ALICE, CAROL, 50)",
            "Alice sends 50 to Carol again, with a different service. Every method involved is",
            "@Transactional and goes through a proxy. The debit and the credit are REQUIRES_NEW."),

    FEE("fee", "Tasks 8 and 9: a method the proxy cannot override",
            "feeService.chargeMonthlyFee(ALICE)",
            "The bank charges Alice the monthly fee of 5."),

    RENAME("rename", "Tasks 8 and 9: readOnly = true",
            "accountService.rename(ALICE, \"Alice Smith\")",
            "Alice got married. Her account is renamed to \"Alice Smith\"."),

    VISIBILITY("visibility", "Task 9: which methods can be transactional",
            "five @Transactional methods of VisibilityExhibit, called from another bean",
            "Five methods with the same body and the same @Transactional. Only the modifiers differ:",
            "public, protected, package-private, public final, private.");

    /** What a scenario did: a short description of the return value, or the exception, plus any lines to print. */
    record Outcome(String returned, Throwable thrown, List<String[]> table) {
    }

    final String flag;
    final String teaches;
    final String call;
    final List<String> story;

    Scenario(String flag, String teaches, String call, String... story) {
        this.flag = flag;
        this.teaches = teaches;
        this.call = call;
        this.story = List.of(story);
    }

    static Scenario from(String flag) {
        return Arrays.stream(values())
                .filter(scenario -> scenario.flag.equalsIgnoreCase(flag))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown scenario: " + flag + ". Use one of: " + names()));
    }

    static String names() {
        return Arrays.stream(values()).map(scenario -> scenario.flag).collect(Collectors.joining(" | "));
    }

    /** Calls the real beans, exactly as another class in the application would. */
    Outcome run(ApplicationContext beans) {
        try {
            switch (this) {
                case TRANSFER -> beans.getBean(TransferService.class).transfer(ALICE, CAROL, 50);
                case BILL -> beans.getBean(BillService.class).payBill(ALICE, CAROL, 50);
                case STANDING_ORDER -> {
                    boolean paid = beans.getBean(StandingOrderJob.class).run(ALICE, CAROL, 50);
                    return new Outcome("returned " + paid + (paid ? "" : " (the payment failed)"), null, null);
                }
                case PAYROLL -> {
                    int paid = beans.getBean(PayrollService.class).payEveryone(ACME, List.of(ALICE, BOB, CAROL), 100);
                    return new Outcome("returned " + paid + " (employees paid)", null, null);
                }
                case ATM -> beans.getBean(AtmService.class).withdraw(ALICE, 500);
                case REQUIRES_NEW_TRAP -> beans.getBean(SplitTransferService.class).transfer(ALICE, CAROL, 50);
                case FEE -> beans.getBean(FeeService.class).chargeMonthlyFee(ALICE);
                case RENAME -> beans.getBean(AccountService.class).rename(ALICE, "Alice Smith");
                case VISIBILITY -> {
                    return new Outcome("returned normally", null, visibility(beans.getBean(VisibilityCaller.class)));
                }
            }
            return new Outcome("returned normally", null, null);
        } catch (Exception e) {
            return new Outcome(null, e, null);
        }
    }

    private static List<String[]> visibility(VisibilityCaller caller) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {"method", "how the call arrives", "transaction active", "injected fields"});
        rows.add(row("public", "through the proxy", caller.callPublic()));
        rows.add(row("protected", "through the proxy", caller.callProtected()));
        rows.add(row("package-private", "through the proxy", caller.callPackagePrivate()));
        rows.add(row("public final", "runs on the proxy object itself", caller.callFinal()));
        rows.add(row("private", "this.privateMethod()", caller.callPrivate()));
        return rows;
    }

    private static String[] row(String method, String how, Observation seen) {
        return new String[] {method, how, seen.transactionActive() ? "yes" : "NO", seen.fieldsInjected() ? "set" : "NULL"};
    }
}
