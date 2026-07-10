package com.polarsmp.listeners;

import com.polarsmp.PolarSMP;
import com.polarsmp.core.ConfigManager;
import org.bukkit.World;
import org.bukkit.event.Listener;

import java.util.List;

/**
 * Listener that provides world-based filtering for PolarSMP game events.
 *
 * <p>Reads {@code world-filter.whitelist} and {@code world-filter.blacklist} from
 * {@code config.yml} to determine which worlds are eligible for PolarSMP mechanics
 * (PvP rank transfers, bounty processing, etc.).</p>
 *
 * <p>Filter precedence:</p>
 * <ol>
 *   <li>If the whitelist is non-empty, <em>only</em> worlds on that list are allowed.</li>
 *   <li>Else if the blacklist is non-empty, any world <em>not</em> on that list is allowed.</li>
 *   <li>Otherwise all worlds are allowed.</li>
 * </ol>
 *
 * <p>This class implements {@link Listener} so Bukkit registers it normally, but it
 * does not handle any specific event itself; it acts as a shared utility accessed by
 * other listeners via dependency injection.</p>
 *
 * @author ANSH9BOSS
 * @version 1.0.0
 */
public final class WorldListener implements Listener {

    /** Config path for the world whitelist. */
    private static final String KEY_WHITELIST = "world-filter.whitelist";

    /** Config path for the world blacklist. */
    private static final String KEY_BLACKLIST = "world-filter.blacklist";

    private final ConfigManager configManager;

    /**
     * Constructs a new WorldListener and logs the active world-filter configuration.
     *
     * @param plugin the PolarSMP plugin instance (used for logging and config access)
     */
    public WorldListener(final PolarSMP plugin) {
        this.configManager = plugin.getConfigManager();

        List<String> whitelist = configManager.getMainConfig().getStringList(KEY_WHITELIST);
        List<String> blacklist = configManager.getMainConfig().getStringList(KEY_BLACKLIST);

        if (!whitelist.isEmpty()) {
            plugin.getLogger().info("[WorldFilter] Whitelist mode active. Allowed worlds: " + whitelist);
        } else if (!blacklist.isEmpty()) {
            plugin.getLogger().info("[WorldFilter] Blacklist mode active. Blocked worlds: " + blacklist);
        } else {
            plugin.getLogger().info("[WorldFilter] No world filter configured – all worlds allowed.");
        }
    }

    /**
     * Determines whether the given world is eligible for PolarSMP game mechanics.
     *
     * <p>Reads the current whitelist and blacklist from config at call time so that
     * a hot-reload ({@code /polarsmp reload}) is immediately reflected without
     * restarting the plugin.</p>
     *
     * @param world the world to check; must not be {@code null}
     * @return {@code true} if PolarSMP mechanics should run in this world,
     *         {@code false} otherwise
     */
    public boolean isWorldAllowed(final World world) {
        List<String> whitelist = configManager.getMainConfig().getStringList(KEY_WHITELIST);
        List<String> blacklist = configManager.getMainConfig().getStringList(KEY_BLACKLIST);

        if (!whitelist.isEmpty()) {
            // Whitelist is active: only explicitly listed worlds pass
            return whitelist.contains(world.getName());
        }

        if (!blacklist.isEmpty()) {
            // Blacklist is active: any world not on the list passes
            return !blacklist.contains(world.getName());
        }

        // No filter configured – all worlds are allowed
        return true;
    }
}
