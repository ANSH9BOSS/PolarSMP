package com.polarsmp.listeners;

import com.polarsmp.PolarSMP;
import com.polarsmp.bounty.BountyManager;
import com.polarsmp.bounty.BountyPlayer;
import com.polarsmp.bounty.StreakManager;
import com.polarsmp.core.ConfigManager;
import com.polarsmp.core.DataStore;
import com.polarsmp.core.PlayerDataCache;
import com.polarsmp.rank.RankManager;
import com.polarsmp.rank.RankPerk;
import com.polarsmp.util.AntiFarmTracker;
import com.polarsmp.util.CombatLogTracker;
import com.polarsmp.util.AnimationUtil;
import com.polarsmp.util.MessageUtil;
import com.polarsmp.util.SoundUtil;
import com.polarsmp.util.FormatUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.UUID;

/**
 * Handles PvP damage tracking, kill detection, rank swap, bounty claiming,
 * and stat updates.
 *
 * @author ANSH9BOSS
 * @version 1.0.0
 */
public final class PvPListener implements Listener {

    private final PolarSMP plugin;
    private final ConfigManager configManager;
    private final RankManager rankManager;
    private final BountyManager bountyManager;
    private final StreakManager streakManager;
    private final AntiFarmTracker antiFarmTracker;
    private final CombatLogTracker combatLogTracker;
    private final PlayerDataCache playerDataCache;
    private final DataStore dataStore;

    /**
     * Constructs a new PvPListener.
     */
    public PvPListener(final PolarSMP plugin, final ConfigManager configManager,
                       final RankManager rankManager, final BountyManager bountyManager,
                       final StreakManager streakManager, final AntiFarmTracker antiFarmTracker,
                       final CombatLogTracker combatLogTracker, final PlayerDataCache playerDataCache,
                       final DataStore dataStore) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.rankManager = rankManager;
        this.bountyManager = bountyManager;
        this.streakManager = streakManager;
        this.antiFarmTracker = antiFarmTracker;
        this.combatLogTracker = combatLogTracker;
        this.playerDataCache = playerDataCache;
        this.dataStore = dataStore;
    }

    /**
     * Tags players in combat when they damage each other.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageByEntity(final EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        Player attacker = null;
        if (event.getDamager() instanceof Player p) {
            attacker = p;
        } else if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            attacker = p;
        }

        if (attacker == null || attacker.equals(victim)) return;

        // Check world allowed
        WorldListener worldFilter = plugin.getWorldListener();
        if (worldFilter != null && !worldFilter.isWorldAllowed(victim.getWorld())) return;

        combatLogTracker.tag(victim.getUniqueId(), attacker.getUniqueId());
        combatLogTracker.tag(attacker.getUniqueId(), victim.getUniqueId());
    }

    /**
     * Handles player deaths to process kills, streaks, ranks, bounties, and rewards.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(final PlayerDeathEvent event) {
        Player victim = event.getPlayer();
        Player killer = victim.getKiller();

        // 1. Killer must be a player
        if (killer == null || killer.equals(victim)) return;

        // 2. Must be a PvP death cause
        var damageEvent = victim.getLastDamageCause();
        if (!(damageEvent instanceof EntityDamageByEntityEvent)) return;

        // 3. Check world allowed
        WorldListener worldFilter = plugin.getWorldListener();
        if (worldFilter != null && !worldFilter.isWorldAllowed(victim.getWorld())) return;

        UUID killerUuid = killer.getUniqueId();
        UUID victimUuid = victim.getUniqueId();

        // 4. Check anti-farm async
        antiFarmTracker.isOnCooldown(killerUuid, victimUuid).thenAccept(onCooldown -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (onCooldown) {
                    long cooldownLeft = antiFarmTracker.getCooldownSeconds();
                    String msg = configManager.getMessage("anti-farm.detected")
                            .replace("<seconds>", String.valueOf(cooldownLeft))
                            .replace("<victim>", victim.getName());
                    killer.sendMessage(PolarSMP.miniMessage().deserialize(msg));
                    return;
                }

                // Log the kill to database for anti-farm tracking
                dataStore.logKill(killerUuid, victimUuid, System.currentTimeMillis());

                // Remove combat tags
                combatLogTracker.untag(victimUuid);

                // Fetch stats from cache
                BountyPlayer killerData = playerDataCache.getPlayer(killerUuid);
                BountyPlayer victimData = playerDataCache.getPlayer(victimUuid);

                if (killerData == null || victimData == null) return;

                // Process coins and streak rewards
                Integer victimRank = rankManager.getRank(victimUuid);
                bountyManager.processKillRewards(killer, victim, victimRank);
                bountyManager.addKillBounty(killer);

                // Update killer stats
                killerData.incrementKills();
                killerData.incrementStreak();
                streakManager.processStreakKill(killer, killerData);

                // Process bounty claim
                bountyManager.processBountyClaim(killer, victim);

                // Update victim stats
                victimData.resetStreak();
                victimData.incrementDeaths();

                streakManager.updateBossBar(victim, victimData);

                // Apply rank swap logic if season is active
                if (rankManager.isSeasonActive()) {
                    var result = rankManager.processKill(killer, victim);
                    if (result.transferred()) {
                        handleRankTransferBroadcasts(killer, victim, result);
                    }
                }

                // Mark cache dirty
                playerDataCache.markDirty(killerUuid);
                playerDataCache.markDirty(victimUuid);

                // Async database save
                dataStore.savePlayer(killerData);
                dataStore.savePlayer(victimData);

                // Update sidebar scoreboards
                plugin.getScoreboardManager().updateScoreboard(killer);
                plugin.getScoreboardManager().updateScoreboard(victim);
            });
        }).exceptionally(ex -> {
            plugin.getLogger().severe("Failed to process PvP kill due to database error: " + ex.getMessage());
            return null;
        });
    }

    /** Helper to play animations and send messages upon rank transfer. */
    private void handleRankTransferBroadcasts(final Player killer, final Player victim,
                                              final RankManager.RankTransferResult result) {
        int rankNum = result.rankTransferred();

        // ── killer gained rank ────────────────────────────────────
        SoundUtil.playSound(killer, configManager.getSound("rank-gained"));
        AnimationUtil.spawnTotemRing(killer, plugin);

        String titleMM = configManager.getMessagesConfig().getString("gui.rank-leaderboard-title", "👑")
                .replace("👑", "") + " NEW RANK ACHIEVED";
        if (rankNum == 1) titleMM = "👑 RANK #1 ACHIEVED 👑";

        String titleText = "<gradient:#FFD700:#FF8C00><bold>" + titleMM + "</bold></gradient>";
        String subtitleText = "<yellow>You are now Rank #" + rankNum + "</yellow>";
        MessageUtil.sendTitle(killer,
                PolarSMP.miniMessage().deserialize(titleText),
                PolarSMP.miniMessage().deserialize(subtitleText),
                10, 60, 20);

        String actionbarGained = configManager.getMessage("rank.gained")
                .replaceAll("<newline>", "")
                .replaceAll("━+", "");
        MessageUtil.sendActionBar(killer, PolarSMP.miniMessage().deserialize(
                "<gold>👑 New Rank: Rank #" + rankNum + "</gold>"));

        String gainedMsg = configManager.getMessage("rank.gained")
                .replace("<victim>", victim.getName())
                .replace("{rank}", String.valueOf(rankNum));
        killer.sendMessage(PolarSMP.miniMessage().deserialize(gainedMsg));

        // ── victim lost rank ──────────────────────────────────────
        SoundUtil.playSound(victim, configManager.getSound("rank-lost"));
        String lostTitle = "<gradient:#FF4444:#CC0000><bold>⚔ RANK STOLEN ⚔</bold></gradient>";
        String lostSubtitle = "<gray>" + killer.getName() + " took your rank</gray>";
        MessageUtil.sendTitle(victim,
                PolarSMP.miniMessage().deserialize(lostTitle),
                PolarSMP.miniMessage().deserialize(lostSubtitle),
                10, 60, 20);

        MessageUtil.sendActionBar(victim, PolarSMP.miniMessage().deserialize(
                "<red>⚔ Your Rank was stolen by " + killer.getName() + "</red>"));

        String lostMsg = configManager.getMessage("rank.lost")
                .replace("<killer>", killer.getName())
                .replace("{rank}", String.valueOf(rankNum));
        victim.sendMessage(PolarSMP.miniMessage().deserialize(lostMsg));

        // ── Server-wide broadcast ─────────────────────────────────
        String broadcastMsg = configManager.getMessage("rank.transfer-broadcast")
                .replace("<killer>", killer.getName())
                .replace("<victim>", victim.getName())
                .replace("{rank}", String.valueOf(rankNum));
        MessageUtil.broadcastMessage(PolarSMP.miniMessage().deserialize(broadcastMsg));
    }
}
