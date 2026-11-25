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
     * The bug: check, then insert. Kept as it is so you can always compare against it. Two requests
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
     * The trap. Kept as it is: insert first, catch the duplicate, carry on in the SAME transaction.
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
     * Correct shape 3: ask the database to skip the insert when the row is there. Nothing fails, so
     * nothing aborts. The number of rows inserted (1 or 0) says whether this call created the row.
     */
    @Transactional
    public Settings insertOnConflictDoNothing(long userId) {
        int inserted = jdbc.update(
                "insert into user_settings (user_id) values (?) on conflict (user_id) do nothing", userId);
        return read(userId, inserted == 1);
    }

    /**
     * "Set the theme, whether or not the row exists yet": one statement. ON CONFLICT DO UPDATE
     * always reports 1 row, so the row count cannot tell an insert from an update. RETURNING
     * (xmax = 0) can: xmax is 0 on a row that was freshly inserted.
     */
    @Transactional
    public Settings saveTheme(long userId, String theme) {
        return jdbc.queryForObject(
                "insert into user_settings (user_id, theme) values (?, ?) "
                        + "on conflict (user_id) do update set theme = excluded.theme, updated_at = now() "
                        + "returning user_id, theme, (xmax = 0) as created",
                (row, n) -> new Settings(row.getLong("user_id"), row.getString("theme"), row.getBoolean("created")),
                userId, theme);
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
