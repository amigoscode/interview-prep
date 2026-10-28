package com.amigoscode.karat.snippets;

import java.util.HashSet;
import java.util.Set;

/** Holds the users of the system. Usernames are unique and can be changed. */
public class UserDirectory {

    private final Set<User> users = new HashSet<>();

    public boolean add(User user) {
        return users.add(user);
    }

    public boolean contains(String username) {
        return users.contains(new User(username));
    }

    public int size() {
        return users.size();
    }

    /** Renames a user. Returns false if nobody has {@code oldUsername}. */
    public boolean rename(String oldUsername, String newUsername) {
        for (User user : users) {
            if (user.getUsername().equals(oldUsername)) {
                user.setUsername(newUsername);
                return true;
            }
        }
        return false;
    }
}
