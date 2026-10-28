package com.amigoscode.karat.music;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * We are building the analytics back-end for a music streaming platform. The system tracks songs
 * and play events. A play is completed when listenedSeconds is at least the song's duration,
 * otherwise it is skipped.
 *
 * <p>Part 1: {@code MusicLibraryTest} is not passing because of a bug in this class. Read the
 * code, run the test, fix the bug.
 *
 * <p>Part 2: implement {@link #getMostPlayedSong}, {@link #getAverageListenTime} and
 * {@link #getMostActiveListener}.
 */
public class MusicLibrary {

    public final Map<Integer, Song> songs = new HashMap<>();
    public final List<PlayEvent> playEvents = new ArrayList<>();

    public void addSong(Song song) {
        songs.put(song.songId, song);
    }

    public void addPlayEvent(PlayEvent event) {
        playEvents.add(event);
    }

    private boolean isCompleted(PlayEvent event) {
        Song song = songs.get(event.songId);
        return event.listenedSeconds >= song.durationSeconds;
    }

    /**
     * Returns the user's total number of plays, the number of distinct songs played and the
     * completion rate: completed plays divided by all plays, 0.0 when the user has no plays.
     */
    public ListenerStats getListenerStats(int userId) {
        List<PlayEvent> userEvents = new ArrayList<>();
        for (PlayEvent e : playEvents) {
            if (e.userId == userId) {
                userEvents.add(e);
            }
        }

        List<PlayEvent> completedEvents = new ArrayList<>();
        for (PlayEvent e : userEvents) {
            if (isCompleted(e)) {
                completedEvents.add(e);
            }
        }

        int totalPlays = completedEvents.size();

        Set<Integer> uniqueSongs = new HashSet<>();
        for (PlayEvent e : completedEvents) {
            uniqueSongs.add(e.songId);
        }

        double completionRate;
        if (totalPlays == 0) {
            completionRate = 0.0;
        } else {
            completionRate = (double) completedEvents.size() / userEvents.size();
        }

        return new ListenerStats(totalPlays, uniqueSongs.size(), completionRate);
    }

    /**
     * Part 2.1: returns the song that has been played the greatest number of times across all
     * play events. Every play event counts as one play, completed and skipped alike. If several
     * songs tie for the highest count, returns the one with the smallest songId. Returns null if
     * there are no play events.
     */
    public Song getMostPlayedSong() {
        throw new UnsupportedOperationException("part 2");
    }

    /**
     * Part 2.2: returns the average listenedSeconds across all play events for the given user,
     * completed and skipped plays included. Returns 0.0 if the user has no play events.
     */
    public double getAverageListenTime(int userId) {
        throw new UnsupportedOperationException("part 2");
    }

    /**
     * Part 2.3: returns the userId of the listener with the greatest number of play events. Every
     * play event counts. If several users tie, returns the smaller userId. Returns -1 if there
     * are no play events.
     */
    public int getMostActiveListener() {
        throw new UnsupportedOperationException("part 2");
    }
}
