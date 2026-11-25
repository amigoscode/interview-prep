package com.amigoscode.constraints;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.amigoscode.constraints.support.Stub;
import com.amigoscode.constraints.user.EmailTakenException;
import com.amigoscode.constraints.user.SignupService;
import com.amigoscode.constraints.user.UserRepository;
import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Unit tests: no Spring, no database. UserRepository is a stub whose check says "the email is free"
 * and whose save fails the way the real one does when the database refuses the row, so only the
 * translation in SignupService is under test.
 */
@Tag("task2")
class SignupServiceTest {

    @Test
    @DisplayName("Translate: when the insert is refused by users_email_key, the signup fails with EmailTakenException (a 409, not a 500)")
    void aViolationOfTheEmailConstraintMeansEmailTaken() {
        SignupService service = serviceWhoseInsertViolates("users_email_key");

        assertThatThrownBy(() -> service.signUp("ana@example.com", "Ana"))
                .isInstanceOf(EmailTakenException.class);
    }

    @Test
    @DisplayName("Translate: a violation of any other constraint is not reported as \"email taken\"")
    void aViolationOfAnotherConstraintIsNotEmailTaken() {
        SignupService service = serviceWhoseInsertViolates("users_display_name_check");

        assertThatThrownBy(() -> service.signUp("ana@example.com", "Ana"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static SignupService serviceWhoseInsertViolates(String constraintName) {
        UserRepository users = Stub.repository(UserRepository.class, user -> {
            throw refusedBy(constraintName);
        });
        return new SignupService(users, step -> { });
    }

    /** The exception users.save really throws: Spring's, wrapping Hibernate's, which knows the constraint name. */
    static DataIntegrityViolationException refusedBy(String constraintName) {
        SQLException fromPostgres = new SQLException(
                "ERROR: duplicate key value violates unique constraint \"" + constraintName + "\"", "23505");
        return new DataIntegrityViolationException("could not execute statement; constraint [" + constraintName + "]",
                new ConstraintViolationException("could not execute statement", fromPostgres, constraintName));
    }
}
