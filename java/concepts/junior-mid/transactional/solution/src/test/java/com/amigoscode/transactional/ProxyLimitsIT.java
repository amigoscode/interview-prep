package com.amigoscode.transactional;

import static com.amigoscode.transactional.demo.Bank.ALICE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.amigoscode.transactional.account.Account;
import com.amigoscode.transactional.account.AccountRepository;
import com.amigoscode.transactional.exhibit.VisibilityCaller;
import com.amigoscode.transactional.exhibit.VisibilityExhibit.Observation;
import com.amigoscode.transactional.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Task 9. These tests pass from the start: they pin down, on this exact stack (Spring Framework 7,
 * Hibernate 7, Postgres 17), which methods a proxy can make transactional and what readOnly does.
 */
@Tag("task9")
class ProxyLimitsIT extends IntegrationTest {

    @Autowired
    VisibilityCaller caller;

    @Autowired
    AccountRepository accounts;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("Visibility: public, protected and package-private @Transactional methods all get a transaction")
    void nonPrivateMethodsAreTransactional() {
        assertThat(caller.callPublic().transactionActive()).as("public").isTrue();
        assertThat(caller.callProtected().transactionActive()).as("protected").isTrue();
        assertThat(caller.callPackagePrivate().transactionActive()).as("package-private").isTrue();
    }

    @Test
    @DisplayName("Visibility: a final method gets no transaction and sees null fields, a private method gets no transaction")
    void finalAndPrivateMethodsAreNotTransactional() {
        Observation finalMethod = caller.callFinal();
        Observation privateMethod = caller.callPrivate();

        assertThat(finalMethod.transactionActive()).as("final: transaction active").isFalse();
        assertThat(finalMethod.fieldsInjected()).as("final: the constructor-injected field is set").isFalse();
        assertThat(privateMethod.transactionActive()).as("private: transaction active").isFalse();
        assertThat(privateMethod.fieldsInjected()).as("private: the constructor-injected field is set").isTrue();
    }

    @Test
    @DisplayName("readOnly: a changed entity is silently not written. No exception, no UPDATE")
    void readOnlySilentlyDropsEntityChanges() {
        TransactionTemplate readOnly = new TransactionTemplate(transactionManager);
        readOnly.setReadOnly(true);

        readOnly.executeWithoutResult(status -> {
            Account alice = accounts.require(ALICE);
            alice.rename("Alice Smith");
            accounts.save(alice);
        });

        assertThat(owner(ALICE)).as("owner in the database after the read-only transaction committed").isEqualTo("Alice");
    }

    @Test
    @DisplayName("readOnly: an UPDATE statement sent inside a read-only transaction is refused by Postgres")
    void readOnlyRefusesAnUpdateStatement() {
        TransactionTemplate readOnly = new TransactionTemplate(transactionManager);
        readOnly.setReadOnly(true);

        assertThatThrownBy(() -> readOnly.executeWithoutResult(status ->
                jdbc.update("update accounts set owner = 'Alice Smith' where id = ?", ALICE)))
                .hasMessageContaining("read-only transaction");

        assertThat(owner(ALICE)).as("owner in the database").isEqualTo("Alice");
    }
}
