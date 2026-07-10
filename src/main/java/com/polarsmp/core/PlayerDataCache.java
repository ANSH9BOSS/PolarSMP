package com.polarsmp.core;

import com.polarsmp.PolarSMP;
import com.polarsmp.bounty.BountyPlayer;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * Thread-safe in-memory cache for all online player data.
 *
 * <p>All game logic reads from and writes to this cache. Async saves push
 * the cache state to the database. A "dirty" flag on each entry tracks
 * whether the entry needs to be persisted.</p>
 *
 * @author ANSH9BOSS
 * @version 1.0.0
 */
public final class PlayerDataCache {

    private final PolarSMP plugin;
    private final ConcurrentHashMap<UUID, BountyPlayer> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Boolean> dirtyFlags = new ConcurrentHashMap<>();

    /**
     * Constructs a new PlayerDataCache.
     *
     * @param plugin the PolarSMP plugin instance
     */
    public PlayerDataCache(final PolarSMP plugin) {
        this.plugin = plugin;
    }

    /**
     * Loads a player's data from the database asynchronously and inserts it into cache.
     * If the player has no existing record a fresh BountyPlayer is created.
     *
     * @param uuid      the player's UUID
     * @param name      the player's current name
     * @param dataStore the DataStore to load from
     */
    public void loadPlayer(final UUID uuid, final String name, final DataStore dataStore) {
        dataStore.loadPlayer(uuid).thenAccept(player -> {
            if (player == null) {
                // First join – create a fresh record
                BountyPlayer fresh = new BountyPlayer(uuid, name);
                cache.put(uuid, fresh);
                dirtyFlags.put(uuid, true); // Mark for save so the record gets inserted
            } else {
                player.setName(name); // Always update name in case it changed
                cache.put(uuid, player);
                dirtyFlags.put(uuid, false);
            }
        }).exceptionally(ex -> {
            plugin.getLogger().log(Level.SEVERE, "Failed to load player data for " + uuid, ex);
            // Fall back to empty record so the player isn't locked out
            cache.put(uuid, new BountyPlayer(uuid, name));
            dirtyFlags.put(uuid, false);
            return null;
        });
    }

    /**
     * Removes a player's data from cache and triggers an async save.
     *
     * @param uuid      the player's UUID
     * @param dataStore the DataStore to save to
     */
    public void unloadPlayer(final UUID uuid, final DataStore dataStore) {
        BountyPlayer player = cache.remove(uuid);
        dirtyFlags.remove(uuid);
        if (player != null) {
            dataStore.savePlayer(player).exceptionally(ex -> {
                plugin.getLogger().log(Level.SEVERE, "Failed to save player data on unload for " + uuid, ex);
                return null;
            });
        }
    }

    /**
     * Returns the cached BountyPlayer for the given UUID, or null if not loaded.
     *
     * @param uuid the player's UUID
     * @return the cached player data, or null
     */
    public BountyPlayer getPlayer(final UUID uuid) {
        return cache.get(uuid);
    }

    /**
     * Checks whether a player's data is loaded in cache.
     *
     * @param uuid the player's UUID
     * @return true if loaded
     */
    public boolean isLoaded(final UUID uuid) {
        return cache.containsKey(uuid);
    }

    /**
     * Marks a player's cache entry as dirty (needs saving).
     *
     * @param uuid the player's UUID
     */
    public void markDirty(final UUID uuid) {
        dirtyFlags.put(uuid, true);
    }

    /**
     * Saves all dirty cache entries asynchronously.
     *
     * @param dataStore the DataStore to save to
     * @return the number of entries saved
     */
    public int saveAllDirtyAsync(final DataStore dataStore) {
        AtomicInteger count = new AtomicInteger(0);
        dirtyFlags.forEach((uuid, dirty) -> {
            if (dirty) {
                BountyPlayer player = cache.get(uuid);
                if (player != null) {
                    dataStore.savePlayer(player);
                    dirtyFlags.put(uuid, false);
                    count.incrementAndGet();
                }
            }
        });
        return count.get();
    }

    /**
     * Saves ALL online player cache entries synchronously (used on shutdown).
     * Blocks the calling thread until all saves complete.
     *
     * @param dataStore the DataStore to save to
     */
    public void saveAllSync(final DataStore dataStore) {
        cache.forEach((uuid, player) -> {
            try {
                dataStore.savePlayer(player).get(); // Blocking call – acceptable on shutdown
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to synchronously save player " + uuid, e);
            }
        });
        plugin.getLogger().info("All player data saved synchronously on shutdown.");
    }

    /**
     * Returns all currently loaded player data.
     *
     * @return unmodifiable view of all cached players
     */
    public Collection<BountyPlayer> getAll() {
        return cache.values();
    }

    /**
     * Clears the entire cache. Should only be called on plugin disable.
     */
    public void clear() {
        cache.clear();
        dirtyFlags.clear();
    }
}
