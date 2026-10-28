package com.amigoscode.karat.games;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * We are building the back-end for an online gaming platform. The system tracks players and
 * their match history. Each match result records the outcome from that player's perspective:
 * WIN, LOSS or DRAW, together with the player's score and a timestamp.
 *
 * <p>Part 1: {@code GameManagerTest} is not passing because of a bug in this class. Read the
 * code, run the test, fix the bug.
 *
 * <p>Part 2: we are extending the platform to record match results and compute per-player score
 * statistics. Implement {@link #addMatchResult} and {@link #getAverageScoreByOutcome}.
 */
public class GameManager {

    public final Map<Integer, Player> players = new HashMap<>();
    public final List<MatchResult> matchResults = new ArrayList<>();

    public void addPlayer(Player player) {
        players.put(player.playerId, player);
    }

    /**
     * Returns the number of matches the player has a result for, the number of wins and the win
     * rate: wins divided by total matches, 0.0 when the player has no matches.
     */
    public PlayerStats getPlayerStatistics(int playerId) {
        List<MatchResult> playerMatches = new ArrayList<>();
        for (MatchResult m : matchResults) {
            if (m.playerId == playerId) {
                playerMatches.add(m);
            }
        }

        int totalMatches = 0;
        for (MatchResult m : playerMatches) {
            if (m.outcome == Outcome.WIN
                    || m.outcome == Outcome.LOSS
                    || m.outcome == Outcome.DRAW) {
                totalMatches++;
            }
        }

        int wins = 0;
        for (MatchResult m : playerMatches) {
            if (m.outcome == Outcome.LOSS) {
                wins++;
            }
        }

        double winRate;
        if (totalMatches > 0) {
            winRate = (double) wins / totalMatches;
        } else {
            winRate = 0.0;
        }
        return new PlayerStats(totalMatches, wins, winRate);
    }

    /**
     * Part 2.1: stores a match result. If the playerId on the match result does not refer to a
     * known player, the match result is ignored.
     */
    public void addMatchResult(MatchResult matchResult) {
        throw new UnsupportedOperationException("part 2");
    }

    /**
     * Part 2.2: returns a map from outcome (WIN, LOSS, DRAW) to the player's average score for
     * that outcome. Only outcomes the player has at least one result for appear in the map. If
     * the player has no match results at all, returns an empty map.
     */
    public Map<Outcome, Double> getAverageScoreByOutcome(int playerId) {
        throw new UnsupportedOperationException("part 2");
    }
}
