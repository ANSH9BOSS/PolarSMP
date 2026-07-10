package com.polarsmp.listeners;

import com.polarsmp.PolarSMP;
import com.polarsmp.bounty.BountyPlayer;
import com.polarsmp.core.ConfigManager;
import com.polarsmp.core.DataStore;
import com.polarsmp.core.PlayerDataCache;
import com.polarsmp.rank.RankManager;
import com.polarsmp.scoreboard.ScoreboardManager;
import com.polarsmp.util.CombatLogTracker;
import com.polarsmp.integrations.VaultHook;
import com.polarsmp.util.MessageUtil;
import com.polarsmp.util.SoundUtil;
import com.polarsmp.util.FormatUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

/**
 * Handles player join caching, resource pack delivery, and combat log checkout.
 *
 * @author ANSH9BOSS
 * @version 1.0.0
 */
public final class PlayerJoinQuitListener implements Listener {

    private final PolarSMP plugin;
    private final ConfigManager configManager;
    private final DataStore dataStore;
    private final PlayerDataCache playerDataCache;
    private final RankManager rankManager;
    private final ScoreboardManager scoreboardManager;
    private final CombatLogTracker combatLogTracker;
    private final VaultHook vaultHook;

    /**
     * Constructs a new PlayerJoinQuitListener.
     */
    public PlayerJoinQuitListener(final PolarSMP plugin, final ConfigManager configManager,
                                 final DataStore dataStore, final PlayerDataCache playerDataCache,
                                 final RankManager rankManager, final ScoreboardManager scoreboardManager,
                                 final CombatLogTracker combatLogTracker, final VaultHook vaultHook) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.dataStore = dataStore;
        this.playerDataCache = playerDataCache;
        this.rankManager = rankManager;
        this.scoreboardManager = scoreboardManager;
        this.combatLogTracker = combatLogTracker;
        this.vaultHook = vaultHook;
    }

    /**
     * Loads player data on join and initializes ranks, permissions, and scoreboard.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(final PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // 1. Load data async to cache
        playerDataCache.loadPlayer(uuid, player.getName(), dataStore);

        // 2. Perform updates on next tick when player is fully online
        Bukkit.getScheduler().runTask(plugin, () -> {
            rankManager.onPlayerJoin(player);
            scoreboardManager.createScoreboard(player);

            // Check if player previously combat logged
            if (combatLogTracker.shouldNotify(uuid)) {
                String returnMsg = configManager.getMessage("combat-log.return-message");
                player.sendMessage(PolarSMP.miniMessage().deserialize(returnMsg));
            }

            // Resource pack delivery
            var rpack = configManager.getMainConfig();
            if (rpack.getBoolean("resource-pack.enabled", false)) {
                String url = rpack.getString("resource-pack.url", "");
                String hash = rpack.getString("resource-pack.hash", "");
                if (!url.isEmpty()) {
                    String loadingMsg = rpack.getString("resource-pack.loading-message", "");
                    if (!loadingMsg.isEmpty()) {
                        MessageUtil.sendActionBar(player, PolarSMP.miniMessage().deserialize(loadingMsg));
                    }
                    if (!hash.isEmpty()) {
                        byte[] hashBytes = hexStringToByteArray(hash);
                        player.setResourcePack(url, hashBytes);
                    } else {
                        player.setResourcePack(url);
                    }
                }
            }
        });
    }

    /**
     * Unloads player data on quit and handles combat logging death processing.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(final PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Check if player combat logged
        if (combatLogTracker.isTagged(uuid)) {
            processCombatLogDeath(player);
        }

        // Cleanup
        scoreboardManager.removeScoreboard(player);
        rankManager.onPlayerQuit(player);
        plugin.getStreakManager().removeBossBar(player);

        // Save and unload from cache
        playerDataCache.unloadPlayer(uuid, dataStore);
        combatLogTracker.untag(uuid);
    }

    /**
     * Penalizes player for combat logging by transferring rank, bounty, and stats.
     */
    private void processCombatLogDeath(final Player victim) {
        UUID victimUuid = victim.getUniqueId();
        UUID attackerUuid = combatLogTracker.getLastAttacker(victimUuid);
        Player attacker = attackerUuid != null ? Bukkit.getPlayer(attackerUuid) : null;

        BountyPlayer victimData = playerDataCache.getPlayer(victimUuid);
        if (victimData == null) return;

        // Log sound
        SoundUtil.playSound(victim, configManager.getSound("combat-log"));

        // Process stats loss
        long victimBounty = victimData.getBounty();
        victimData.setBounty(0);
        victimData.resetStreak();
        victimData.incrementDeaths();
        playerDataCache.markDirty(victimUuid);

        // Transfer resources to last attacker if online
        if (attacker != null && attacker.isOnline()) {
            BountyPlayer attackerData = playerDataCache.getPlayer(attackerUuid);
            if (attackerData != null) {
                // Award bounty as coins
                if (victimBounty > 0) {
                    plugin.getBountyManager().addCoins(attacker, attackerData, victimBounty);
                    String msg = configManager.getMessage("bounty.claimed-self")
                            .replace("<amount>", FormatUtil.formatNumber(victimBounty))
                            .replace("<victim>", victim.getName());
                    MessageUtil.sendActionBar(attacker, PolarSMP.miniMessage().deserialize(msg));
                }

                // Record the death on database
                dataStore.logKill(attackerUuid, victimUuid, System.currentTimeMillis());
            }
        }

        // Rank displacement
        if (rankManager.isSeasonActive()) {
            Integer victimRank = rankManager.getRank(victimUuid);
            if (victimRank != null) {
                if (attacker != null && attacker.isOnline()) {
                    // Transfer victim rank to online attacker
                    rankManager.adminSetRank(attacker, victimRank);
                    rankManager.adminClearRank(victim);

                    String gainedMsg = configManager.getMessage("rank.gained")
                            .replace("<victim>", victim.getName())
                            .replace("{rank}", String.valueOf(victimRank));
                    attacker.sendMessage(PolarSMP.miniMessage().deserialize(gainedMsg));
                } else {
                    // Strip rank to vacant if attacker offline
                    rankManager.adminClearRank(victim);
                }
            }
        }

        // Mark for notice on rejoin
        combatLogTracker.markForNotification(victimUuid);

        // Broadcast combat log death
        String broadcast = configManager.getMessage("combat-log.death-broadcast")
                .replace("<player>", victim.getName());
        MessageUtil.broadcastMessage(PolarSMP.miniMessage().deserialize(broadcast));
    }

    /**
     * Parses a hex string into a byte array.
     */
    private byte[] hexStringToByteArray(final String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }
}
