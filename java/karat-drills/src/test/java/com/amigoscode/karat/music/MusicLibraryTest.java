package com.amigoscode.karat.music;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * This is not a complete test suite, but tests some basic functionality of the code and shows how
 * to use it. One test fails on purpose: that is part 1.
 */
class MusicLibraryTest {

    @Test
    void testGetListenerStats() {
        MusicLibrary lib = new MusicLibrary();
        lib.addSong(new Song(101, "Song A", "Artist 1", 180));
        lib.addSong(new Song(102, "Song B", "Artist 2", 200));
        lib.addSong(new Song(103, "Song C", "Artist 1", 240));

        lib.addPlayEvent(new PlayEvent(1, 1, 101, 180, 1000)); // completed
        lib.addPlayEvent(new PlayEvent(2, 1, 102, 220, 1100)); // completed
        lib.addPlayEvent(new PlayEvent(3, 1, 101, 50, 1200));  // skipped
        lib.addPlayEvent(new PlayEvent(4, 1, 103, 100, 1300)); // skipped

        ListenerStats stats = lib.getListenerStats(1);
        assertThat(stats.totalPlays).isEqualTo(4);
        assertThat(stats.uniqueSongs).isEqualTo(3);
        assertThat(stats.completionRate).isCloseTo(0.5, within(1e-4));
    }

    @Test
    void testGetMostPlayedSong() {
        MusicLibrary lib = new MusicLibrary();
        lib.addSong(new Song(101, "Song A", "Artist 1", 180));
        lib.addSong(new Song(102, "Song B", "Artist 2", 200));
        lib.addSong(new Song(103, "Song C", "Artist 3", 240));

        assertThat(lib.getMostPlayedSong()).isNull();

        lib.addPlayEvent(new PlayEvent(1, 1, 101, 180, 100));
        lib.addPlayEvent(new PlayEvent(2, 2, 101, 50, 200));
        lib.addPlayEvent(new PlayEvent(3, 3, 102, 200, 300));
        lib.addPlayEvent(new PlayEvent(4, 4, 102, 100, 400));

        Song song = lib.getMostPlayedSong();
        assertThat(song).isNotNull();
        assertThat(song.songId).isEqualTo(101);
        assertThat(song.title).isEqualTo("Song A");
    }

    @Test
    void testGetAverageListenTime() {
        MusicLibrary lib = new MusicLibrary();
        lib.addSong(new Song(101, "Song A", "Artist 1", 180));

        lib.addPlayEvent(new PlayEvent(1, 1, 101, 100, 100));
        lib.addPlayEvent(new PlayEvent(2, 1, 101, 250, 200));
        lib.addPlayEvent(new PlayEvent(3, 1, 101, 150, 300));

        assertThat(lib.getAverageListenTime(1)).isCloseTo(166.6666667, within(1e-4));
        assertThat(lib.getAverageListenTime(99)).isCloseTo(0.0, within(1e-4));
    }

    @Test
    void testGetMostActiveListener() {
        MusicLibrary lib = new MusicLibrary();

        assertThat(lib.getMostActiveListener()).isEqualTo(-1);

        lib.addPlayEvent(new PlayEvent(1, 1, 101, 100, 100));
        lib.addPlayEvent(new PlayEvent(2, 1, 102, 200, 200));
        lib.addPlayEvent(new PlayEvent(3, 2, 103, 150, 300));
        lib.addPlayEvent(new PlayEvent(4, 2, 101, 120, 400));

        assertThat(lib.getMostActiveListener()).isEqualTo(1);
    }

    @Test
    void listenerStatsForUserWithNoPlaysAreZero() {
        MusicLibrary lib = new MusicLibrary();
        lib.addSong(new Song(101, "Song A", "Artist 1", 180));
        lib.addPlayEvent(new PlayEvent(1, 1, 101, 180, 1000));

        ListenerStats stats = lib.getListenerStats(99);
        assertThat(stats.totalPlays).isZero();
        assertThat(stats.uniqueSongs).isZero();
        assertThat(stats.completionRate).isZero();
    }

    @Test
    void playOfUnknownSongCountsAsSkipped() {
        MusicLibrary lib = new MusicLibrary();
        lib.addSong(new Song(101, "Song A", "Artist 1", 180));
        lib.addPlayEvent(new PlayEvent(1, 1, 101, 180, 1000)); // completed
        lib.addPlayEvent(new PlayEvent(2, 1, 555, 999, 1100)); // song 555 never added

        ListenerStats stats = lib.getListenerStats(1);
        assertThat(stats.totalPlays).isEqualTo(2);
        assertThat(stats.uniqueSongs).isEqualTo(2);
        assertThat(stats.completionRate).isCloseTo(0.5, within(1e-4));
    }

    @Test
    void mostPlayedSongTieGoesToSmallestSongId() {
        MusicLibrary lib = new MusicLibrary();
        lib.addSong(new Song(101, "Song A", "Artist 1", 180));
        lib.addSong(new Song(102, "Song B", "Artist 2", 200));
        lib.addPlayEvent(new PlayEvent(1, 1, 102, 200, 100));
        lib.addPlayEvent(new PlayEvent(2, 2, 102, 10, 200));
        lib.addPlayEvent(new PlayEvent(3, 3, 101, 180, 300));
        lib.addPlayEvent(new PlayEvent(4, 4, 101, 20, 400));

        assertThat(lib.getMostPlayedSong().songId).isEqualTo(101);
    }

    @Test
    void mostActiveListenerTieGoesToSmallerUserId() {
        MusicLibrary lib = new MusicLibrary();
        lib.addPlayEvent(new PlayEvent(1, 7, 101, 100, 100));
        lib.addPlayEvent(new PlayEvent(2, 3, 101, 100, 200));
        lib.addPlayEvent(new PlayEvent(3, 7, 102, 100, 300));
        lib.addPlayEvent(new PlayEvent(4, 3, 102, 100, 400));

        assertThat(lib.getMostActiveListener()).isEqualTo(3);
    }
}
