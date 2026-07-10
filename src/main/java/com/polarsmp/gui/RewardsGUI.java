package com.polarsmp.gui;

import com.polarsmp.PolarSMP;
import com.polarsmp.bounty.BountyPlayer;
import com.polarsmp.core.ConfigManager;
import com.polarsmp.core.DataStore;
import com.polarsmp.core.PlayerDataCache;
import com.polarsmp.util.FormatUtil;
import com.polarsmp.util.ItemBuilder;
import com.polarsmp.util.SoundUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Rewards milestone GUI for tracking and claiming achievements.
 * Theme: Dark Green and Gold.
 *
 * @author ANSH9BOSS
 * @version 1.0.0
 */
public final class RewardsGUI {

    private final PolarSMP plugin;
    private final ConfigManager configManager;
    private final PlayerDataCache playerDataCache;
    private final DataStore dataStore;

    /**
     * Constructs a new RewardsGUI.
     */
    public RewardsGUI(final PolarSMP plugin, final ConfigManager configManager,
                      final PlayerDataCache playerDataCache, final DataStore dataStore) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.playerDataCache = playerDataCache;
        this.dataStore = dataStore;
    }

    /**
     * Builds and opens the Rewards GUI.
     *
     * @param player the player opening the menu
     */
    public void open(final Player player) {
        String titleMM = configManager.getMessagesConfig().getString("gui.rewards-title", "<dark_green><bold>🏆 Reward Milestones 🏆</bold></dark_green>");
        Inventory inv = GuiBuilder.createInventory(PolarSMP.miniMessage().deserialize(titleMM), 54);

        // Border: Green stained glass
        ItemStack border = GuiBuilder.createBorderItem(Material.GREEN_STAINED_GLASS_PANE, Component.empty());
        GuiBuilder.fillBorder(inv, border);

        BountyPlayer p = playerDataCache.getPlayer(player.getUniqueId());
        if (p == null) return;

        List<Map<?, ?>> rewardsConfig = configManager.getRewardsConfig().getMapList("rewards");
        int index = 0;
        int[] slots = {
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43
        };

        for (Map<?, ?> rewardMap : rewardsConfig) {
            if (index >= slots.length) break;

            String id = (String) rewardMap.get("id");
            String nameStr = (String) rewardMap.get("display-name");
            String matStr = (String) rewardMap.get("material");
            List<String> loreStr = (List<String>) rewardMap.get("lore");
            String reqType = (String) rewardMap.get("requirement-type");
            long reqVal = ((Number) rewardMap.get("requirement-value")).longValue();

            Material mat = Material.matchMaterial(matStr);
            if (mat == null) mat = Material.PAPER;

            final Material itemMat = mat;
            final int slotIndex = slots[index];

            // Check claim status and requirements
            dataStore.hasRewardClaimed(player.getUniqueId(), id).thenAccept(claimed -> {
                long currentVal = getPlayerStat(p, reqType, id);
                boolean met = currentVal >= reqVal;

                // Sync back to main thread to edit Bukkit inventory safely
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                    ItemStack displayItem;
                    if (claimed) {
                        // Claimed: Grey Dye icon
                        displayItem = new ItemBuilder(Material.GRAY_DYE)
                                .name("<gray>" + PolarSMP.miniMessage().stripTags(nameStr) + "</gray>")
                                .loreFromMiniMessage(List.of(
                                        "<gray>✔ CLAIMED</gray>"
                                ))
                                .build();
                    } else if (met) {
                        // Completed but Unclaimed: normal material glowing
                        List<String> completedLore = new ArrayList<>(loreStr);
                        completedLore.add("");
                        completedLore.add(configManager.getMessagesConfig().getString("gui.reward-unclaimed", "<green><bold>✔ CLICK TO CLAIM</bold></green>"));

                        displayItem = new ItemBuilder(itemMat)
                                .name(nameStr)
                                .loreFromMiniMessage(completedLore)
                                .glowing(true)
                                .build();
                    } else {
                        // Uncompleted: Grey name, progress bar
                        List<String> incompleteLore = new ArrayList<>(loreStr);
                        incompleteLore.add("");
                        String bar = FormatUtil.generateProgressBar(currentVal, reqVal, 10, '=', '-', "<green>", "<gray>");
                        incompleteLore.add("<gray>Progress: " + currentVal + " / " + reqVal + "</gray>");
                        incompleteLore.add("<gray>[" + bar + "] " + FormatUtil.formatPercent(currentVal, reqVal) + "</gray>");

                        displayItem = new ItemBuilder(itemMat)
                                .name("<gray>" + PolarSMP.miniMessage().stripTags(nameStr) + "</gray>")
                                .loreFromMiniMessage(incompleteLore)
                                .build();
                    }

                    inv.setItem(slotIndex, displayItem);
                });
            });

            index++;
        }

        // Close Button (Slot 49)
        inv.setItem(49, GuiBuilder.createCloseButton());

        player.openInventory(inv);
    }

    /**
     * Handles slot clicks for claiming reward payouts.
     */
    public void handleClick(final Player player, final int slot) {
        int[] slots = {
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43
        };

        int rewardIndex = -1;
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == slot) {
                rewardIndex = i;
                break;
            }
        }

        if (rewardIndex == -1) return;

        List<Map<?, ?>> rewardsConfig = configManager.getRewardsConfig().getMapList("rewards");
        if (rewardIndex >= rewardsConfig.size()) return;

        Map<?, ?> rewardMap = rewardsConfig.get(rewardIndex);
        String id = (String) rewardMap.get("id");
        String reqType = (String) rewardMap.get("requirement-type");
        long reqVal = ((Number) rewardMap.get("requirement-value")).longValue();
        String rewardType = (String) rewardMap.get("reward-type");
        Object rewardAmtObj = rewardMap.get("reward-amount");
        long rewardAmount = rewardAmtObj instanceof Number ? ((Number) rewardAmtObj).longValue() : 0L;
        String rewardMat = (String) rewardMap.get("reward-material");
        String rewardCmd = (String) rewardMap.get("reward-command");

        BountyPlayer p = playerDataCache.getPlayer(player.getUniqueId());
        if (p == null) return;

        // Verify requirements again
        dataStore.hasRewardClaimed(player.getUniqueId(), id).thenAccept(claimed -> {
            if (claimed) return;

            long currentVal = getPlayerStat(p, reqType, id);
            if (currentVal < reqVal) {
                SoundUtil.playSound(player, configManager.getSound("gui-deny"));
                return;
            }

            // Perform reward payout on main thread
            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                // Save claim
                dataStore.saveRewardClaimed(player.getUniqueId(), id);

                // Process rewards
                switch (rewardType.toUpperCase()) {
                    case "COINS":
                        plugin.getBountyManager().addCoins(player, p, rewardAmount);
                        player.sendMessage(PolarSMP.miniMessage().deserialize("<green>✔ Claimed " + rewardAmount + " coins!</green>"));
                        break;
                    case "ITEM":
                        if (rewardMat != null) {
                            Material mat = Material.matchMaterial(rewardMat);
                            if (mat != null) {
                                ItemStack item = new ItemStack(mat, (int) rewardAmount);
                                var rem = player.getInventory().addItem(item);
                                for (ItemStack left : rem.values()) {
                                    player.getWorld().dropItemNaturally(player.getLocation(), left);
                                }
                                player.sendMessage(PolarSMP.miniMessage().deserialize("<green>✔ Claimed reward items!</green>"));
                            }
                        }
                        break;
                    case "COMMAND":
                        if (rewardCmd != null) {
                            String cmd = rewardCmd.replace("%player%", player.getName());
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                            player.sendMessage(PolarSMP.miniMessage().deserialize("<green>✔ Claimed command reward!</green>"));
                        }
                        break;
                    default:
                        break;
                }

                SoundUtil.playSound(player, configManager.getSound("reward-claim"));

                // Refresh GUI
                open(player);
            });
        });
    }

    private long getPlayerStat(final BountyPlayer stats, final String reqType, final String rewardId) {
        switch (reqType.toUpperCase()) {
            case "TOTAL_KILLS":
                return stats.getTotalKills();
            case "KILLS":
                return stats.getTotalKills();
            case "COINS":
                return stats.getCoins();
            case "STREAK":
                return stats.getHighestStreak();
            case "KILL_RANK_1":
                // Checked via database log usually, return 1 if database has it claimed
                try {
                    return dataStore.hasRewardClaimed(stats.getUuid(), rewardId).get() ? 1 : 0;
                } catch (Exception e) {
                    return 0;
                }
            case "HOLD_RANK":
                Integer r = plugin.getRankManager().getRank(stats.getUuid());
                return (r != null && r == 1) ? 1 : 0;
            default:
                return 0;
        }
    }
}
