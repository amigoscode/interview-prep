package com.amigoscode.karat.games;

/** The result of {@link GameManager#getPlayerStatistics(int)}. */
public class PlayerStats {
    public final int totalMatches;
    public final int wins;
    public final double winRate;

    public PlayerStats(int totalMatches, int wins, double winRate) {
        this.totalMatches = totalMatches;
        this.wins = wins;
        this.winRate = winRate;
    }
}
