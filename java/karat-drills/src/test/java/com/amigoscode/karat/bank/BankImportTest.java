package com.amigoscode.karat.bank;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * This is not a complete test suite, but tests some basic functionality of the code and shows how
 * to use it. One test fails on purpose: that is part 1.
 */
@Disabled("bank drill: remove this line to begin part 1")
class BankImportTest {

    @Test
    void transaction() {
        Transaction t = new Transaction("t001", 101, 500.00, TransactionType.DEPOSIT, 1, "Salary", "SRC_A");

        assertThat(t.transactionId).isEqualTo("t001");
        assertThat(t.accountId).isEqualTo(101);
        assertThat(t.amount).isCloseTo(500.00, within(1e-4));
        assertThat(t.transactionType).isEqualTo(TransactionType.DEPOSIT);
        assertThat(t.timestamp).isEqualTo(1);
        assertThat(t.description).isEqualTo("Salary");
        assertThat(t.source).isEqualTo("SRC_A");
    }

    @Test
    void accountSummary() {
        ImportSystem system = new ImportSystem();
        // Account 101: 6 transactions covering all 5 types
        system.addTransaction(new Transaction("t001", 101, 500.00, TransactionType.DEPOSIT, 1, "Salary", "SRC_A"));
        system.addTransaction(new Transaction("t002", 101, 300.00, TransactionType.DEPOSIT, 2, "Refund", "SRC_A"));
        system.addTransaction(new Transaction("t003", 101, 150.00, TransactionType.WITHDRAWAL, 3, "ATM", "SRC_A"));
        system.addTransaction(new Transaction("t004", 101, 50.00, TransactionType.FEE, 4, "Service fee", "SRC_A"));
        system.addTransaction(new Transaction("t005", 101, 200.00, TransactionType.TRANSFER, 5, "Wire out", "SRC_A"));
        system.addTransaction(new Transaction("t006", 101, 20.00, TransactionType.INTEREST, 6, "Interest paid", "SRC_A"));
        // Account 202: 2 transactions to verify per-account filtering
        system.addTransaction(new Transaction("t007", 202, 100.00, TransactionType.DEPOSIT, 7, "Deposit", "SRC_A"));
        system.addTransaction(new Transaction("t008", 202, 30.00, TransactionType.WITHDRAWAL, 8, "Withdrawal", "SRC_A"));

        // Expected: credits 820, debits 400, net 420
        Map<String, Object> summary101 = system.getAccountSummary(101);
        assertThat((double) summary101.get("total_credits")).isCloseTo(820.00, within(1e-4));
        assertThat((double) summary101.get("total_debits")).isCloseTo(400.00, within(1e-4));
        assertThat((double) summary101.get("net_change")).isCloseTo(420.00, within(1e-4));
        assertThat((int) summary101.get("transaction_count")).isEqualTo(6);

        // Expected: credits 100, debits 30, net 70
        Map<String, Object> summary202 = system.getAccountSummary(202);
        assertThat((double) summary202.get("total_credits")).isCloseTo(100.00, within(1e-4));
        assertThat((double) summary202.get("total_debits")).isCloseTo(30.00, within(1e-4));
        assertThat((double) summary202.get("net_change")).isCloseTo(70.00, within(1e-4));
        assertThat((int) summary202.get("transaction_count")).isEqualTo(2);
    }

    @Test
    @Disabled("part 2: remove this line when part 1 is green")
    void addTransaction() {
        ImportSystem system = new ImportSystem();
        assertThat(system.balances).isEmpty();

        // A first deposit to a new account.
        system.addTransaction(new Transaction("t001", 101, 500.00, TransactionType.DEPOSIT, 1, "Salary", "SRC_A"));
        assertThat(system.transactions).hasSize(1);
        assertThat(system.balances.get(101)).isCloseTo(500.00, within(1e-4));

        // A cash withdrawal from the same account.
        system.addTransaction(new Transaction("t002", 101, 150.00, TransactionType.WITHDRAWAL, 2, "ATM", "SRC_A"));
        assertThat(system.transactions).hasSize(2);
        assertThat(system.balances.get(101)).isCloseTo(350.00, within(1e-4));

        // A service fee charged to the same account.
        system.addTransaction(new Transaction("t003", 101, 50.00, TransactionType.FEE, 3, "Service fee", "SRC_A"));
        assertThat(system.transactions).hasSize(3);
        assertThat(system.balances.get(101)).isCloseTo(300.00, within(1e-4));

        // Monthly interest credited to the same account.
        system.addTransaction(new Transaction("t004", 101, 20.00, TransactionType.INTEREST, 4, "Interest paid", "SRC_A"));
        assertThat(system.transactions).hasSize(4);
        assertThat(system.balances.get(101)).isCloseTo(320.00, within(1e-4));

        // A deposit to a different, brand-new account.
        system.addTransaction(new Transaction("t005", 202, 200.00, TransactionType.DEPOSIT, 5, "Deposit", "SRC_A"));
        assertThat(system.transactions).hasSize(5);
        assertThat(system.balances.get(202)).isCloseTo(200.00, within(1e-4));
        assertThat(system.balances.get(101)).isCloseTo(320.00, within(1e-4));

        // A wire transfer out of the second account.
        system.addTransaction(new Transaction("t006", 202, 50.00, TransactionType.TRANSFER, 6, "Wire", "SRC_A"));
        assertThat(system.transactions).hasSize(6);
        assertThat(system.balances.get(202)).isCloseTo(150.00, within(1e-4));

        // A brand-new account whose first transaction is a debit.
        system.addTransaction(new Transaction("t007", 303, 40.00, TransactionType.FEE, 7, "Setup fee", "SRC_A"));
        assertThat(system.transactions).hasSize(7);
        assertThat(system.balances.get(303)).isCloseTo(-40.00, within(1e-4));
    }

    @Test
    @Disabled("part 2: remove this line when part 1 is green")
    void typeSummary() {
        ImportSystem system = new ImportSystem();
        // A mix of transactions across two accounts covering all five types.
        system.addTransaction(new Transaction("t001", 101, 500.00, TransactionType.DEPOSIT, 1, "Salary", "SRC_A"));
        system.addTransaction(new Transaction("t002", 101, 300.00, TransactionType.DEPOSIT, 2, "Refund", "SRC_A"));
        system.addTransaction(new Transaction("t003", 202, 800.00, TransactionType.DEPOSIT, 3, "Big deposit", "SRC_A"));
        system.addTransaction(new Transaction("t004", 101, 150.00, TransactionType.WITHDRAWAL, 4, "ATM", "SRC_A"));
        system.addTransaction(new Transaction("t005", 202, 50.00, TransactionType.WITHDRAWAL, 5, "ATM", "SRC_A"));
        system.addTransaction(new Transaction("t006", 101, 50.00, TransactionType.FEE, 6, "Service fee", "SRC_A"));
        system.addTransaction(new Transaction("t007", 202, 200.00, TransactionType.TRANSFER, 7, "Wire", "SRC_A"));
        system.addTransaction(new Transaction("t008", 101, 20.00, TransactionType.INTEREST, 8, "Interest", "SRC_A"));

        // "count" is an Integer and "total_amount" a Double: the maps are compared with equals.
        Map<String, Map<String, Object>> expected = Map.of(
                "DEPOSIT", Map.of("count", 3, "total_amount", 1600.00),
                "WITHDRAWAL", Map.of("count", 2, "total_amount", 200.00),
                "FEE", Map.of("count", 1, "total_amount", 50.00),
                "TRANSFER", Map.of("count", 1, "total_amount", 200.00),
                "INTEREST", Map.of("count", 1, "total_amount", 20.00));
        assertThat(system.getTypeSummary()).containsExactlyInAnyOrderEntriesOf(expected);

        // A separate system where only one transaction type is used.
        ImportSystem system2 = new ImportSystem();
        system2.addTransaction(new Transaction("t100", 300, 100.00, TransactionType.DEPOSIT, 1, "First", "SRC_A"));
        system2.addTransaction(new Transaction("t101", 300, 200.00, TransactionType.DEPOSIT, 2, "Second", "SRC_A"));

        Map<String, Map<String, Object>> expected2 = Map.of("DEPOSIT", Map.of("count", 2, "total_amount", 300.00));
        assertThat(system2.getTypeSummary()).containsExactlyInAnyOrderEntriesOf(expected2);
    }
}
