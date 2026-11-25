package com.amigoscode.constraints;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.amigoscode.constraints.settings.Settings;
import com.amigoscode.constraints.settings.Strategy;
import com.amigoscode.constraints.support.IntegrationTest;
import com.amigoscode.constraints.support.Race;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Task 6. user_settings has user_id as its primary key, so there can never be two rows for one
 * user. These tests are about what the application does when the database says no. The first
 * three pass on purpose, before and after your fix: they document the bug and the trap.
 */
@Tag("task6")
class SettingsIT extends IntegrationTest {

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("check-first: two first requests at the same moment: one of them fails on the primary key (this documents the bug)")
    void checkFirstFailsUnderARace() {
        long user = insertUser("lee@example.com");
        work.everyonePassesTheCheckBeforeAnyoneInserts(2);

        Race.Result<Settings> result = Race.run(2, () -> settingsService.getOrCreate(Strategy.CHECK_FIRST, user));

        assertThat(result.succeeded()).as("requests that got the settings").hasSize(1);
        assertThat(result.failedWith(DuplicateKeyException.class)).as("requests that hit the primary key").isEqualTo(1);
        assertThat(settingsRows(user)).as("rows in user_settings").isEqualTo(1);
    }

    @Test
    @DisplayName("catch-inside: after a duplicate key is caught inside the transaction, the next statement fails: \"current transaction is aborted\" (this documents the trap)")
    void catchingInsideTheTransactionDoesNotHelp() {
        long user = insertUser("lee@example.com");
        settingsService.getOrCreate(Strategy.CATCH_INSIDE, user);

        assertThatThrownBy(() -> settingsService.getOrCreate(Strategy.CATCH_INSIDE, user))
                .as("the second call: the insert fails, the catch block reads the row")
                .hasMessageContaining("current transaction is aborted, commands ignored until end of transaction block");
    }

    @Test
    @DisplayName("swallow: a write, then a caught duplicate key, then a normal return: the commit reports nothing and the write is gone (this documents the trap)")
    void swallowingInsideTheTransactionLosesEarlierWrites() {
        long user = insertUser("lee@example.com");
        jdbc.update("insert into user_settings (user_id) values (?)", user);

        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            jdbc.update("update user_settings set theme = 'dark' where user_id = ?", user);   // this worked
            try {
                jdbc.update("insert into user_settings (user_id) values (?)", user);          // this is refused
            } catch (DuplicateKeyException e) {
                // swallowed. Nothing else touches the database, and the method returns normally.
            }
        });

        assertThat(jdbc.queryForObject("select theme from user_settings where user_id = ?", String.class, user))
                .as("theme after a transaction that returned without any exception")
                .isEqualTo("light");
    }

    @Test
    @DisplayName("catch-outside: the duplicate key is caught after the transaction has ended: the second request gets the existing row")
    void catchingOutsideTheTransactionWorks() {
        long user = insertUser("lee@example.com");

        assertThat(settingsService.getOrCreate(Strategy.CATCH_OUTSIDE, user).created()).as("first call created the row").isTrue();
        assertThat(settingsService.getOrCreate(Strategy.CATCH_OUTSIDE, user).created()).as("second call created the row").isFalse();
        assertThat(settingsRows(user)).as("rows in user_settings").isEqualTo(1);
    }

    @Test
    @DisplayName("savepoint: rolling back to a savepoint keeps the transaction alive: the second request gets the existing row")
    void aSavepointKeepsTheTransactionAlive() {
        long user = insertUser("lee@example.com");

        assertThat(settingsService.getOrCreate(Strategy.SAVEPOINT, user).created()).as("first call created the row").isTrue();
        assertThat(settingsService.getOrCreate(Strategy.SAVEPOINT, user).created()).as("second call created the row").isFalse();
        assertThat(settingsRows(user)).as("rows in user_settings").isEqualTo(1);
    }

    @Test
    @DisplayName("on-conflict: two first requests at the same moment: both get the settings, one row, exactly one is told it created the row")
    void onConflictDoNothingSurvivesARace() {
        long user = insertUser("lee@example.com");
        work.everyonePassesTheCheckBeforeAnyoneInserts(2);

        Race.Result<Settings> result = Race.run(2, () -> settingsService.getOrCreate(Strategy.ON_CONFLICT, user));

        assertThat(result.failed()).as("errors that would be a 500").isEmpty();
        assertThat(result.succeeded()).as("requests that got the settings").hasSize(2);
        assertThat(result.succeeded().stream().filter(Settings::created).count()).as("requests told they created the row").isEqualTo(1);
        assertThat(settingsRows(user)).as("rows in user_settings").isEqualTo(1);
    }

    @Test
    @DisplayName("on-conflict: an existing row is left alone: a saved theme is not reset to the default")
    void onConflictDoNothingDoesNotOverwrite() {
        long user = insertUser("lee@example.com");
        jdbc.update("insert into user_settings (user_id, theme) values (?, 'dark')", user);

        Settings settings = settingsService.getOrCreate(Strategy.ON_CONFLICT, user);

        assertThat(settings.theme()).as("theme").isEqualTo("dark");
        assertThat(settings.created()).as("created").isFalse();
    }

    @Test
    @DisplayName("save theme: two saves at the same moment for a user without a row: both succeed, one row, exactly one is told it created the row")
    void saveThemeSurvivesARace() {
        long user = insertUser("kim@example.com");
        work.everyonePassesTheCheckBeforeAnyoneInserts(2);

        Race.Result<Settings> result = Race.run(2, () -> settingsService.saveTheme(user, "dark"));

        assertThat(result.failed()).as("errors that would be a 500").isEmpty();
        assertThat(result.succeeded()).as("saves that succeeded").hasSize(2);
        assertThat(result.succeeded().stream().filter(Settings::created).count()).as("saves told they created the row").isEqualTo(1);
        assertThat(settingsRows(user)).as("rows in user_settings").isEqualTo(1);
    }

    @Test
    @DisplayName("save theme: the first save creates the row, the second one updates it")
    void saveThemeCreatesThenUpdates() {
        long user = insertUser("kim@example.com");

        Settings first = settingsService.saveTheme(user, "dark");
        Settings second = settingsService.saveTheme(user, "blue");

        assertThat(first.created()).as("first save created the row").isTrue();
        assertThat(second.created()).as("second save created the row").isFalse();
        assertThat(jdbc.queryForObject("select theme from user_settings where user_id = ?", String.class, user))
                .as("theme in the database").isEqualTo("blue");
    }
}
