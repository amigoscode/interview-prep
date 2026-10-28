package com.amigoscode.karat.music;

/** The result of {@link MusicLibrary#getListenerStats(int)}. */
public class ListenerStats {
    public final int totalPlays;
    public final int uniqueSongs;
    public final double completionRate;

    public ListenerStats(int totalPlays, int uniqueSongs, double completionRate) {
        this.totalPlays = totalPlays;
        this.uniqueSongs = uniqueSongs;
        this.completionRate = completionRate;
    }
}
