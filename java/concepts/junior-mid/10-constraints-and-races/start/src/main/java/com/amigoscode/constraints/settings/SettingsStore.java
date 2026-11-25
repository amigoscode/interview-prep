package com.amigoscode.constraints.settings;

import com.amigoscode.constraints.work.SlowWork;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * One database transaction per method, plain SQL through JdbcTemplate. user_settings has user_id as
 * its primary key, so the database already refuses a second row for the same user. The methods
 * differ in what they do about that refusal.
 *
 * <p>checkThenInsert and insertAndCatchInside stay broken for ever, so you can always compare
 * against them. insertDefaults and insertWithSavepoint already work. You write the last two.
 */
@Repository
public class SettingsStore {

    private static final String INSERT_DEFAULTS = "insert into user_settings (user_id) values (?)";

    private final JdbcTemplate jdbc;
    private final SlowWork work;

    public SettingsStore(JdbcTemplate jdbc, SlowWork work) {
        this.jdbc = jdbc;
        this.work = work;
    }

    /**
     * The bug: check, then insert. Do not fix this one. Two requests
     * can both find nothing, and the slower one hits the primary key: a 500.
     */
    @Transactional
    public Settings checkThenInsert(long userId) {
        if (exists(userId)) {                                     // CHECK
            return read(userId, false);
        }
        work.run("load the defaults");                            // ...time passes...
        jdbc.update(INSERT_DEFAULTS, userId);                     // INSERT
        return read(userId, true);
    }

    /**
     * The trap. Do not fix this one either: insert first, catch the duplicate, carry on in the SAME transaction.
     * In Postgres a failed statement aborts the whole transaction, so the read in the catch block
     * fails with "current transaction is aborted, commands ignored until end of transaction block".
     */
    @Transactional
    public Settings insertAndCatchInside(long userId) {
        try {
            jdbc.update(INSERT_DEFAULTS, userId);
            return read(userId, true);
        } catch (DuplicateKeyException e) {
            return read(userId, false);                           // never works: the transaction is dead
        }
    }

    /** Correct shape 1, first half: only the insert. SettingsService catches the duplicate OUTSIDE this transaction. */
    @Transactional
    public void insertDefaults(long userId) {
        jdbc.update(INSERT_DEFAULTS, userId);
    }

    /**
     * Correct shape 2: a savepoint. ROLLBACK TO SAVEPOINT undoes the failed insert only, and the
     * transaction is usable again.
     */
    @Transactional
    public Settings insertWithSavepoint(long userId) {
        jdbc.execute("savepoint before_insert");
        boolean created;
        try {
            jdbc.update(INSERT_DEFAULTS, userId);
            jdbc.execute("release savepoint before_insert");
            created = true;
        } catch (DuplicateKeyException e) {
            jdbc.execute("rollback to savepoint before_insert");
            created = false;
        }
        return read(userId, created);
    }

    /**
     * TODO Task 6: correct shape 3. Ask the database to skip the insert when the row is already
     * there, in one statement, and use the number of rows it inserted to fill in "created".
     *
     * <p>As shipped this is the same bug as checkThenInsert.
     */
    @Transactional
    public Settings insertOnConflictDoNothing(long userId) {
        if (exists(userId)) {
            return read(userId, false);
        }
        work.run("load the defaults");
        jdbc.update(INSERT_DEFAULTS, userId);
        return read(userId, true);
    }

    /**
     * "Set the theme, whether or not the row exists yet."
     *
     * <p>TODO Task 6: one statement that inserts the row or updates it, and that says which of the
     * two it did.
     *
     * <p>As shipped it updates, and inserts when the update changed 0 rows. Two requests can both
     * change 0 rows.
     */
    @Transactional
    public Settings saveTheme(long userId, String theme) {
        int updated = jdbc.update("update user_settings set theme = ?, updated_at = now() where user_id = ?", theme, userId);
        if (updated == 0) {
            work.run("load the defaults");
            jdbc.update("insert into user_settings (user_id, theme) values (?, ?)", userId, theme);
        }
        return new Settings(userId, theme, updated == 0);
    }

    @Transactional(readOnly = true)
    public Settings find(long userId) {
        return read(userId, false);
    }

    private boolean exists(long userId) {
        return jdbc.queryForObject("select count(*) from user_settings where user_id = ?", Integer.class, userId) > 0;
    }

    private Settings read(long userId, boolean created) {
        return jdbc.queryForObject("select user_id, theme from user_settings where user_id = ?",
                (row, n) -> new Settings(row.getLong("user_id"), row.getString("theme"), created), userId);
    }
}
