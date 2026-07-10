package com.polarsmp.util;

import com.polarsmp.PolarSMP;
import com.polarsmp.core.ConfigManager;
import com.polarsmp.core.DataStore;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Enforces the anti-farm kill cooldown by querying kill_log for recent duplicates.
 *
 * @author ANSH9BOSS
 * @version 1.0.0
 */
public final class AntiFarmTracker {

    private final PolarSMP plugin;
    private final ConfigManager configManager;
    private final DataStore dataStore;

    /**
     * Constructs a new AntiFarmTracker.
     *
     * @param plugin        the PolarSMP plugin instance
     * @param configManager the configuration manager
     * @param dataStore     the data store for kill log queries
     */
    public AntiFarmTracker(final PolarSMP plugin, final ConfigManager configManager,
                           final DataStore dataStore) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.dataStore = dataStore;
    }

    /**
     * Checks asynchronously whether the killer is on cooldown for killing this victim.
     *
     * @param killerUuid the killer's UUID
     * @param victimUuid the victim's UUID
     * @return a CompletableFuture resolving to true if on cooldown (rewards denied)
     */
    public CompletableFuture<Boolean> isOnCooldown(final UUID killerUuid, final UUID victimUuid) {
        if (!configManager.getMainConfig().getBoolean("anti-farm.enabled", true)) {
            return CompletableFuture.completedFuture(false);
        }
        long cooldownSeconds = configManager.getMainConfig().getLong("anti-farm.cooldown-seconds", 300);
        long cutoff = System.currentTimeMillis() - (cooldownSeconds * 1000L);
        return dataStore.wasRecentlyKilled(killerUuid, victimUuid, cutoff);
    }

    /**
     * Returns the configured cooldown duration in seconds.
     *
     * @return cooldown in seconds
     */
    public long getCooldownSeconds() {
        return configManager.getMainConfig().getLong("anti-farm.cooldown-seconds", 300);
    }

    /**
     * Returns whether anti-farm protection is enabled.
     *
     * @return true if enabled
     */
    public boolean isEnabled() {
        return configManager.getMainConfig().getBoolean("anti-farm.enabled", true);
    }
}
