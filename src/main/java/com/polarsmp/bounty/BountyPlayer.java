package com.polarsmp.bounty;

import java.util.UUID;

/**
 * Data model representing a player's full PolarSMP statistics.
 *
 * <p>Stored in {@link com.polarsmp.core.PlayerDataCache} for in-memory access
 * and persisted to the database via {@link com.polarsmp.core.DataStore}.</p>
 *
 * @author ANSH9BOSS
 * @version 1.0.0
 */
public final class BountyPlayer {

    private final UUID uuid;
    private String name;
    private long coins;
    private long bounty;
    private int killStreak;
    private int highestStreak;
    private int totalKills;
    private int totalDeaths;

    /**
     * Constructs a fresh BountyPlayer with all stats at zero.
     *
     * @param uuid the player's UUID
     * @param name the player's current display name
     */
    public BountyPlayer(final UUID uuid, final String name) {
        this.uuid = uuid;
        this.name = name;
        this.coins = 0;
        this.bounty = 0;
        this.killStreak = 0;
        this.highestStreak = 0;
        this.totalKills = 0;
        this.totalDeaths = 0;
    }

    // ─── Getters ──────────────────────────────────────────────────

    /** @return the player's UUID */
    public UUID getUuid() { return uuid; }

    /** @return the player's name */
    public String getName() { return name; }

    /** @return the player's current coin balance */
    public long getCoins() { return coins; }

    /** @return the player's current bounty */
    public long getBounty() { return bounty; }

    /** @return the player's current kill streak */
    public int getKillStreak() { return killStreak; }

    /** @return the player's all-time highest kill streak */
    public int getHighestStreak() { return highestStreak; }

    /** @return the player's total PvP kills */
    public int getTotalKills() { return totalKills; }

    /** @return the player's total PvP deaths */
    public int getTotalDeaths() { return totalDeaths; }

    /**
     * Calculates and returns the K/D ratio.
     * Deaths are floored to 1 to prevent division by zero.
     *
     * @return K/D ratio rounded to 2 decimal places
     */
    public double getKdRatio() {
        return Math.round((double) totalKills / Math.max(1, totalDeaths) * 100.0) / 100.0;
    }

    // ─── Setters ──────────────────────────────────────────────────

    /** @param name the new player name */
    public void setName(final String name) { this.name = name; }

    /** @param coins the new coin balance */
    public void setCoins(final long coins) { this.coins = Math.max(0, coins); }

    /** @param bounty the new bounty value */
    public void setBounty(final long bounty) { this.bounty = Math.max(0, bounty); }

    /** @param killStreak the new kill streak */
    public void setKillStreak(final int killStreak) { this.killStreak = Math.max(0, killStreak); }

    /** @param highestStreak the new highest streak */
    public void setHighestStreak(final int highestStreak) { this.highestStreak = Math.max(0, highestStreak); }

    /** @param totalKills the new total kills */
    public void setTotalKills(final int totalKills) { this.totalKills = Math.max(0, totalKills); }

    /** @param totalDeaths the new total deaths */
    public void setTotalDeaths(final int totalDeaths) { this.totalDeaths = Math.max(0, totalDeaths); }

    // ─── Mutators ─────────────────────────────────────────────────

    /**
     * Adds coins to the player's balance.
     *
     * @param amount the amount to add (must be positive)
     */
    public void addCoins(final long amount) { this.coins += amount; }

    /**
     * Removes coins from the player's balance, floored at zero.
     *
     * @param amount the amount to remove
     */
    public void removeCoins(final long amount) { this.coins = Math.max(0, this.coins - amount); }

    /**
     * Adds bounty to the player.
     *
     * @param amount the bounty to add
     */
    public void addBounty(final long amount) { this.bounty += amount; }

    /**
     * Resets the player's kill streak to zero.
     */
    public void resetStreak() { this.killStreak = 0; }

    /**
     * Increments the kill streak by one and updates highest streak if exceeded.
     *
     * @return the new kill streak value
     */
    public int incrementStreak() {
        this.killStreak++;
        if (this.killStreak > this.highestStreak) {
            this.highestStreak = this.killStreak;
        }
        return this.killStreak;
    }

    /**
     * Increments total kills by one.
     */
    public void incrementKills() { this.totalKills++; }

    /**
     * Increments total deaths by one.
     */
    public void incrementDeaths() { this.totalDeaths++; }

    @Override
    public String toString() {
        return "BountyPlayer{uuid=" + uuid + ", name=" + name + ", coins=" + coins
                + ", bounty=" + bounty + ", streak=" + killStreak + "}";
    }
}
