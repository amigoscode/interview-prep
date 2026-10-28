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

    /** A play of a song the library does not know counts as skipped rather than blowing up. */
    private boolean isCompleted(PlayEvent event) {
        Song song = songs.get(event.songId);
        return song != null && event.listenedSeconds >= song.durationSeconds;
    }

    /**
     * Returns the user's total number of plays, the number of distinct songs played and the
     * completion rate: completed plays divided by all plays, 0.0 when the user has no plays.
     */
    public ListenerStats getListenerStats(int userId) {
        List<PlayEvent> userEvents = eventsFor(userId);

        int completed = 0;
        Set<Integer> uniqueSongs = new HashSet<>();
        // Bug was counting totalPlays and uniqueSongs over the completed plays only; every play
        // of the user counts, completion only feeds the rate.
        for (PlayEvent e : userEvents) {
            uniqueSongs.add(e.songId);
            if (isCompleted(e)) {
                completed++;
            }
        }

        int totalPlays = userEvents.size();
        double completionRate = totalPlays == 0 ? 0.0 : (double) completed / totalPlays;
        return new ListenerStats(totalPlays, uniqueSongs.size(), completionRate);
    }

    /**
     * Part 2.1: returns the song that has been played the greatest number of times across all
     * play events. Every play event counts as one play, completed and skipped alike. If several
     * songs tie for the highest count, returns the one with the smallest songId. Returns null if
     * there are no play events.
     */
    public Song getMostPlayedSong() {
        Map<Integer, Integer> playsBySong = new HashMap<>();
        for (PlayEvent e : playEvents) {
            playsBySong.merge(e.songId, 1, Integer::sum);
        }
        int bestSongId = idWithHighestCount(playsBySong);
        return bestSongId == -1 ? null : songs.get(bestSongId);
    }

    /**
     * Part 2.2: returns the average listenedSeconds across all play events for the given user,
     * completed and skipped plays included. Returns 0.0 if the user has no play events.
     */
    public double getAverageListenTime(int userId) {
        List<PlayEvent> userEvents = eventsFor(userId);
        if (userEvents.isEmpty()) {
            return 0.0;
        }
        double total = 0;
        for (PlayEvent e : userEvents) {
            total += e.listenedSeconds;
        }
        return total / userEvents.size();
    }

    /**
     * Part 2.3: returns the userId of the listener with the greatest number of play events. Every
     * play event counts. If several users tie, returns the smaller userId. Returns -1 if there
     * are no play events.
     */
    public int getMostActiveListener() {
        Map<Integer, Integer> playsByUser = new HashMap<>();
        for (PlayEvent e : playEvents) {
            playsByUser.merge(e.userId, 1, Integer::sum);
        }
        return idWithHighestCount(playsByUser);
    }

    private List<PlayEvent> eventsFor(int userId) {
        List<PlayEvent> userEvents = new ArrayList<>();
        for (PlayEvent e : playEvents) {
            if (e.userId == userId) {
                userEvents.add(e);
            }
        }
        return userEvents;
    }

    /** The id with the highest count, the smallest id on a tie, -1 when the map is empty. */
    private static int idWithHighestCount(Map<Integer, Integer> countsById) {
        int bestId = -1;
        int bestCount = 0;
        for (Map.Entry<Integer, Integer> entry : countsById.entrySet()) {
            int id = entry.getKey();
            int count = entry.getValue();
            if (count > bestCount || (count == bestCount && id < bestId)) {
                bestId = id;
                bestCount = count;
            }
        }
        return bestId;
    }
}
