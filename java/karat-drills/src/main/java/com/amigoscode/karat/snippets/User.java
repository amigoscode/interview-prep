package com.amigoscode.karat.snippets;

/**
 * Theory question: "Although this code works correctly, it has a design problem. What is the
 * problem, how would you improve the design, and why?"
 */
public class User {

    private String username;

    public User(String username) {
        this.username = username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getUsername() {
        return username;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User)) return false;
        User user = (User) o;
        return username.equals(user.username);
    }

    @Override
    public int hashCode() {
        return username.hashCode();
    }
}
