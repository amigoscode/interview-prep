package com.amigoscode.karat.music;

/** Data about a song in the library. Duration is in seconds. */
public class Song {
    public final int songId;
    public final String title;
    public final String artist;
    public final int durationSeconds;

    public Song(int songId, String title, String artist, int durationSeconds) {
        this.songId = songId;
        this.title = title;
        this.artist = artist;
        this.durationSeconds = durationSeconds;
    }

    @Override
    public String toString() {
        return "Song ID: " + songId + ", Title: " + title + ", Artist: " + artist
                + ", Duration: " + durationSeconds + "s";
    }
}
