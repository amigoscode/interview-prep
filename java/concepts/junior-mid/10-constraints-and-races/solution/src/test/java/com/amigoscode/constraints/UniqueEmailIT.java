package com.amigoscode.constraints;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.amigoscode.constraints.support.IntegrationTest;
import com.amigoscode.constraints.support.Race;
import com.amigoscode.constraints.user.EmailTakenException;
import com.amigoscode.constraints.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

@Tag("task2")
class UniqueEmailIT extends IntegrationTest {

    @Test
    @DisplayName("Unique: the database refuses a second row with the same email, even from code that skips the service")
    void theDatabaseRefusesTheSameEmailTwice() {
        insertUser("ana@example.com");

        assertThatThrownBy(() -> insertUser("ana@example.com"))
                .as("a plain SQL insert of an email that is already in the table")
                .isInstanceOf(DuplicateKeyException.class);
        assertThat(userRows()).as("rows in users").isEqualTo(1);
    }

    @Test
    @DisplayName("Unique: two signups with the same email at the same moment: one account is created, the other is told the email is taken")
    void onlyOneOfTwoSignupsCreatesAnAccount() {
        work.everyonePassesTheCheckBeforeAnyoneInserts(2);

        Race.Result<User> result = Race.run(2, () -> signupService.signUp("ana@example.com", "Ana"));

        assertThat(result.failedWithAnythingBut(EmailTakenException.class)).as("errors that would be a 500").isEmpty();
        assertThat(result.succeeded()).as("accounts created").hasSize(1);
        assertThat(result.failedWith(EmailTakenException.class)).as("requests told the email is taken").isEqualTo(1);
        assertThat(userRows()).as("rows in users").isEqualTo(1);
    }

    @Test
    @DisplayName("Unique: 20 signups with the same email: 1 account, 19 told the email is taken, 1 row")
    void onlyOneOfTwentySignupsCreatesAnAccount() {
        work.takes(20);

        Race.Result<User> result = Race.run(20, () -> signupService.signUp("ana@example.com", "Ana"));

        assertThat(result.failedWithAnythingBut(EmailTakenException.class)).as("errors that would be a 500").isEmpty();
        assertThat(result.succeeded()).as("accounts created").hasSize(1);
        assertThat(result.failedWith(EmailTakenException.class)).as("requests told the email is taken").isEqualTo(19);
        assertThat(userRows()).as("rows in users").isEqualTo(1);
    }

    @Test
    @DisplayName("Unique: a signup that arrives after the first one finished is stopped by the application's check, before any insert is tried")
    void aLaterSignupIsStoppedByTheFastPath() {
        signupService.signUp("ana@example.com", "Ana");

        assertThatThrownBy(() -> signupService.signUp("ana@example.com", "Ana again"))
                .isInstanceOf(EmailTakenException.class);

        assertThat(work.arrivals())
                .as("signups that got past the email check (the second one must not)")
                .isEqualTo(1);
    }
}
