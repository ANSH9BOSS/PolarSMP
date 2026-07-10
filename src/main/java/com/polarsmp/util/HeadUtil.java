package com.polarsmp.util;

import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

/**
 * Utility class for fetching and caching player skull items.
 *
 * @author ANSH9BOSS
 * @version 1.0.0
 */
public final class HeadUtil {

    private static final ConcurrentHashMap<UUID, ItemStack> skullCache = new ConcurrentHashMap<>();

    private HeadUtil() {}

    /**
     * Returns a player head ItemStack for an OfflinePlayer.
     * Uses a cache to avoid redundant lookups.
     *
     * @param player the OfflinePlayer whose head to fetch
     * @return the skull ItemStack
     */
    public static ItemStack getSkull(final OfflinePlayer player) {
        if (player == null) return getDefaultSkull();
        return skullCache.computeIfAbsent(player.getUniqueId(), uuid -> {
            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(player);
                skull.setItemMeta(meta);
            }
            return skull;
        });
    }

    /**
     * Returns a generic skeleton skull for vacant/unknown players.
     *
     * @return a SKELETON_SKULL ItemStack
     */
    public static ItemStack getDefaultSkull() {
        return new ItemStack(Material.SKELETON_SKULL);
    }

    /**
     * Removes a player's skull from the cache (e.g., on name change).
     *
     * @param uuid the player's UUID
     */
    public static void invalidate(final UUID uuid) {
        skullCache.remove(uuid);
    }

    /**
     * Clears the entire skull cache.
     */
    public static void clearCache() {
        skullCache.clear();
    }
}
