package com.polarsmp.core;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Interface defining all asynchronous database operations for PolarSMP.
 *
 * <p>All methods return {@link CompletableFuture} instances that are executed
 * on a thread other than the main server thread. Callers must synchronise
 * back to the main thread before accessing the Bukkit API with results.</p>
 *
 * <p>Implementations: {@link SQLiteDataStore}, {@link MySQLDataStore}.</p>
 *
 * @author ANSH9BOSS
 * @version 1.0.0
 */
public interface DataStore {

    // ─── Player data ──────────────────────────────────────────────

    /**
     * Loads full player stats from the database.
     *
     * @param uuid the player's UUID
     * @return a future resolving to the player data, or null if not found
     */
    CompletableFuture<com.polarsmp.bounty.BountyPlayer> loadPlayer(UUID uuid);

    /**
     * Persists a player's stats to the database.
     *
     * @param player the player data model to save
     * @return a future that completes when the save is done
     */
    CompletableFuture<Void> savePlayer(com.polarsmp.bounty.BountyPlayer player);

    // ─── Rank data ────────────────────────────────────────────────

    /**
     * Loads the stored rank number for a player.
     *
     * @param uuid the player's UUID
     * @return a future resolving to their rank (1–10), or null if unranked
     */
    CompletableFuture<Integer> loadRank(UUID uuid);

    /**
     * Persists a player's rank assignment.
     *
     * @param uuid       the player's UUID
     * @param rankNumber the rank number (1–10), or null to clear the rank
     * @return a future that completes when done
     */
    CompletableFuture<Void> saveRank(UUID uuid, Integer rankNumber);

    /**
     * Loads all current rank assignments as a list of {@link com.polarsmp.rank.RankPlayer}.
     *
     * @return a future resolving to all rank holders
     */
    CompletableFuture<List<com.polarsmp.rank.RankPlayer>> loadAllRanks();

    // ─── Rewards ──────────────────────────────────────────────────

    /**
     * Records that a player has claimed a specific reward.
     *
     * @param uuid     the player's UUID
     * @param rewardId the reward's unique identifier
     * @return a future that completes when done
     */
    CompletableFuture<Void> saveRewardClaimed(UUID uuid, String rewardId);

    /**
     * Checks whether a player has already claimed a specific reward.
     *
     * @param uuid     the player's UUID
     * @param rewardId the reward's unique identifier
     * @return a future resolving to true if the reward was already claimed
     */
    CompletableFuture<Boolean> hasRewardClaimed(UUID uuid, String rewardId);

    // ─── Kill log ─────────────────────────────────────────────────

    /**
     * Logs a PvP kill to the kill_log table for anti-farm tracking.
     *
     * @param killerUuid the killer's UUID
     * @param victimUuid the victim's UUID
     * @param timestamp  the kill timestamp in milliseconds
     * @return a future that completes when done
     */
    CompletableFuture<Void> logKill(UUID killerUuid, UUID victimUuid, long timestamp);

    /**
     * Checks whether the killer recently killed the victim since the given timestamp.
     *
     * @param killerUuid      the killer's UUID
     * @param victimUuid      the victim's UUID
     * @param cutoffTimestamp kills at or after this timestamp count as "recent"
     * @return a future resolving to true if a recent kill exists
     */
    CompletableFuture<Boolean> wasRecentlyKilled(UUID killerUuid, UUID victimUuid, long cutoffTimestamp);

    // ─── Leaderboards ─────────────────────────────────────────────

    /**
     * Retrieves the top players sorted by total coins descending.
     *
     * @param limit maximum number of results
     * @return a future resolving to the sorted list
     */
    CompletableFuture<List<com.polarsmp.bounty.BountyPlayer>> getTopByCoins(int limit);

    /**
     * Retrieves the top players sorted by current bounty descending.
     *
     * @param limit maximum number of results
     * @return a future resolving to the sorted list
     */
    CompletableFuture<List<com.polarsmp.bounty.BountyPlayer>> getTopByBounty(int limit);

    /**
     * Retrieves the top players sorted by highest kill streak descending.
     *
     * @param limit maximum number of results
     * @return a future resolving to the sorted list
     */
    CompletableFuture<List<com.polarsmp.bounty.BountyPlayer>> getTopByStreak(int limit);

    /**
     * Retrieves the top players sorted by total kills descending.
     *
     * @param limit maximum number of results
     * @return a future resolving to the sorted list
     */
    CompletableFuture<List<com.polarsmp.bounty.BountyPlayer>> getTopByKills(int limit);

    /**
     * Closes the underlying connection pool and releases all resources.
     */
    void close();
}
