package com.amigoscode.karat.access;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * We are building a system to manage physical access to a corporate campus. Each user has a
 * unique ID, a name and an access level. Access levels are ranked: VISITOR is the lowest, STAFF
 * is higher and ADMIN is the highest. A user is authorized for a required access level when their
 * own level is the same or higher, so an ADMIN is authorized for STAFF and VISITOR areas, and a
 * STAFF user is authorized for VISITOR areas.
 *
 * <p>Part 1: {@code AccessManagerTest} is not passing because of a bug in this class. Read the
 * code, run the test, fix the bug.
 *
 * <p>Part 2 (our prediction, not from the question bank): security wants reports. Implement
 * {@link #getUserIdsAtOrAbove} and {@link #getCountByLevel}.
 */
public class AccessManager {

    private final List<User> users = new ArrayList<>();

    /** Registers a user on the campus. */
    public void addUser(User user) {
        users.add(user);
    }

    /** Updates the access level of the given user. */
    public void updateAccessLevel(int userId, AccessLevel accessLevel) {
        for (User user : users) {
            if (user.userId == userId) {
                user.accessLevel = accessLevel;
                break;
            }
        }
    }

    /** Returns whether the given user is authorized for the required access level. */
    public boolean isAuthorized(int userId, AccessLevel requiredLevel) {
        for (User user : users) {
            if (user.userId == userId) {
                // Bug was == on the level, so a higher level was not authorized for a lower area.
                return user.accessLevel.getRank() >= requiredLevel.getRank();
            }
        }
        return false;
    }

    /**
     * Part 2.1: the IDs of every user whose access level is the given level or higher, sorted in
     * ascending order. Empty when nobody qualifies.
     */
    public List<Integer> getUserIdsAtOrAbove(AccessLevel level) {
        List<Integer> ids = new ArrayList<>();
        for (User user : users) {
            if (user.accessLevel.getRank() >= level.getRank()) {
                ids.add(user.userId);
            }
        }
        ids.sort(null);
        return ids;
    }

    /**
     * Part 2.2: a map from access level to the number of users at exactly that level. Only levels
     * with at least one user appear; levels with none are omitted.
     */
    public Map<AccessLevel, Integer> getCountByLevel() {
        Map<AccessLevel, Integer> counts = new EnumMap<>(AccessLevel.class);
        for (User user : users) {
            counts.merge(user.accessLevel, 1, Integer::sum);
        }
        return counts;
    }
}
