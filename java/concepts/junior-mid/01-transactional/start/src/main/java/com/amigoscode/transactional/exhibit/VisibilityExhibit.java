package com.amigoscode.transactional.exhibit;

import com.amigoscode.transactional.account.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * An exhibit for Task 9: the same in start/ and solution/. Five methods, all @Transactional, that
 * differ only in their modifiers. Each one reports what it found when it ran. Do not fix it.
 */
@Service
public class VisibilityExhibit {

    /** What a method saw when it ran. */
    public record Observation(boolean transactionActive, boolean fieldsInjected) {
    }

    private final AccountRepository accounts;

    public VisibilityExhibit(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Transactional
    public Observation publicMethod() {
        return observe();
    }

    @Transactional
    protected Observation protectedMethod() {
        return observe();
    }

    @Transactional
    Observation packagePrivateMethod() {
        return observe();
    }

    @Transactional
    public final Observation finalMethod() {
        return observe();
    }

    /** A private method can only be reached from inside this class, so the caller is always "this". */
    public Observation callsPrivateMethod() {
        return privateMethod();
    }

    @Transactional
    private Observation privateMethod() {
        return observe();
    }

    private Observation observe() {
        return new Observation(TransactionSynchronizationManager.isActualTransactionActive(), accounts != null);
    }
}
