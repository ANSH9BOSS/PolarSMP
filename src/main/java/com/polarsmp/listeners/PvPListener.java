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
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

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

        // Suppress default death message immediately if custom pvp messages enabled
        if (configManager.getMainConfig().getBoolean("cosmetics.custom-kill-messages", true)) {
            event.deathMessage(net.kyori.adventure.text.Component.empty());
        }

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

                // Trigger cosmetics and death effects (#5)
                handleDeathCosmetics(victim, killer);

                // Trigger custom kill message (#5)
                handleCustomKillMessage(victim, killer);

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

    private void handleDeathCosmetics(final Player victim, final Player killer) {
        if (configManager.getMainConfig().getBoolean("cosmetics.death-effects", true)) {
            org.bukkit.Location loc = victim.getLocation();
            org.bukkit.World world = victim.getWorld();

            // Spawn smoke and explosion effects
            world.spawnParticle(Particle.LARGE_SMOKE, loc.add(0, 1, 0), 20, 0.4, 0.4, 0.4, 0.05);
            world.spawnParticle(Particle.FLAME, loc, 30, 0.5, 0.5, 0.5, 0.1);
            world.spawnParticle(Particle.DUST, loc, 50, 0.6, 0.6, 0.6, 0.05, new Particle.DustOptions(Color.RED, 1.2f));

            // Custom lightning/thunder strike sound (silent strike, only sound to avoid real damage/fire)
            world.playSound(loc, org.bukkit.Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.6f, 1.2f);
        }
    }

    private void handleCustomKillMessage(final Player victim, final Player killer) {
        if (configManager.getMainConfig().getBoolean("cosmetics.custom-kill-messages", true)) {
            java.util.List<String> messages = configManager.getMessagesConfig().getStringList("kill-messages");
            if (messages.isEmpty()) return;

            // Pick random template
            String template = messages.get(new java.util.Random().nextInt(messages.size()));

            // Get killer K/D
            BountyPlayer killerData = playerDataCache.getPlayer(killer.getUniqueId());
            String kd = killerData != null ? FormatUtil.formatKD(killerData.getTotalKills(), killerData.getTotalDeaths()) : "0.00";

            // Format names with rank prefix
            String killerNameFormatted = getFormattedPlayerName(killer);
            String victimNameFormatted = getFormattedPlayerName(victim);

            String formattedMessage = template
                    .replace("<killer>", killerNameFormatted)
                    .replace("<victim>", victimNameFormatted)
                    .replace("<kd>", kd);

            MessageUtil.broadcastMessage(PolarSMP.miniMessage().deserialize(formattedMessage));
        }
    }

    private String getFormattedPlayerName(final Player player) {
        Integer rank = rankManager.getRank(player.getUniqueId());
        String prefix = "";
        if (rank != null) {
            prefix = configManager.getRanksConfig().getString("ranks.rank-" + rank + ".prefix", "");
        } else {
            prefix = configManager.getRanksConfig().getString("unranked-prefix", "<gray>[Unranked]</gray> ");
        }
        return prefix + player.getName();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(final EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        Player killer = null;
        if (event.getDamager() instanceof Player p) {
            killer = p;
        } else if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            killer = p;
        }

        if (killer == null) return;

        // 1. Blood particles effect
        if (configManager.getMainConfig().getBoolean("cosmetics.blood-particles", true)) {
            victim.getWorld().spawnParticle(
                    Particle.DUST,
                    victim.getLocation().add(0, 1, 0),
                    12, 0.25, 0.4, 0.25, 0.05,
                    new Particle.DustOptions(Color.RED, 0.9f)
            );
        }
    }

    // Concurrent map to track who is tracking whom (tracker UUID -> target UUID)
    private final java.util.concurrent.ConcurrentHashMap<UUID, UUID> activeTrackers = new java.util.concurrent.ConcurrentHashMap<>();

    @EventHandler
    public void onPlayerInteract(final org.bukkit.event.player.PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.COMPASS) return;

        // Trigger on right click
        if (event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_AIR || event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            // Cancel default lodestone point behavior
            event.setCancelled(true);

            UUID currentTargetUuid = activeTrackers.get(player.getUniqueId());
            Player target = null;
            if (currentTargetUuid != null) {
                target = Bukkit.getPlayer(currentTargetUuid);
            }

            // If no target or target went offline, find a new target
            if (target == null || !target.isOnline()) {
                activeTrackers.remove(player.getUniqueId());

                // Find online player with highest bounty (excluding themselves)
                Player highestBountyPlayer = null;
                long highestBounty = -1;

                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (online.getUniqueId().equals(player.getUniqueId())) continue;

                    BountyPlayer data = playerDataCache.getPlayer(online.getUniqueId());
                    if (data != null && data.getBounty() > highestBounty) {
                        highestBounty = data.getBounty();
                        highestBountyPlayer = online;
                    }
                }

                if (highestBountyPlayer != null) {
                    target = highestBountyPlayer;
                    activeTrackers.put(player.getUniqueId(), target.getUniqueId());
                    player.sendMessage(PolarSMP.miniMessage().deserialize(
                            "<gradient:#FFD700:#FFA500>[PolarSMP]</gradient> <green>Locked tracker compass onto </green><gold>" + target.getName() + "</gold><green> (Bounty: " + highestBounty + " coins).</green>"
                    ));
                    SoundUtil.playSound(player, configManager.getSound("purchase-success"));
                } else {
                    player.sendMessage(PolarSMP.miniMessage().deserialize(
                            "<gradient:#FFD700:#FFA500>[PolarSMP]</gradient> <red>No other online players found to track.</red>"
                    ));
                    SoundUtil.playSound(player, configManager.getSound("errors"));
                    return;
                }
            }

            // Update compass direction and point
            player.setCompassTarget(target.getLocation());
            int distance = (int) player.getLocation().distance(target.getLocation());

            MessageUtil.sendActionBar(player, PolarSMP.miniMessage().deserialize(
                    "<bold><gradient:#FF4444:#FFA500>🧭 TRACKING: " + target.getName() + " | Distance: " + distance + "m</gradient></bold>"
            ));

            // Small portal/tracker effect at player location
            player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation().add(0, 1, 0), 10, 0.2, 0.2, 0.2, 0.1);
        }
    }

    @EventHandler
    public void onPlayerQuit(final org.bukkit.event.player.PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        activeTrackers.remove(uuid);
        // Clear anyone tracking them
        activeTrackers.values().removeIf(targetUuid -> targetUuid.equals(uuid));
    }
}
