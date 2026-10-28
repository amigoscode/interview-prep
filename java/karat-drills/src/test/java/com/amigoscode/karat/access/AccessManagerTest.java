package com.amigoscode.karat.access;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * This is not a complete test suite, but tests some basic functionality of the code and shows how
 * to use it. One test fails on purpose: that is part 1.
 */
@Disabled("access drill: remove this line to begin part 1")
class AccessManagerTest {

    @Test
    void user() {
        User u = new User(1, "John Doe", AccessLevel.VISITOR);

        assertThat(u.userId).isEqualTo(1);
        assertThat(u.name).isEqualTo("John Doe");
        assertThat(u.accessLevel).isEqualTo(AccessLevel.VISITOR);
    }

    @Test
    void isAuthorized() {
        AccessManager manager = new AccessManager();
        manager.addUser(new User(1, "Vera Visitor", AccessLevel.VISITOR));
        manager.addUser(new User(2, "Sam Staff", AccessLevel.STAFF));
        manager.addUser(new User(3, "Ada Admin", AccessLevel.ADMIN));

        // A user is authorized for their own level and for any lower level.
        assertThat(manager.isAuthorized(1, AccessLevel.VISITOR)).isTrue();
        assertThat(manager.isAuthorized(1, AccessLevel.STAFF)).isFalse();
        assertThat(manager.isAuthorized(1, AccessLevel.ADMIN)).isFalse();
        assertThat(manager.isAuthorized(2, AccessLevel.VISITOR)).isTrue();
        assertThat(manager.isAuthorized(2, AccessLevel.STAFF)).isTrue();
        assertThat(manager.isAuthorized(2, AccessLevel.ADMIN)).isFalse();
        assertThat(manager.isAuthorized(3, AccessLevel.VISITOR)).isTrue();
        assertThat(manager.isAuthorized(3, AccessLevel.STAFF)).isTrue();
        assertThat(manager.isAuthorized(3, AccessLevel.ADMIN)).isTrue();

        // An unknown user is never authorized.
        assertThat(manager.isAuthorized(999, AccessLevel.VISITOR)).isFalse();
    }

    @Test
    @Disabled("part 2: remove this line when part 1 is green")
    void userIdsAtOrAboveAreSortedAndIncludeTheLevelItself() {
        AccessManager manager = new AccessManager();
        manager.addUser(new User(5, "Ada Admin", AccessLevel.ADMIN));
        manager.addUser(new User(1, "Vera Visitor", AccessLevel.VISITOR));
        manager.addUser(new User(4, "Sam Staff", AccessLevel.STAFF));
        manager.addUser(new User(2, "Stan Staff", AccessLevel.STAFF));

        assertThat(manager.getUserIdsAtOrAbove(AccessLevel.VISITOR)).containsExactly(1, 2, 4, 5);
        assertThat(manager.getUserIdsAtOrAbove(AccessLevel.STAFF)).containsExactly(2, 4, 5);
        assertThat(manager.getUserIdsAtOrAbove(AccessLevel.ADMIN)).containsExactly(5);
    }

    @Test
    @Disabled("part 2: remove this line when part 1 is green")
    void userIdsAtOrAboveIsEmptyWhenNobodyQualifies() {
        AccessManager manager = new AccessManager();
        assertThat(manager.getUserIdsAtOrAbove(AccessLevel.VISITOR)).isEmpty();

        manager.addUser(new User(1, "Vera Visitor", AccessLevel.VISITOR));
        manager.addUser(new User(2, "Sam Staff", AccessLevel.STAFF));
        assertThat(manager.getUserIdsAtOrAbove(AccessLevel.ADMIN)).isEmpty();
    }

    @Test
    @Disabled("part 2: remove this line when part 1 is green")
    void countByLevelCountsEachLevelSeparately() {
        AccessManager manager = new AccessManager();
        manager.addUser(new User(1, "Vera Visitor", AccessLevel.VISITOR));
        manager.addUser(new User(2, "Sam Staff", AccessLevel.STAFF));
        manager.addUser(new User(3, "Ada Admin", AccessLevel.ADMIN));
        manager.addUser(new User(4, "Vic Visitor", AccessLevel.VISITOR));
        manager.addUser(new User(5, "Val Visitor", AccessLevel.VISITOR));

        Map<AccessLevel, Integer> counts = manager.getCountByLevel();

        assertThat(counts).containsOnlyKeys(AccessLevel.VISITOR, AccessLevel.STAFF, AccessLevel.ADMIN);
        assertThat(counts.get(AccessLevel.VISITOR)).isEqualTo(3);
        assertThat(counts.get(AccessLevel.STAFF)).isEqualTo(1);
        assertThat(counts.get(AccessLevel.ADMIN)).isEqualTo(1);
    }

    @Test
    @Disabled("part 2: remove this line when part 1 is green")
    void countByLevelOmitsLevelsWithNoUsers() {
        AccessManager manager = new AccessManager();
        assertThat(manager.getCountByLevel()).isEmpty();

        manager.addUser(new User(1, "Sam Staff", AccessLevel.STAFF));
        manager.addUser(new User(2, "Stan Staff", AccessLevel.STAFF));

        Map<AccessLevel, Integer> counts = manager.getCountByLevel();

        assertThat(counts).containsOnlyKeys(AccessLevel.STAFF);
        assertThat(counts.get(AccessLevel.STAFF)).isEqualTo(2);
        assertThat(counts).doesNotContainKey(AccessLevel.VISITOR);
        assertThat(counts).doesNotContainKey(AccessLevel.ADMIN);
    }
}
