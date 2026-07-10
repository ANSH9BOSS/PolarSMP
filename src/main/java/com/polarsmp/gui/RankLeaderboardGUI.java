package com.polarsmp.gui;

import com.polarsmp.PolarSMP;
import com.polarsmp.bounty.BountyPlayer;
import com.polarsmp.core.ConfigManager;
import com.polarsmp.core.DataStore;
import com.polarsmp.rank.RankManager;
import com.polarsmp.rank.RankPerk;
import com.polarsmp.util.FormatUtil;
import com.polarsmp.util.HeadUtil;
import com.polarsmp.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Gold and Black themed Rank Leaderboard GUI displaying all 10 ranks.
 *
 * @author ANSH9BOSS
 * @version 1.0.0
 */
public final class RankLeaderboardGUI {

    private final PolarSMP plugin;
    private final ConfigManager configManager;
    private final RankManager rankManager;
    private final DataStore dataStore;

    /**
     * Constructs a new RankLeaderboardGUI.
     */
    public RankLeaderboardGUI(final PolarSMP plugin, final ConfigManager configManager,
                              final RankManager rankManager, final DataStore dataStore) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.rankManager = rankManager;
        this.dataStore = dataStore;
    }

    /**
     * Builds and opens the Rank Leaderboard GUI.
     */
    public void open(final Player player) {
        String titleMM = configManager.getMessagesConfig().getString("gui.rank-leaderboard-title", "<gold><bold>👑 PolarRank Leaderboard 👑</bold></gold>");
        Inventory inv = GuiBuilder.createInventory(PolarSMP.miniMessage().deserialize(titleMM), 54);

        // Border: Gold glass
        ItemStack border = GuiBuilder.createBorderItem(Material.YELLOW_STAINED_GLASS_PANE, Component.empty());
        GuiBuilder.fillBorder(inv, border);

        // Season Info (Slot 4)
        ItemStack infoItem;
        if (rankManager.isSeasonActive()) {
            long elapsed = System.currentTimeMillis() - rankManager.getSeasonStartTime();
            infoItem = new ItemBuilder(Material.NETHER_STAR)
                    .name("<gold><bold>Season Information</bold></gold>")
                    .loreFromMiniMessage(List.of(
                            "<gray>Status: <green>ACTIVE</green></gray>",
                            "<gray>Elapsed Time: <yellow>" + FormatUtil.formatDuration(elapsed) + "</yellow></gray>"
                    ))
                    .glowing(true)
                    .build();
        } else {
            infoItem = new ItemBuilder(Material.NETHER_STAR)
                    .name("<gold><bold>Season Information</bold></gold>")
                    .loreFromMiniMessage(List.of(
                            "<gray>Status: <red>INACTIVE / NOT STARTED</red></gray>"
                    ))
                    .build();
        }
        inv.setItem(4, infoItem);

        // Display slots
        // Ranks 1 to 5 at slots 10-14
        // Ranks 6 to 10 at slots 28-32
        int[] slots = {10, 11, 12, 13, 14, 28, 29, 30, 31, 32};
        Map<Integer, UUID> ranks = rankManager.getRankMap();

        for (int rankNum = 1; rankNum <= 10; rankNum++) {
            int inventorySlot = slots[rankNum - 1];
            UUID holderUuid = ranks.get(rankNum);

            if (holderUuid != null) {
                OfflinePlayer op = Bukkit.getOfflinePlayer(holderUuid);
                int currentRankNum = rankNum;

                // Load player stats from database to show live Coins/Bounty
                dataStore.loadPlayer(holderUuid).thenAccept(pData -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        BountyPlayer stats = pData != null ? pData : new BountyPlayer(holderUuid, op.getName());
                        long heldDuration = System.currentTimeMillis() - rankManager.getRankTimestamp(currentRankNum);

                        String crown = currentRankNum == 1 ? " 👑" : "";
                        RankPerk perk = rankManager.getPerk(currentRankNum);
                        String rankColor = perk != null ? perk.getColorFormat() : "<gold>";

                        ItemStack head = new ItemBuilder(HeadUtil.getSkull(op))
                                .name(rankColor + "<bold>Rank #" + currentRankNum + crown + "</bold></rankColor>")
                                .loreFromMiniMessage(List.of(
                                        "<gray>Holder: <gold>" + op.getName() + "</gold></gray>",
                                        "<gray>Coins: <yellow>🪙 " + FormatUtil.formatNumber(stats.getCoins()) + "</yellow></gray>",
                                        "<gray>Bounty: <red>💀 " + FormatUtil.formatNumber(stats.getBounty()) + "</red></gray>",
                                        "<gray>Held For: <aqua>" + FormatUtil.formatDuration(heldDuration) + "</aqua></gray>"
                                ))
                                .glowing(true)
                                .build();
                        inv.setItem(inventorySlot, head);
                    });
                });
            } else {
                // Vacant Rank
                ItemStack vacantItem = new ItemBuilder(Material.BARRIER)
                        .name("<red><bold>Rank #" + rankNum + "</bold></red>")
                        .loreFromMiniMessage(List.of(
                                configManager.getMessagesConfig().getString("gui.rank-vacant", "<gray><italic>VACANT</italic></gray>")
                        ))
                        .build();
                inv.setItem(inventorySlot, vacantItem);
            }
        }

        // Close Button (Slot 49)
        inv.setItem(49, GuiBuilder.createCloseButton());

        player.openInventory(inv);
    }
}
