package com.polarsmp.gui;

import com.polarsmp.PolarSMP;
import com.polarsmp.core.ConfigManager;
import com.polarsmp.core.DataStore;
import com.polarsmp.core.PlayerDataCache;
import com.polarsmp.rank.RankManager;
import com.polarsmp.bounty.BountyManager;
import com.polarsmp.util.SoundUtil;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks open GUIs and routes click actions per player.
 *
 * @author ANSH9BOSS
 * @version 1.0.0
 */
public final class GuiManager {

    private final PolarSMP plugin;
    private final ConfigManager configManager;
    private final PlayerDataCache playerDataCache;
    private final RankManager rankManager;
    private final BountyManager bountyManager;
    private final DataStore dataStore;

    // UUID -> GUI type ("profile", "rewards", "shop", "rank_leaderboard", "bounty_leaderboard")
    private final ConcurrentHashMap<UUID, String> openGuis = new ConcurrentHashMap<>();

    // UUID -> extra GUI data (like target player UUID or current tab)
    private final ConcurrentHashMap<UUID, Object> guiData = new ConcurrentHashMap<>();

    /**
     * Constructs a new GuiManager.
     */
    public GuiManager(final PolarSMP plugin, final ConfigManager configManager,
                      final PlayerDataCache playerDataCache, final RankManager rankManager,
                      final BountyManager bountyManager, final DataStore dataStore) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.playerDataCache = playerDataCache;
        this.rankManager = rankManager;
        this.bountyManager = bountyManager;
        this.dataStore = dataStore;
    }

    /**
     * Opens the Profile GUI for a player (either self or target).
     */
    public void openProfileGui(final Player viewer, final Player target) {
        openGuis.put(viewer.getUniqueId(), "profile");
        guiData.put(viewer.getUniqueId(), target.getUniqueId());
        new ProfileGUI(plugin, configManager, playerDataCache, rankManager).open(viewer, target);
        SoundUtil.playSound(viewer, configManager.getSound("gui-open"));
    }

    /**
     * Opens the Rewards GUI.
     */
    public void openRewardsGui(final Player player) {
        openGuis.put(player.getUniqueId(), "rewards");
        new RewardsGUI(plugin, configManager, playerDataCache, dataStore).open(player);
        SoundUtil.playSound(player, configManager.getSound("gui-open"));
    }

    /**
     * Opens the Shop GUI.
     */
    public void openShopGui(final Player player) {
        openGuis.put(player.getUniqueId(), "shop");
        new ShopGUI(plugin, configManager, bountyManager, playerDataCache).open(player);
        SoundUtil.playSound(player, configManager.getSound("gui-open"));
    }

    /**
     * Opens the Rank Leaderboard GUI.
     */
    public void openRankLeaderboardGui(final Player player) {
        openGuis.put(player.getUniqueId(), "rank_leaderboard");
        new RankLeaderboardGUI(plugin, configManager, rankManager, dataStore).open(player);
        SoundUtil.playSound(player, configManager.getSound("gui-open"));
    }

    /**
     * Opens the Bounty Leaderboard GUI.
     */
    public void openBountyLeaderboardGui(final Player player) {
        openGuis.put(player.getUniqueId(), "bounty_leaderboard");
        new BountyLeaderboardGUI(plugin, configManager, dataStore).open(player);
        SoundUtil.playSound(player, configManager.getSound("gui-open"));
    }

    /**
     * Routes clicks from the inventory listener to the active GUI handler.
     */
    public void handleClick(final Player player, final int slot, final ItemStack item) {
        UUID uuid = player.getUniqueId();
        String type = openGuis.get(uuid);
        if (type == null) return;

        SoundUtil.playSound(player, configManager.getSound("gui-click"));

        switch (type) {
            case "profile":
                UUID targetUuid = (UUID) guiData.get(uuid);
                Player target = org.bukkit.Bukkit.getPlayer(targetUuid);
                if (slot == 49) {
                    player.closeInventory();
                } else if (slot == 13 && target != null) {
                    // Back button context if looking at other profile
                    if (!uuid.equals(targetUuid)) {
                        openProfileGui(player, player); // return to self
                    }
                }
                break;

            case "rewards":
                if (slot == 49) {
                    player.closeInventory();
                } else if (slot >= 9 && slot < 45) {
                    new RewardsGUI(plugin, configManager, playerDataCache, dataStore).handleClick(player, slot);
                }
                break;

            case "shop":
                if (slot == 49) {
                    player.closeInventory();
                } else if (slot >= 9 && slot < 45) {
                    new ShopGUI(plugin, configManager, bountyManager, playerDataCache).handleClick(player, slot);
                }
                break;

            case "rank_leaderboard":
                if (slot == 49) {
                    player.closeInventory();
                }
                break;

            case "bounty_leaderboard":
                new BountyLeaderboardGUI(plugin, configManager, dataStore).handleClick(player, slot, this);
                break;

            default:
                break;
        }
    }

    /**
     * Clears tracking for a player when their inventory closes.
     */
    public void onClose(final Player player) {
        UUID uuid = player.getUniqueId();
        if (openGuis.containsKey(uuid)) {
            openGuis.remove(uuid);
            guiData.remove(uuid);
            SoundUtil.playSound(player, configManager.getSound("gui-close"));
        }
    }

    /**
     * Closes the menus of all currently tracked players.
     */
    public void closeAll() {
        for (UUID uuid : openGuis.keySet()) {
            Player p = org.bukkit.Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.closeInventory();
            }
        }
        openGuis.clear();
        guiData.clear();
    }

    /** @return true if the player has an active PolarSMP GUI open */
    public boolean hasOpenGui(final UUID uuid) {
        return openGuis.containsKey(uuid);
    }

    /** Sets/updates extra GUI data for tracking tabs or pagination. */
    public void setGuiData(final UUID uuid, final Object data) {
        guiData.put(uuid, data);
    }

    /** Gets the extra GUI data associated with the player. */
    public Object getGuiData(final UUID uuid) {
        return guiData.get(uuid);
    }
}
