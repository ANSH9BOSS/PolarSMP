package com.polarsmp.util;

import com.polarsmp.PolarSMP;
import com.polarsmp.core.ConfigManager;
import org.bukkit.Bukkit;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks combat-tagged players and handles combat log punishment logic.
 *
 * <p>Players who deal or receive PvP damage are added to the combat tag map.
 * A repeating task checks for expired tags every second. When a tagged player
 * disconnects, their data is processed immediately (rank transfer, bounty
 * steal) and they are marked for a notification on next login.</p>
 *
 * @author ANSH9BOSS
 * @version 1.0.0
 */
public final class CombatLogTracker {

    private final PolarSMP plugin;
    private final ConfigManager configManager;

    /** UUID → timestamp of when they were last tagged */
    private final ConcurrentHashMap<UUID, Long> combatTagged = new ConcurrentHashMap<>();

    /** victim UUID → last attacker UUID */
    private final ConcurrentHashMap<UUID, UUID> lastAttacker = new ConcurrentHashMap<>();

    /** Players who should receive a combat log notification on next join */
    private final ConcurrentHashMap<UUID, Boolean> pendingNotification = new ConcurrentHashMap<>();

    /**
     * Constructs a new CombatLogTracker and starts the cleanup task.
     *
     * @param plugin        the PolarSMP plugin instance
     * @param configManager the configuration manager
     */
    public CombatLogTracker(final PolarSMP plugin, final ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;

        // Start expiry checker every second
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::checkExpirations, 20L, 20L);
    }

    /**
     * Tags a player as in-combat and records their last attacker.
     *
     * @param playerUuid   the player being tagged
     * @param attackerUuid the UUID of the attacker (for bounty/rank transfer on log)
     */
    public void tag(final UUID playerUuid, final UUID attackerUuid) {
        if (!isEnabled()) return;
        combatTagged.put(playerUuid, System.currentTimeMillis());
        if (attackerUuid != null) {
            lastAttacker.put(playerUuid, attackerUuid);
        }
    }

    /**
     * Checks whether a player is currently combat-tagged.
     *
     * @param playerUuid the player's UUID
     * @return true if tagged and not yet expired
     */
    public boolean isTagged(final UUID playerUuid) {
        Long timestamp = combatTagged.get(playerUuid);
        if (timestamp == null) return false;
        long elapsed = System.currentTimeMillis() - timestamp;
        long duration = getCombatLogSeconds() * 1000L;
        return elapsed < duration;
    }

    /**
     * Removes a player from the combat tag map.
     *
     * @param playerUuid the player's UUID
     */
    public void untag(final UUID playerUuid) {
        combatTagged.remove(playerUuid);
        lastAttacker.remove(playerUuid);
    }

    /**
     * Returns the UUID of the last player who attacked the given player.
     *
     * @param victimUuid the victim's UUID
     * @return the attacker's UUID, or null if not tracked
     */
    public UUID getLastAttacker(final UUID victimUuid) {
        return lastAttacker.get(victimUuid);
    }

    /**
     * Marks a player to receive a combat log notification on their next login.
     *
     * @param playerUuid the player's UUID
     */
    public void markForNotification(final UUID playerUuid) {
        pendingNotification.put(playerUuid, true);
    }

    /**
     * Checks whether a player has a pending combat log notification and clears it.
     *
     * @param playerUuid the player's UUID
     * @return true if there is a pending notification (clears it)
     */
    public boolean shouldNotify(final UUID playerUuid) {
        return pendingNotification.remove(playerUuid) != null;
    }

    /**
     * Returns whether the combat log system is enabled in config.
     *
     * @return true if enabled
     */
    public boolean isEnabled() {
        return configManager.getMainConfig().getBoolean("combat-log.enabled", true);
    }

    /**
     * Returns the combat tag duration in seconds.
     *
     * @return seconds of combat tag duration
     */
    public long getCombatLogSeconds() {
        return configManager.getMainConfig().getLong("combat-log.timer-seconds", 15);
    }

    /** Checks all tagged players and removes expired entries. */
    private void checkExpirations() {
        long now = System.currentTimeMillis();
        long duration = getCombatLogSeconds() * 1000L;
        combatTagged.entrySet().removeIf(entry -> now - entry.getValue() >= duration);
    }
}
