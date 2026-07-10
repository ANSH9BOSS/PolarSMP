package com.polarsmp.gui;

import com.polarsmp.PolarSMP;
import com.polarsmp.bounty.BountyPlayer;
import com.polarsmp.core.ConfigManager;
import com.polarsmp.core.PlayerDataCache;
import com.polarsmp.rank.RankManager;
import com.polarsmp.rank.RankPerk;
import com.polarsmp.util.FormatUtil;
import com.polarsmp.util.HeadUtil;
import com.polarsmp.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * Premium 54-slot Profile GUI showing all player statistics.
 * Theme: Dark Blue and Gold.
 *
 * @author ANSH9BOSS
 * @version 1.0.0
 */
public final class ProfileGUI {

    private final PolarSMP plugin;
    private final ConfigManager configManager;
    private final PlayerDataCache playerDataCache;
    private final RankManager rankManager;

    /**
     * Constructs a new ProfileGUI.
     */
    public ProfileGUI(final PolarSMP plugin, final ConfigManager configManager,
                      final PlayerDataCache playerDataCache, final RankManager rankManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.playerDataCache = playerDataCache;
        this.rankManager = rankManager;
    }

    /**
     * Builds and opens the Profile GUI.
     *
     * @param viewer the player viewing the inventory
     * @param target the player whose profile statistics are being viewed
     */
    public void open(final Player viewer, final Player target) {
        String titleMM = configManager.getMessagesConfig().getString("gui.profile-title", "<dark_blue><bold>✦ Player Profile ✦</bold></dark_blue>");
        Inventory inv = GuiBuilder.createInventory(PolarSMP.miniMessage().deserialize(titleMM), 54);

        // Fill border with dark blue stained glass
        ItemStack border = GuiBuilder.createBorderItem(Material.BLUE_STAINED_GLASS_PANE, Component.empty());
        GuiBuilder.fillBorder(inv, border);

        BountyPlayer stats = playerDataCache.getPlayer(target.getUniqueId());
        if (stats == null) return;

        // Player Head (Slot 13)
        Integer rankNum = rankManager.getRank(target.getUniqueId());
        String rankLabel = rankNum != null ? "Rank #" + rankNum : "Unranked";
        String color = rankNum != null ? rankManager.getPerk(rankNum).getColorFormat() : "<gray>";

        ItemStack skull = new ItemBuilder(HeadUtil.getSkull(target))
                .name("<gold><bold>" + target.getName() + "</bold></gold>")
                .loreFromMiniMessage(List.of(
                        "<gray>Role: " + color + rankLabel + "</gray>"
                ))
                .build();
        inv.setItem(13, skull);

        // 1. Coins (Slot 10)
        ItemStack coins = new ItemBuilder(Material.GOLD_INGOT)
                .name("<gold><bold>Coins</bold></gold>")
                .loreFromMiniMessage(List.of(
                        "<gray>Current Balance:</gray>",
                        "<gold>🪙 " + FormatUtil.formatNumber(stats.getCoins()) + " Coins</gold>"
                ))
                .glowing(true)
                .build();
        inv.setItem(10, coins);

        // 2. Bounty (Slot 12)
        ItemStack bounty = new ItemBuilder(Material.RED_DYE)
                .name("<red><bold>Bounty</bold></red>")
                .loreFromMiniMessage(List.of(
                        "<gray>Active Bounty:</gray>",
                        "<red>💀 " + FormatUtil.formatNumber(stats.getBounty()) + " Coins</red>"
                ))
                .glowing(true)
                .build();
        inv.setItem(12, bounty);

        // 3. Kills (Slot 14)
        ItemStack kills = new ItemBuilder(Material.DIAMOND_SWORD)
                .name("<green><bold>Total Kills</bold></green>")
                .loreFromMiniMessage(List.of(
                        "<gray>Player Eliminations:</gray>",
                        "<green>⚔ " + stats.getTotalKills() + " Kills</green>"
                ))
                .glowing(true)
                .build();
        inv.setItem(14, kills);

        // 4. Deaths (Slot 16)
        ItemStack deaths = new ItemBuilder(Material.SKELETON_SKULL)
                .name("<red><bold>Total Deaths</bold></red>")
                .loreFromMiniMessage(List.of(
                        "<gray>Player Deaths:</gray>",
                        "<red>💀 " + stats.getTotalDeaths() + " Deaths</red>"
                ))
                .build();
        inv.setItem(16, deaths);

        // 5. K/D Ratio (Slot 20)
        ItemStack kd = new ItemBuilder(Material.COMPASS)
                .name("<aqua><bold>K/D Ratio</bold></aqua>")
                .loreFromMiniMessage(List.of(
                        "<gray>Combat Performance:</gray>",
                        "<aqua>📊 " + FormatUtil.formatKD(stats.getTotalKills(), stats.getTotalDeaths()) + " K/D</aqua>"
                ))
                .glowing(true)
                .build();
        inv.setItem(20, kd);

        // 6. Current Streak (Slot 22)
        ItemStack streak = new ItemBuilder(Material.BLAZE_ROD)
                .name("<yellow><bold>Current Streak</bold></yellow>")
                .loreFromMiniMessage(List.of(
                        "<gray>Active Kill Streak:</gray>",
                        "<yellow>🔥 " + stats.getKillStreak() + " Kills</yellow>"
                ))
                .glowing(true)
                .build();
        inv.setItem(22, streak);

        // 7. Highest Streak (Slot 24)
        ItemStack highestStreak = new ItemBuilder(Material.NETHER_STAR)
                .name("<gold><bold>Highest Streak</bold></gold>")
                .loreFromMiniMessage(List.of(
                        "<gray>All-Time High Streak:</gray>",
                        "<gold>⭐ " + stats.getHighestStreak() + " Kills</gold>"
                ))
                .glowing(true)
                .build();
        inv.setItem(24, highestStreak);

        // 8. Rewards Progress (Slot 28)
        int completedRewards = getCompletedCount(target);
        int totalRewards = configManager.getRewardsConfig().getMapList("rewards").size();
        String progressBar = FormatUtil.generateProgressBar(completedRewards, totalRewards, 10, '■', '□', "<green>", "<dark_gray>");
        ItemStack progress = new ItemBuilder(Material.WRITTEN_BOOK)
                .name("<green><bold>Milestone Rewards Progress</bold></green>")
                .loreFromMiniMessage(List.of(
                        "<gray>Milestones Completed:</gray>",
                        "<green>🏆 " + completedRewards + " of " + totalRewards + " (" + FormatUtil.formatPercent(completedRewards, totalRewards) + ")</green>",
                        "",
                        progressBar
                ))
                .glowing(true)
                .build();
        inv.setItem(28, progress);

        // 9. Current Rank (Slot 30)
        String rankColor = rankNum != null ? rankManager.getPerk(rankNum).getColorFormat() : "<gray>";
        String rankDisplay = rankNum != null ? rankManager.getPerk(rankNum).getPrefixFormat() : "Unranked";
        ItemStack rankItem = new ItemBuilder(Material.NAME_TAG)
                .name("<gold><bold>Current Rank</bold></gold>")
                .loreFromMiniMessage(List.of(
                        "<gray>SMP Exclusive Rank:</gray>",
                        rankColor + rankDisplay
                ))
                .glowing(true)
                .build();
        inv.setItem(30, rankItem);

        // 10. Total Earnings / Emerald (Slot 34)
        ItemStack allTime = new ItemBuilder(Material.EMERALD)
                .name("<emerald><bold>Total Earnings</bold></emerald>")
                .loreFromMiniMessage(List.of(
                        "<gray>Lifetime Economy Rank:</gray>",
                        "<emerald>🟢 Ranked earner</emerald>"
                ))
                .glowing(true)
                .build();
        inv.setItem(34, allTime);

        // Bottom Controls (Slot 49)
        if (viewer.getUniqueId().equals(target.getUniqueId())) {
            inv.setItem(49, GuiBuilder.createCloseButton());
        } else {
            // Return back button if inspecting other
            ItemStack backButton = new ItemBuilder(Material.ARROW)
                    .name(configManager.getMessagesConfig().getString("gui.back-button", "<gray>◀ Back</gray>"))
                    .build();
            inv.setItem(49, backButton);
        }

        viewer.openInventory(inv);
    }

    private int getCompletedCount(final Player player) {
        int count = 0;
        List<java.util.Map<?, ?>> rewards = configManager.getRewardsConfig().getMapList("rewards");
        for (var map : rewards) {
            String reqType = (String) map.get("requirement-type");
            long reqVal = ((Number) map.get("requirement-value")).longValue();
            BountyPlayer stats = playerDataCache.getPlayer(player.getUniqueId());
            if (stats == null) continue;

            boolean met = false;
            switch (reqType.toUpperCase()) {
                case "TOTAL_KILLS":
                    met = stats.getTotalKills() >= reqVal;
                    break;
                case "KILLS":
                    met = stats.getTotalKills() >= reqVal;
                    break;
                case "COINS":
                    met = stats.getCoins() >= reqVal;
                    break;
                case "STREAK":
                    met = stats.getHighestStreak() >= reqVal;
                    break;
                case "KILL_RANK_1":
                    // If they have claimed or logged it
                    try {
                        met = plugin.getDataStore().hasRewardClaimed(player.getUniqueId(), (String) map.get("id")).get();
                    } catch (Exception ignored) {}
                    break;
                case "HOLD_RANK":
                    Integer r = rankManager.getRank(player.getUniqueId());
                    met = r != null && r == reqVal;
                    break;
                default:
                    break;
            }
            if (met) count++;
        }
        return count;
    }
}
