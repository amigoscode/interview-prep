package com.amigoscode.karat.payments;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * This is not a complete test suite, but tests some basic functionality of the code and shows how
 * to use it. One test fails on purpose: that is part 1.
 */
@Disabled("payments drill: remove this line to begin part 1")
class AccountManagerTest {

    @Test
    void getBalanceBasic() {
        AccountManager mgr = new AccountManager();
        mgr.addAccount(new Account(1, "Alice"));

        mgr.addTransaction(new Transaction(101, 1, TransactionType.CREDIT, 100.0, 1000));
        mgr.addTransaction(new Transaction(102, 1, TransactionType.DEBIT, 30.0, 1010));
        mgr.addTransaction(new Transaction(103, 1, TransactionType.DEBIT, 20.0, 1020));
        mgr.addTransaction(new Transaction(104, 1, TransactionType.CREDIT, 10.0, 1030));

        // Expected balance: 100 - 30 - 20 + 10 = 60
        assertThat(mgr.getBalance(1)).isCloseTo(60.0, within(1e-4));

        mgr.addTransaction(new Transaction(105, 1, TransactionType.DEBIT, 70.0, 1045));
        assertThat(mgr.getBalance(1)).isCloseTo(-10.0, within(1e-4));
    }

    @Test
    void getBalanceMultipleAccounts() {
        AccountManager mgr = new AccountManager();
        mgr.addAccount(new Account(1, "Alice"));
        mgr.addAccount(new Account(2, "Bob"));

        mgr.addTransaction(new Transaction(201, 1, TransactionType.CREDIT, 50.0, 2000));
        mgr.addTransaction(new Transaction(202, 2, TransactionType.CREDIT, 80.0, 2005));
        mgr.addTransaction(new Transaction(203, 1, TransactionType.DEBIT, 10.0, 2010));
        mgr.addTransaction(new Transaction(204, 2, TransactionType.DEBIT, 5.5, 2015));
        mgr.addTransaction(new Transaction(205, 2, TransactionType.DEBIT, 14.5, 2020));

        // Account 1: 50 - 10 = 40
        assertThat(mgr.getBalance(1)).isCloseTo(40.0, within(1e-4));
        // Account 2: 80 - 5.5 - 14.5 = 60
        assertThat(mgr.getBalance(2)).isCloseTo(60.0, within(1e-4));
    }

    @Test
    @Disabled("part 2: remove this line when part 1 is green")
    void averageTransactionAmountByAccount() {
        AccountManager mgr = new AccountManager();
        mgr.addAccount(new Account(1, "Alice"));
        mgr.addAccount(new Account(2, "Bob"));
        mgr.addAccount(new Account(3, "Charlie")); // no transactions

        // Account 1: 100, 30, 20, 10 => avg = 160/4 = 40
        mgr.addTransaction(new Transaction(101, 1, TransactionType.CREDIT, 100.0, 1000));
        mgr.addTransaction(new Transaction(102, 1, TransactionType.DEBIT, 30.0, 1010));
        mgr.addTransaction(new Transaction(103, 1, TransactionType.DEBIT, 20.0, 1020));
        mgr.addTransaction(new Transaction(104, 1, TransactionType.CREDIT, 10.0, 1030));

        // Account 2: 80, 5.5, 14.5 => avg = 100/3 = 33.333...
        mgr.addTransaction(new Transaction(201, 2, TransactionType.CREDIT, 80.0, 2005));
        mgr.addTransaction(new Transaction(202, 2, TransactionType.DEBIT, 5.5, 2015));
        mgr.addTransaction(new Transaction(203, 2, TransactionType.DEBIT, 14.5, 2020));

        Map<Integer, Double> avg = mgr.getAverageTransactionAmountByAccount();

        assertThat(avg.get(1)).isCloseTo(40.0, within(1e-4));
        assertThat(avg.get(2)).isCloseTo(33.3333, within(1e-4));
        // Account 3 has no transactions -> should not be present
        assertThat(avg).doesNotContainKey(3);
    }
}
