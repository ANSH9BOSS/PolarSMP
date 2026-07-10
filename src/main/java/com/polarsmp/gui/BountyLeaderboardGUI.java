package com.polarsmp.gui;

import com.polarsmp.PolarSMP;
import com.polarsmp.bounty.BountyPlayer;
import com.polarsmp.core.ConfigManager;
import com.polarsmp.core.DataStore;
import com.polarsmp.util.FormatUtil;
import com.polarsmp.util.HeadUtil;
import com.polarsmp.util.ItemBuilder;
import com.polarsmp.util.SoundUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * Red and Black themed Bounty Leaderboard GUI with four tabs:
 * Coins, Bounties, Streaks, Kills.
 *
 * @author ANSH9BOSS
 * @version 1.0.0
 */
public final class BountyLeaderboardGUI {

    private final PolarSMP plugin;
    private final ConfigManager configManager;
    private final DataStore dataStore;

    /**
     * Active tabs enum.
     */
    public enum Tab {
        COINS, BOUNTY, STREAK, KILLS
    }

    /**
     * Constructs a new BountyLeaderboardGUI.
     */
    public BountyLeaderboardGUI(final PolarSMP plugin, final ConfigManager configManager,
                                 final DataStore dataStore) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.dataStore = dataStore;
    }

    /**
     * Builds and opens the Bounty Leaderboard GUI with COINS active by default.
     *
     * @param player the player opening the menu
     */
    public void open(final Player player) {
        open(player, Tab.COINS);
    }

    /**
     * Opens the leaderboard GUI displaying a specific active tab.
     */
    public void open(final Player player, final Tab tab) {
        String titleMM = configManager.getMessagesConfig().getString("gui.bounty-leaderboard-title", "<red><bold>💀 PolarBounty Leaderboard 💀</bold></red>");
        Inventory inv = GuiBuilder.createInventory(PolarSMP.miniMessage().deserialize(titleMM), 54);

        // Border: Red glass
        ItemStack border = GuiBuilder.createBorderItem(Material.RED_STAINED_GLASS_PANE, Component.empty());
        GuiBuilder.fillBorder(inv, border);

        // Set Tab data in GuiManager for tracking
        plugin.getGuiManager().setGuiData(player.getUniqueId(), tab);

        // 4 Tabs at slots 0, 1, 2, 3
        ItemStack coinsTab = new ItemBuilder(Material.GOLD_INGOT)
                .name(configManager.getMessagesConfig().getString("gui.tab-coins", "<gold><bold>🪙 Top Coins</bold></gold>"))
                .glowing(tab == Tab.COINS)
                .build();
        inv.setItem(0, coinsTab);

        ItemStack bountyTab = new ItemBuilder(Material.RED_DYE)
                .name(configManager.getMessagesConfig().getString("gui.tab-bounty", "<red><bold>💀 Top Bounties</bold></red>"))
                .glowing(tab == Tab.BOUNTY)
                .build();
        inv.setItem(1, bountyTab);

        ItemStack streakTab = new ItemBuilder(Material.BLAZE_ROD)
                .name(configManager.getMessagesConfig().getString("gui.tab-streak", "<yellow><bold>🔥 Top Streaks</bold></yellow>"))
                .glowing(tab == Tab.STREAK)
                .build();
        inv.setItem(2, streakTab);

        ItemStack killsTab = new ItemBuilder(Material.DIAMOND_SWORD)
                .name(configManager.getMessagesConfig().getString("gui.tab-kills", "<green><bold>⚔ Most Kills</bold></green>"))
                .glowing(tab == Tab.KILLS)
                .build();
        inv.setItem(3, killsTab);

        // Close Button (Slot 49)
        inv.setItem(49, GuiBuilder.createCloseButton());

        player.openInventory(inv);

        // Fetch and display entries
        refreshEntries(player, inv, tab);
    }

    /**
     * Handles slot-based tab switching and close buttons.
     */
    public void handleClick(final Player player, final int slot, final GuiManager manager) {
        if (slot == 49) {
            player.closeInventory();
            return;
        }

        Tab newTab = null;
        if (slot == 0) newTab = Tab.COINS;
        else if (slot == 1) newTab = Tab.BOUNTY;
        else if (slot == 2) newTab = Tab.STREAK;
        else if (slot == 3) newTab = Tab.KILLS;

        if (newTab != null) {
            SoundUtil.playSound(player, configManager.getSound("tab-switch"));
            open(player, newTab);
        }
    }

    /**
     * Refreshes the top entries in the inventory grid slots 9 through 17.
     */
    private void refreshEntries(final Player player, final Inventory inv, final Tab tab) {
        // Clear slots 9-17 first
        for (int i = 9; i <= 17; i++) {
            inv.setItem(i, null);
        }

        // Fetch top 9 entries from database async
        var future = switch (tab) {
            case COINS -> dataStore.getTopByCoins(9);
            case BOUNTY -> dataStore.getTopByBounty(9);
            case STREAK -> dataStore.getTopByStreak(9);
            case KILLS -> dataStore.getTopByKills(9);
        };

        future.thenAccept(list -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                int index = 0;
                for (BountyPlayer entry : list) {
                    OfflinePlayer op = Bukkit.getOfflinePlayer(entry.getUuid());

                    String valueStr = switch (tab) {
                        case COINS -> "<gold>🪙 " + FormatUtil.formatNumber(entry.getCoins()) + " Coins</gold>";
                        case BOUNTY -> "<red>💀 " + FormatUtil.formatNumber(entry.getBounty()) + " Bounty</red>";
                        case STREAK -> "<yellow>🔥 " + entry.getHighestStreak() + " Streak</yellow>";
                        case KILLS -> "<green>⚔ " + entry.getTotalKills() + " Kills</green>";
                    };

                    ItemStack head = new ItemBuilder(HeadUtil.getSkull(op))
                            .name("<gold><bold>#" + (index + 1) + " " + entry.getName() + "</bold></gold>")
                            .loreFromMiniMessage(List.of(
                                    valueStr
                            ))
                            .glowing(index == 0) // Glow for the first place
                            .build();

                    inv.setItem(9 + index, head);
                    index++;
                }

                // Fill empty spots with vacant indicators
                for (int i = index; i < 9; i++) {
                    ItemStack vacant = new ItemBuilder(Material.BARRIER)
                            .name("<gray><italic>Empty Slot</italic></gray>")
                            .build();
                    inv.setItem(9 + i, vacant);
                }
            });
        }).exceptionally(ex -> {
            plugin.getLogger().severe("Failed to load leaderboard stats: " + ex.getMessage());
            return null;
        });
    }
}
