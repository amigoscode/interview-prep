package com.amigoscode.transactional;

import static com.amigoscode.transactional.demo.Bank.ALICE;
import static org.assertj.core.api.Assertions.assertThat;

import com.amigoscode.transactional.account.AccountService;
import com.amigoscode.transactional.support.IntegrationTest;
import com.amigoscode.transactional.trace.TransactionTrace;
import com.amigoscode.transactional.trace.TransactionTrace.Kind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@Tag("task8")
class RenameIT extends IntegrationTest {

    @Autowired
    AccountService accountService;

    @Test
    @DisplayName("Rename: the new owner name is written to the database")
    void renameIsWritten() {
        accountService.rename(ALICE, "Alice Smith");

        assertThat(owner(ALICE)).as("owner of account 1 in the database").isEqualTo("Alice Smith");
    }

    @Test
    @DisplayName("Rename: balanceOf still runs in a read-only transaction")
    void lookupsStayReadOnly() {
        long balance = accountService.balanceOf(ALICE);

        assertThat(balance).isEqualTo(100);
        assertThat(TransactionTrace.events())
                .filteredOn(event -> event.kind() == Kind.BEGIN)
                .as("transactions opened by balanceOf")
                .hasSize(1)
                .allMatch(TransactionTrace.Event::readOnly, "is read-only");
    }
}
