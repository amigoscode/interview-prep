package com.amigoscode.karat.games;

/** Data about a player on the platform. */
public class Player {
    public final int playerId;
    public final String username;

    public Player(int playerId, String username) {
        this.playerId = playerId;
        this.username = username;
    }

    @Override
    public String toString() {
        return "Player ID: " + playerId + ", Username: " + username;
    }
}
