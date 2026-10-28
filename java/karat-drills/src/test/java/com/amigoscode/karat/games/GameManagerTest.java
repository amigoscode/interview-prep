package com.amigoscode.karat.games;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * This is not a complete test suite, but tests some basic functionality of the code and shows how
 * to use it. One test fails on purpose: that is part 1.
 */
class GameManagerTest {

    @Test
    void testGetPlayerStatistics() {
        GameManager gm = new GameManager();
        gm.addPlayer(new Player(1, "player1"));
        gm.addPlayer(new Player(2, "player2"));

        gm.matchResults.add(new MatchResult(1, 2, Outcome.WIN, 80, 1000));
        gm.matchResults.add(new MatchResult(1, 2, Outcome.LOSS, 50, 2000));
        gm.matchResults.add(new MatchResult(1, 2, Outcome.DRAW, 60, 3000));
        gm.matchResults.add(new MatchResult(1, 2, Outcome.WIN, 90, 4000));

        PlayerStats stats = gm.getPlayerStatistics(1);
        assertThat(stats.totalMatches).isEqualTo(4);
        assertThat(stats.wins).isEqualTo(2);
        assertThat(stats.winRate).isCloseTo(0.5, within(1e-4));

        gm.matchResults.add(new MatchResult(2, 1, Outcome.DRAW, 60, 1000));
        gm.matchResults.add(new MatchResult(2, 1, Outcome.DRAW, 60, 2000));

        PlayerStats stats2 = gm.getPlayerStatistics(2);
        assertThat(stats2.totalMatches).isEqualTo(2);
        assertThat(stats2.wins).isEqualTo(0);
        assertThat(stats2.winRate).isCloseTo(0.0, within(1e-4));
    }

    @Test
    void testAddMatchResult() {
        GameManager gm = new GameManager();
        gm.addPlayer(new Player(1, "player1"));
        gm.addPlayer(new Player(2, "player2"));

        gm.addMatchResult(new MatchResult(1, 2, Outcome.WIN, 80, 1000));
        gm.addMatchResult(new MatchResult(2, 1, Outcome.LOSS, 50, 1000));
        gm.addMatchResult(new MatchResult(99, 1, Outcome.WIN, 100, 2000)); // no player 99: ignored

        assertThat(gm.matchResults).hasSize(2);
    }

    @Test
    void testGetAverageScoreByOutcome() {
        GameManager gm = new GameManager();
        gm.addPlayer(new Player(1, "player1"));
        gm.addPlayer(new Player(2, "player2"));

        gm.addMatchResult(new MatchResult(1, 2, Outcome.WIN, 80, 1000));
        gm.addMatchResult(new MatchResult(2, 1, Outcome.LOSS, 50, 1000));
        gm.addMatchResult(new MatchResult(1, 2, Outcome.WIN, 90, 2000));
        gm.addMatchResult(new MatchResult(2, 1, Outcome.LOSS, 60, 2000));
        gm.addMatchResult(new MatchResult(1, 2, Outcome.DRAW, 70, 3000));
        gm.addMatchResult(new MatchResult(2, 1, Outcome.DRAW, 70, 3000));

        Map<Outcome, Double> avg1 = gm.getAverageScoreByOutcome(1);
        assertThat(avg1.get(Outcome.WIN)).isCloseTo(85.0, within(1e-4));
        assertThat(avg1.get(Outcome.DRAW)).isCloseTo(70.0, within(1e-4));
        assertThat(avg1).doesNotContainKey(Outcome.LOSS); // player1 has no losses

        Map<Outcome, Double> avg2 = gm.getAverageScoreByOutcome(2);
        assertThat(avg2.get(Outcome.LOSS)).isCloseTo(55.0, within(1e-4));
        assertThat(avg2.get(Outcome.DRAW)).isCloseTo(70.0, within(1e-4));
        assertThat(avg2).doesNotContainKey(Outcome.WIN); // player2 has no wins

        gm.addPlayer(new Player(3, "player3"));
        assertThat(gm.getAverageScoreByOutcome(3)).isEmpty();
    }

    @Test
    void playerWithNoMatchesHasZeroStatistics() {
        GameManager gm = new GameManager();
        gm.addPlayer(new Player(1, "player1"));

        PlayerStats stats = gm.getPlayerStatistics(1);
        assertThat(stats.totalMatches).isZero();
        assertThat(stats.wins).isZero();
        assertThat(stats.winRate).isZero();
    }

    @Test
    void averageScoreForUnknownPlayerIsEmpty() {
        GameManager gm = new GameManager();
        gm.addPlayer(new Player(1, "player1"));
        gm.addMatchResult(new MatchResult(1, 2, Outcome.WIN, 80, 1000));

        assertThat(gm.getAverageScoreByOutcome(42)).isEmpty();
    }

    @Test
    void averageScoreOnlyCountsThatPlayersResults() {
        GameManager gm = new GameManager();
        gm.addPlayer(new Player(1, "player1"));
        gm.addPlayer(new Player(2, "player2"));
        gm.addMatchResult(new MatchResult(1, 2, Outcome.WIN, 80, 1000));
        gm.addMatchResult(new MatchResult(2, 1, Outcome.WIN, 100, 1000));

        Map<Outcome, Double> avg1 = gm.getAverageScoreByOutcome(1);
        assertThat(avg1).containsOnlyKeys(Outcome.WIN);
        assertThat(avg1.get(Outcome.WIN)).isCloseTo(80.0, within(1e-4));
    }
}
