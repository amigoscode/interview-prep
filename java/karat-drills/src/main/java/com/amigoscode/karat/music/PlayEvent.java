package com.amigoscode.karat.music;

/**
 * One play of a song by a user. A play is completed when listenedSeconds is at least the song's
 * duration, otherwise it is skipped.
 */
public class PlayEvent {
    public final int playId;
    public final int userId;
    public final int songId;
    public final int listenedSeconds;
    public final int timestamp;

    public PlayEvent(int playId, int userId, int songId, int listenedSeconds, int timestamp) {
        this.playId = playId;
        this.userId = userId;
        this.songId = songId;
        this.listenedSeconds = listenedSeconds;
        this.timestamp = timestamp;
    }
}
