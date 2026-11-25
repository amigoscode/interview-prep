package com.amigoscode.constraints.settings;

import com.amigoscode.constraints.user.UserNotFoundException;
import com.amigoscode.constraints.user.UserRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/** Picks the strategy. Deliberately NOT transactional: every SettingsStore method is its own transaction. */
@Service
public class SettingsService {

    private final SettingsStore store;
    private final UserRepository users;

    public SettingsService(SettingsStore store, UserRepository users) {
        this.store = store;
        this.users = users;
    }

    /** Returns the user's settings, creating the row with the defaults the first time it is asked for. */
    public Settings getOrCreate(Strategy strategy, long userId) {
        requireUser(userId);
        return switch (strategy) {
            case CHECK_FIRST -> store.checkThenInsert(userId);
            case CATCH_INSIDE -> store.insertAndCatchInside(userId);
            case CATCH_OUTSIDE -> insertAndCatchOutside(userId);
            case SAVEPOINT -> store.insertWithSavepoint(userId);
            case ON_CONFLICT -> store.insertOnConflictDoNothing(userId);
        };
    }

    public Settings saveTheme(long userId, String theme) {
        requireUser(userId);
        return store.saveTheme(userId, theme);
    }

    /**
     * Correct shape 1: the insert runs in its own transaction. When it fails, that transaction is
     * rolled back and finished before the exception arrives here, so the read starts a fresh one.
     */
    private Settings insertAndCatchOutside(long userId) {
        try {
            store.insertDefaults(userId);
            return new Settings(userId, store.find(userId).theme(), true);
        } catch (DuplicateKeyException e) {
            return store.find(userId);
        }
    }

    private void requireUser(long userId) {
        if (!users.existsById(userId)) {
            throw new UserNotFoundException(userId);
        }
    }
}
