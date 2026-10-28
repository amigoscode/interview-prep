package com.amigoscode.karat.access;

import java.util.Objects;

/** Data about a campus user. */
public class User {
    public final int userId;
    public final String name;
    public AccessLevel accessLevel;

    public User(int userId, String name, AccessLevel accessLevel) {
        this.userId = userId;
        this.name = name;
        this.accessLevel = accessLevel;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof User u)) {
            return false;
        }
        return userId == u.userId
                && name.equals(u.name)
                && accessLevel == u.accessLevel;
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, name, accessLevel);
    }

    @Override
    public String toString() {
        return "User ID: " + userId + ", Name: " + name + ", Access Level: " + accessLevel;
    }
}
