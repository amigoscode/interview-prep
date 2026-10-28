package com.amigoscode.karat.snippets;

/**
 * Theory question: "Although this code works correctly, it has a design problem. What is the
 * problem, how would you improve the design, and why?" The problem: {@code username} is used in
 * {@code equals} and {@code hashCode} but can be changed with a setter, so a {@code User} that sits
 * in a {@code HashSet} or is a {@code HashMap} key is filed under a hash it no longer has. The fix
 * is to make the class immutable: final field, no setter, a {@link #withUsername} that returns a
 * new instance. The directory then replaces the old object instead of mutating it.
 */
public final class User {

    private final String username;

    public User(String username) {
        this.username = username;
    }

    /** Returns a copy of this user with a new username. */
    public User withUsername(String username) {
        return new User(username);
    }

    public String getUsername() {
        return username;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof User other && username.equals(other.username);
    }

    @Override
    public int hashCode() {
        return username.hashCode();
    }
}
