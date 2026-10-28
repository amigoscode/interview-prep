package com.amigoscode.karat.games;

/**
 * One player's experience of a single match: the player the record belongs to, the opponent, the
 * outcome from this player's perspective, the player's score and when the match was played.
 */
public class MatchResult {
    public final int playerId;
    public final int opponentId;
    public final Outcome outcome;
    public final int score;
    public final int timestamp;

    public MatchResult(int playerId, int opponentId, Outcome outcome, int score, int timestamp) {
        this.playerId = playerId;
        this.opponentId = opponentId;
        this.outcome = outcome;
        this.score = score;
        this.timestamp = timestamp;
    }
}
