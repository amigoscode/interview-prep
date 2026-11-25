package com.amigoscode.constraints;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.amigoscode.constraints.support.IntegrationTest;
import com.amigoscode.constraints.support.Race;
import com.amigoscode.constraints.user.EmailTakenException;
import com.amigoscode.constraints.user.User;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

@Tag("task4")
class CaseInsensitiveEmailIT extends IntegrationTest {

    @Test
    @DisplayName("Case: the database refuses ana@example.com when Ana@Example.com is already there, even from code that skips the service")
    void theDatabaseRefusesTheSameEmailInAnotherCase() {
        insertUser("Ana@Example.com");

        assertThatThrownBy(() -> insertUser("ana@example.com"))
                .as("a plain SQL insert of the same address in lower case")
                .isInstanceOf(DuplicateKeyException.class);
        assertThat(userRows()).as("rows in users").isEqualTo(1);
    }

    @Test
    @DisplayName("Case: Ana@Example.com and ana@example.com sign up at the same moment: one account, the other is told the email is taken")
    void onlyOneOfTwoSpellingsCreatesAnAccount() {
        List<String> spellings = List.of("Ana@Example.com", "ana@example.com");
        work.everyonePassesTheCheckBeforeAnyoneInserts(2);

        Race.Result<User> result = Race.run(2, i -> () -> signupService.signUp(spellings.get(i), "Ana"));

        assertThat(result.failedWithAnythingBut(EmailTakenException.class)).as("errors that would be a 500").isEmpty();
        assertThat(result.succeeded()).as("accounts created").hasSize(1);
        assertThat(result.failedWith(EmailTakenException.class)).as("requests told the email is taken").isEqualTo(1);
        assertThat(userRows()).as("rows in users").isEqualTo(1);
    }

    @Test
    @DisplayName("Case: ANA@EXAMPLE.COM arriving after ana@example.com is stopped by the application's check, before any insert is tried")
    void aLaterSignupInAnotherCaseIsStoppedByTheFastPath() {
        signupService.signUp("ana@example.com", "Ana");

        assertThatThrownBy(() -> signupService.signUp("ANA@EXAMPLE.COM", "Ana again"))
                .isInstanceOf(EmailTakenException.class);

        assertThat(work.arrivals())
                .as("signups that got past the email check (the second one must not)")
                .isEqualTo(1);
    }
}
