package com.polarsmp.bounty;

import com.polarsmp.PolarSMP;
import com.polarsmp.core.ConfigManager;
import com.polarsmp.core.DataStore;
import com.polarsmp.core.PlayerDataCache;
import com.polarsmp.integrations.VaultHook;
import com.polarsmp.rank.RankManager;
import com.polarsmp.util.FormatUtil;
import com.polarsmp.util.MessageUtil;
import com.polarsmp.util.SoundUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Manages all coin, bounty, and kill reward logic for PolarSMP.
 *
 * <p>Handles passive bounty accrual, kill rewards, streak bonuses,
 * Vault economy mirroring, and server-wide bounty broadcasts.</p>
 *
 * @author ANSH9BOSS
 * @version 1.0.0
 */
public final class BountyManager {

    private final PolarSMP plugin;
    private final ConfigManager configManager;
    private final DataStore dataStore;
    private final PlayerDataCache playerDataCache;
    private final RankManager rankManager;
    private final VaultHook vaultHook;

    private org.bukkit.scheduler.BukkitTask passiveBountyTask;

    /**
     * Constructs a new BountyManager.
     *
     * @param plugin          the PolarSMP plugin instance
     * @param configManager   the config manager
     * @param dataStore       the data store for persistence
     * @param playerDataCache the in-memory player cache
     * @param rankManager     the rank manager
     * @param vaultHook       the optional Vault integration hook
     */
    public BountyManager(final PolarSMP plugin, final ConfigManager configManager,
                         final DataStore dataStore, final PlayerDataCache playerDataCache,
                         final RankManager rankManager, final VaultHook vaultHook) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.dataStore = dataStore;
        this.playerDataCache = playerDataCache;
        this.rankManager = rankManager;
        this.vaultHook = vaultHook;
    }

    // ─── Kill reward processing ───────────────────────────────────

    /**
     * Awards base kill coins and rank kill bonus to the killer.
     * Called synchronously on the main thread during kill processing.
     *
     * @param killer      the killing player
     * @param victim      the killed player
     * @param victimRank  the victim's rank number (null if unranked)
     */
    public void processKillRewards(final Player killer, final Player victim, final Integer victimRank) {
        BountyPlayer killerData = playerDataCache.getPlayer(killer.getUniqueId());
        if (killerData == null) return;

        var bountyConf = configManager.getBountyConfig();
        long baseCoins = bountyConf.getLong("base-kill-coins", 10);

        // Award base kill coins
        addCoins(killer, killerData, baseCoins);
        MessageUtil.sendActionBar(killer, MiniMessage.miniMessage().deserialize(
                configManager.getMessage("bounty.coins-received")
                        .replace("<amount>", FormatUtil.formatNumber(baseCoins))));
        SoundUtil.playSound(killer, configManager.getSound("coins-received"));

        // Award ranked kill bonus
        if (victimRank != null) {
            String bonusKey = "ranked-kill-bonus.rank-" + victimRank + "-bonus";
            long bonus = bountyConf.getLong(bonusKey, 0);
            if (bonus > 0) {
                addCoins(killer, killerData, bonus);
                MessageUtil.sendActionBar(killer, MiniMessage.miniMessage().deserialize(
                        configManager.getMessage("bounty.ranked-kill-bonus")
                                .replace("<amount>", FormatUtil.formatNumber(bonus))));
            }
        }

        playerDataCache.markDirty(killer.getUniqueId());
    }

    /**
     * Transfers the victim's bounty to the killer.
     *
     * @param killer     the killing player
     * @param victim     the killed player
     */
    public void processBountyClaim(final Player killer, final Player victim) {
        BountyPlayer killerData = playerDataCache.getPlayer(killer.getUniqueId());
        BountyPlayer victimData = playerDataCache.getPlayer(victim.getUniqueId());
        if (killerData == null || victimData == null) return;

        long victimBounty = victimData.getBounty();
        if (victimBounty <= 0) return;

        // Transfer bounty to killer as coins
        addCoins(killer, killerData, victimBounty);
        victimData.setBounty(0);
        playerDataCache.markDirty(victim.getUniqueId());
        playerDataCache.markDirty(killer.getUniqueId());

        // Notify killer
        String msg = configManager.getMessage("bounty.claimed-self")
                .replace("<amount>", FormatUtil.formatNumber(victimBounty))
                .replace("<victim>", victim.getName());
        MessageUtil.sendActionBar(killer, MiniMessage.miniMessage().deserialize(msg));
        SoundUtil.playSound(killer, configManager.getSound("bounty-claimed"));

        // Notify victim
        String victimMsg = configManager.getMessage("bounty.stolen-from")
                .replace("<amount>", FormatUtil.formatNumber(victimBounty))
                .replace("<killer>", killer.getName());
        MessageUtil.sendActionBar(victim, MiniMessage.miniMessage().deserialize(victimMsg));

        // Server-wide broadcast if above threshold
        long threshold = configManager.getMainConfig().getLong("bounty.broadcast-threshold", 500);
        if (victimBounty >= threshold) {
            String broadcast = configManager.getMessage("bounty.claimed-broadcast")
                    .replace("<killer>", killer.getName())
                    .replace("<victim>", victim.getName())
                    .replace("<amount>", FormatUtil.formatNumber(victimBounty));
            MessageUtil.broadcastMessage(MiniMessage.miniMessage().deserialize(broadcast));
        }
    }

    /**
     * Adds coins to a player's balance and optionally mirrors to Vault.
     *
     * @param player      the player to reward
     * @param playerData  the player's data model
     * @param amount      the amount to add
     */
    public void addCoins(final Player player, final BountyPlayer playerData, final long amount) {
        playerData.addCoins(amount);
        vaultHook.deposit(player, amount);
        playerDataCache.markDirty(player.getUniqueId());
    }

    /**
     * Adds bounty to a player on every kill.
     *
     * @param player     the killing player
     */
    public void addKillBounty(final Player player) {
        BountyPlayer data = playerDataCache.getPlayer(player.getUniqueId());
        if (data == null) return;
        long bountyGain = configManager.getBountyConfig().getLong("bounty-gain-per-kill", 5);
        data.addBounty(bountyGain);
        playerDataCache.markDirty(player.getUniqueId());
    }

    /**
     * Adds streak milestone bounty to a player.
     *
     * @param player the player who hit a milestone
     */
    public void addStreakMilestoneBounty(final Player player) {
        BountyPlayer data = playerDataCache.getPlayer(player.getUniqueId());
        if (data == null) return;
        long bountyGain = configManager.getBountyConfig().getLong("bounty-gain-per-streak-milestone", 20);
        data.addBounty(bountyGain);
        playerDataCache.markDirty(player.getUniqueId());
    }

    // ─── Admin operations ─────────────────────────────────────────

    /**
     * Gives coins to a player (admin command).
     *
     * @param target the target player
     * @param amount the amount to give
     */
    public void adminGiveCoins(final Player target, final long amount) {
        BountyPlayer data = playerDataCache.getPlayer(target.getUniqueId());
        if (data == null) return;
        addCoins(target, data, amount);
        dataStore.savePlayer(data);
    }

    /**
     * Takes coins from a player, floored at zero (admin command).
     *
     * @param target the target player
     * @param amount the amount to remove
     */
    public void adminTakeCoins(final Player target, final long amount) {
        BountyPlayer data = playerDataCache.getPlayer(target.getUniqueId());
        if (data == null) return;
        data.removeCoins(amount);
        vaultHook.withdraw(target, Math.min(amount, data.getCoins() + amount));
        playerDataCache.markDirty(target.getUniqueId());
        dataStore.savePlayer(data);
    }

    /**
     * Sets a player's bounty directly (admin command).
     *
     * @param target the target player
     * @param amount the bounty amount to set
     */
    public void adminSetBounty(final Player target, final long amount) {
        BountyPlayer data = playerDataCache.getPlayer(target.getUniqueId());
        if (data == null) return;
        data.setBounty(amount);
        playerDataCache.markDirty(target.getUniqueId());
        dataStore.savePlayer(data);
    }

    /**
     * Resets all stats for a player to zero (admin command).
     *
     * @param target the target player
     */
    public void adminResetStats(final Player target) {
        BountyPlayer data = playerDataCache.getPlayer(target.getUniqueId());
        if (data == null) return;
        data.setCoins(0);
        data.setBounty(0);
        data.setKillStreak(0);
        data.setHighestStreak(0);
        data.setTotalKills(0);
        data.setTotalDeaths(0);
        playerDataCache.markDirty(target.getUniqueId());
        dataStore.savePlayer(data);
    }

    // ─── Passive bounty task ──────────────────────────────────────

    /**
     * Starts the repeating async task that passively accrues bounty for ranked players
     * every 60 seconds (1 game minute) and runs glowing outline threshold checks.
     */
    public void startPassiveBountyTask() {
        passiveBountyTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                BountyPlayer data = playerDataCache.getPlayer(player.getUniqueId());
                if (data == null) continue;

                // 1. Passive gain if ranked
                Integer rank = rankManager.getRank(player.getUniqueId());
                if (rank != null) {
                    String key = "bounty-passive-gain.rank-" + rank + "-per-minute";
                    long gain = configManager.getBountyConfig().getLong(key, 0);
                    if (gain > 0) {
                        data.addBounty(gain);
                        playerDataCache.markDirty(player.getUniqueId());
                    }
                }

                // 2. Glow threshold check (sync back to main thread)
                Bukkit.getScheduler().runTask(plugin, () -> {
                    long threshold = configManager.getMainConfig().getLong("bounty.glowing-threshold", 5000);
                    player.setGlowing(data.getBounty() >= threshold);
                });
            }
        }, 1200L, 1200L); // 1200 ticks = 60 seconds
    }

    /**
     * Places a bounty contract on a target player.
     *
     * @param sender the player placing the contract
     * @param target the target player receiving the bounty
     * @param amount the coin contract amount
     * @return true if successfully placed, false if insufficient coins
     */
    public boolean placeContract(final Player sender, final Player target, final long amount) {
        BountyPlayer senderData = playerDataCache.getPlayer(sender.getUniqueId());
        BountyPlayer targetData = playerDataCache.getPlayer(target.getUniqueId());
        if (senderData == null || targetData == null) return false;

        long taxPct = configManager.getMainConfig().getLong("bounty.contract-tax-percentage", 5);
        long tax = (long) Math.ceil(amount * (taxPct / 100.0));
        long totalCost = amount + tax;

        if (senderData.getCoins() < totalCost) return false;

        // Deduct from sender
        adminTakeCoins(sender, totalCost);

        // Add bounty to target
        targetData.addBounty(amount);
        playerDataCache.markDirty(target.getUniqueId());

        // Save target state
        dataStore.savePlayer(targetData);

        // Notify sender
        String placedMsg = configManager.getMessage("bounty.contract-placed")
                .replace("<amount>", FormatUtil.formatNumber(amount))
                .replace("<target>", target.getName())
                .replace("<tax>", FormatUtil.formatNumber(tax));
        sender.sendMessage(PolarSMP.miniMessage().deserialize(placedMsg));
        SoundUtil.playSound(sender, configManager.getSound("coins-received"));

        // Global broadcast
        String broadcastMsg = configManager.getMessage("bounty.contract-broadcast")
                .replace("<player>", sender.getName())
                .replace("<amount>", FormatUtil.formatNumber(amount))
                .replace("<target>", target.getName());
        MessageUtil.broadcastMessage(PolarSMP.miniMessage().deserialize(broadcastMsg));

        // Update target glowing status on main thread
        long threshold = configManager.getMainConfig().getLong("bounty.glowing-threshold", 5000);
        target.setGlowing(targetData.getBounty() >= threshold);

        return true;
    }

    /**
     * Returns the streak milestone coin bonus for the given streak count.
     *
     * @param streak the current streak value
     * @return the bonus coins for that milestone, or 0 if none
     */
    public long getMilestoneBonus(final int streak) {
        ConfigurationSection milestones = configManager.getBountyConfig()
                .getConfigurationSection("streak-milestones");
        if (milestones == null) return 0;
        return milestones.getLong(String.valueOf(streak), 0);
    }

    /**
     * Checks if a streak value is a configured milestone.
     *
     * @param streak the streak to check
     * @return true if the streak is a milestone
     */
    public boolean isMilestone(final int streak) {
        return getMilestoneBonus(streak) > 0;
    }
}
