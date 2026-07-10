package com.polarsmp.rank;

import java.util.UUID;

/**
 * Immutable data model representing a player's rank assignment.
 *
 * @param uuid           the ranked player's UUID
 * @param rankNumber     the rank number (1–10)
 * @param playerName     the player's display name at time of record
 * @param timestampGained Unix timestamp in ms when this rank was acquired
 *
 * @author ANSH9BOSS
 * @version 1.0.0
 */
public record RankPlayer(UUID uuid, int rankNumber, String playerName, long timestampGained) {

    /**
     * Returns a human-readable rank label.
     *
     * @return "Rank #N" string
     */
    public String getRankLabel() {
        return "Rank #" + rankNumber;
    }

    /**
     * Returns the duration this rank has been held in milliseconds.
     *
     * @return milliseconds since rank was gained
     */
    public long getHeldDurationMs() {
        return System.currentTimeMillis() - timestampGained;
    }
}
