package com.amigoscode.ledger;

import com.amigoscode.ledger.model.Account;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static com.amigoscode.ledger.util.Util.isNegativeAmount;

/**
 * Story 1 starts here. Add fields and methods as the stories demand; the signatures below are
 * only the shape the interviewer's skeleton usually has. Write the test first when you can.
 */
public final class Ledger {

    private List<Account> accountList = new ArrayList<>();

    public Ledger(List<Account> accountList) {
        this.accountList = accountList;
    }


    public void open(String accountId, BigDecimal openingBalance) {

        isAccountIdEmpty(accountId);
        isAccountExists(accountId);
        isNegativeAmount(openingBalance);

        accountList.add(new Account(accountId, openingBalance));


    }

    private static void isAccountIdEmpty(String accountId) {
        if (accountId.isBlank()) {
            throw new IllegalArgumentException("account Id is blank");
        }
    }

    private void isAccountExists(String accountId) {

        if (accountList.stream().anyMatch(account -> account.getAccountId().equals(accountId))) {
            throw new IllegalArgumentException("account already exists");
        }
    }

    public BigDecimal balance(String accountId) {
        isAccountIdEmpty(accountId);
        return fetchAccount(accountId).getBalance();

    }

    private Account fetchAccount(String accountId) {
        return accountList.stream().filter(account -> account.getAccountId().equals(accountId)
        ).findFirst().orElseThrow(() -> new IllegalStateException("account does not exist"));
    }

    ;


    public void deposit(String accountId, BigDecimal amount) {
        //check if account exists with this accountid 
        //check if amount is non negative

        isAccountExists(accountId);
        if (isNegativeAmount(amount)) {
            fetchAccount(accountId).getBalance().add(amount);
        }
    }


    public void withdraw(String accountId, BigDecimal amount) {
        //check if account exists
        //check if amount is non negative
        //check if balance is greater than the amount to withdraw

        isAccountIdEmpty(accountId);
        isNegativeAmount(amount);
        BigDecimal currBalance = fetchAccount(accountId).getBalance();
        if (currBalance.compareTo(amount) > 0) {
            fetchAccount(accountId).setBalance(currBalance.subtract(amount));
        } else throw new IllegalArgumentException("your account is running out of balance");

    }

    public void transfer(String fromId, String toId, BigDecimal amount) {
        //check fromId and toId are not empty
        //check fromId and toId not equal
        // check from acc balance > amount
        isAccountIdEmpty(fromId);
        isAccountIdEmpty(toId);
        isAccountExists(fromId);
        isAccountExists(toId);
        isNegativeAmount(amount);
        if(fromId.equals(toId)){
            throw new IllegalStateException("transfer not allowed within same account");
        }
        BigDecimal fromAccCurrBalance = fetchAccount(fromId).getBalance();
        BigDecimal toAccCurrBalance = fetchAccount(toId).getBalance();
        if (fromAccCurrBalance.compareTo(amount) > 0) {
            fetchAccount(fromId).setBalance(fromAccCurrBalance.subtract(amount));
            fetchAccount(toId).setBalance(toAccCurrBalance.add(amount));
        } else {
            throw new IllegalArgumentException("your from account does not have sufficient balance");
        }
    }
}
