package com.polarsmp.integrations;

import com.polarsmp.PolarSMP;
import com.polarsmp.core.ConfigManager;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.node.Node;
import org.bukkit.Bukkit;

import java.util.UUID;
import java.util.logging.Level;

/**
 * Optional LuckPerms integration for syncing the Rank 1 permission node.
 *
 * @author ANSH9BOSS
 * @version 1.0.0
 */
public final class LuckPermsHook {
    private final PolarSMP plugin;
    private final ConfigManager configManager;
    private LuckPerms luckPerms;
    private boolean available = false;

    /**
     * Constructs a new LuckPermsHook.
     *
     * @param plugin        the PolarSMP plugin instance
     * @param configManager the configuration manager
     */
    public LuckPermsHook(final PolarSMP plugin, final ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        var provider = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        if (provider != null) {
            luckPerms = provider.getProvider();
            available = true;
            plugin.getLogger().info("LuckPerms integration active.");
        } else {
            plugin.getLogger().info("LuckPerms not found - rank permission sync disabled.");
        }
    }

    /**
     * Adds the Rank 1 holder permission to the user.
     *
     * @param uuid the user's UUID
     */
    public void addRank1Permission(final UUID uuid) {
        if (!available) return;
        String node = configManager.getMainConfig().getString("luckperms.rank1-permission", "polarsmp.rank1");
        try {
            luckPerms.getUserManager().modifyUser(uuid, user -> {
                user.data().add(Node.builder(node).build());
            });
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to add Rank 1 permission via LuckPerms", e);
        }
    }

    /**
     * Removes the Rank 1 holder permission from the user.
     *
     * @param uuid the user's UUID
     */
    public void removeRank1Permission(final UUID uuid) {
        if (!available) return;
        String node = configManager.getMainConfig().getString("luckperms.rank1-permission", "polarsmp.rank1");
        try {
            luckPerms.getUserManager().modifyUser(uuid, user -> {
                user.data().remove(Node.builder(node).build());
            });
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to remove Rank 1 permission via LuckPerms", e);
        }
    }

    /** @return true if LuckPerms is available on the server */
    public boolean isAvailable() { return available; }
}
