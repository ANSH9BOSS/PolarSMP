package com.polarsmp.bounty;

import com.polarsmp.PolarSMP;
import com.polarsmp.core.ConfigManager;
import com.polarsmp.util.FormatUtil;
import com.polarsmp.util.MessageUtil;
import com.polarsmp.util.SoundUtil;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages kill streak tracking, milestone detection, and BossBar display.
 *
 * <p>Each online player with an active streak receives a personalised BossBar
 * showing their current streak and progress toward the next milestone. The bar
 * animates smoothly when the streak increases, and its color changes based on
 * the current streak level.</p>
 *
 * @author ANSH9BOSS
 * @version 1.0.0
 */
public final class StreakManager {

    private final PolarSMP plugin;
    private final ConfigManager configManager;
    private final BountyManager bountyManager;

    /** Active BossBars per player UUID. */
    private final Map<UUID, BossBar> bossBars = new HashMap<>();

    /** Milestone streak values in ascending order. */
    private static final int[] MILESTONES = {3, 5, 10, 15, 20};

    /**
     * Constructs a new StreakManager.
     *
     * @param plugin        the PolarSMP plugin instance
     * @param configManager the config manager
     * @param bountyManager the bounty manager for milestone coin awards
     */
    public StreakManager(final PolarSMP plugin, final ConfigManager configManager,
                         final BountyManager bountyManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.bountyManager = bountyManager;
    }

    /**
     * Processes a streak increment for the killer and checks for milestones.
     * Must be called on the main thread.
     *
     * @param killer     the killing player
     * @param killerData the killer's data model
     */
    public void processStreakKill(final Player killer, final BountyPlayer killerData) {
        int newStreak = killerData.getKillStreak(); // Already incremented by BountyPlayer

        // Update/create BossBar
        updateBossBar(killer, killerData);

        // Check milestone
        if (bountyManager.isMilestone(newStreak)) {
            long bonus = bountyManager.getMilestoneBonus(newStreak);

            // Award milestone coins
            if (bonus > 0) {
                killerData.addCoins(bonus);
            }

            // Award milestone bounty
            bountyManager.addStreakMilestoneBounty(killer);

            // Send milestone title
            String titleText = "<gradient:#FF8C00:#FF4500><bold>🔥 KILL STREAK x" + newStreak + "</bold></gradient>";
            String subtitleText = "<yellow>+" + FormatUtil.formatNumber(bonus) + " Bonus Coins!</yellow>";
            MessageUtil.sendTitle(killer,
                    MiniMessage.miniMessage().deserialize(titleText),
                    MiniMessage.miniMessage().deserialize(subtitleText),
                    10, 60, 20);

            // Send chat notification
            String milestoneMsg = configManager.getMessage("streak.milestone")
                    .replace("<streak>", String.valueOf(newStreak))
                    .replace("<bonus>", FormatUtil.formatNumber(bonus));
            killer.sendMessage(MiniMessage.miniMessage().deserialize(milestoneMsg));

            // Server-wide broadcast for significant milestones
            if (newStreak >= 5) {
                String broadcast = configManager.getMessage("streak.milestone-broadcast")
                        .replace("<player>", killer.getName())
                        .replace("<streak>", String.valueOf(newStreak));
                MessageUtil.broadcastMessage(MiniMessage.miniMessage().deserialize(broadcast));
            }

            // Play streak sound
            SoundUtil.playSound(killer, configManager.getSound("streak-milestone"));

            // Spawn particle helix on milestone
            spawnHelixParticles(killer, newStreak);
        }
    }

    /**
     * Creates or updates the BossBar for a player based on their current streak.
     *
     * @param player     the player to update the BossBar for
     * @param playerData the player's current data
     */
    public void updateBossBar(final Player player, final BountyPlayer playerData) {
        int streak = playerData.getKillStreak();

        if (streak <= 0) {
            // Remove BossBar if streak is zero
            removeBossBar(player);
            return;
        }

        BossBar bar = bossBars.computeIfAbsent(player.getUniqueId(), uuid -> {
            BossBar newBar = BossBar.bossBar(
                    Component.empty(),
                    0f,
                    getBossBarColor(streak),
                    BossBar.Overlay.PROGRESS
            );
            player.showBossBar(newBar);
            return newBar;
        });

        // Update title
        int nextMilestone = getNextMilestone(streak);
        String titleText;
        if (nextMilestone > 0) {
            titleText = "<bold><gradient:#FF8C00:#FF4500>🔥 Streak: " + streak + "</gradient></bold>"
                    + " <gray>→ Next Milestone: <yellow>" + nextMilestone + "</yellow></gray>";
        } else {
            titleText = "<bold><gradient:#FF0000:#8B0000>🔥 GODLIKE STREAK: " + streak + "</gradient></bold>";
        }

        bar.name(MiniMessage.miniMessage().deserialize(titleText));

        // Update progress bar
        float progress;
        if (nextMilestone > 0) {
            int prevMilestone = getPreviousMilestone(streak);
            int range = nextMilestone - prevMilestone;
            int current = streak - prevMilestone;
            progress = Math.min(1.0f, (float) current / range);
        } else {
            progress = 1.0f;
        }

        // Animate bar filling
        animateBarProgress(bar, progress, getBossBarColor(streak));
    }

    /**
     * Removes a player's BossBar.
     *
     * @param player the player to remove the BossBar for
     */
    public void removeBossBar(final Player player) {
        BossBar bar = bossBars.remove(player.getUniqueId());
        if (bar != null) {
            player.hideBossBar(bar);
        }
    }

    /**
     * Removes all active BossBars. Called on plugin disable.
     */
    public void removeAllBossBars() {
        bossBars.forEach((uuid, bar) -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.hideBossBar(bar);
        });
        bossBars.clear();
    }

    /**
     * Animates the BossBar progress filling over 10 ticks.
     *
     * @param bar      the boss bar to animate
     * @param target   the target progress (0–1)
     * @param color    the boss bar color to set
     */
    private void animateBarProgress(final BossBar bar, final float target, final BossBar.Color color) {
        float current = bar.progress();
        float step = (target - current) / 10.0f;

        if (Math.abs(target - current) < 0.01f) {
            bar.progress(target);
            bar.color(color);
            return;
        }

        // Schedule 10-tick animation
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            float newProgress = bar.progress() + step;
            if ((step > 0 && newProgress >= target) || (step < 0 && newProgress <= target)) {
                bar.progress(target);
                bar.color(color);
                task.cancel();
            } else {
                bar.progress(Math.max(0f, Math.min(1f, newProgress)));
            }
        }, 0L, 1L);
    }

    /**
     * Returns the BossBar color for the given streak.
     *
     * @param streak the current streak
     * @return the BossBar color
     */
    private BossBar.Color getBossBarColor(final int streak) {
        if (streak >= 20) return BossBar.Color.PINK;
        if (streak >= 15) return BossBar.Color.PURPLE;
        if (streak >= 10) return BossBar.Color.RED;
        if (streak >= 5) return BossBar.Color.YELLOW;
        return BossBar.Color.GREEN;
    }

    /**
     * Returns the next milestone above the given streak, or -1 if none.
     *
     * @param streak the current streak
     * @return the next milestone, or -1
     */
    private int getNextMilestone(final int streak) {
        for (int m : MILESTONES) {
            if (m > streak) return m;
        }
        return -1;
    }

    /**
     * Returns the milestone at or below the given streak.
     *
     * @param streak the current streak
     * @return the previous milestone, or 0
     */
    private int getPreviousMilestone(final int streak) {
        int prev = 0;
        for (int m : MILESTONES) {
            if (m <= streak) prev = m;
        }
        return prev;
    }

    /**
     * Spawns flame particles in a helix pattern above the player on a streak milestone.
     *
     * @param player the player to display the particles around
     * @param streak the milestone streak value (affects height)
     */
    private void spawnHelixParticles(final Player player, final int streak) {
        final int ticks = 40;
        final double radius = 0.5;
        final double speed = 0.3;

        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (!player.isOnline()) {
                task.cancel();
                return;
            }
            for (int i = 0; i < ticks; i++) {
                double angle = (2 * Math.PI * i) / ticks;
                double x = radius * Math.cos(angle);
                double z = radius * Math.sin(angle);
                double y = i * 0.05;
                player.getWorld().spawnParticle(
                        org.bukkit.Particle.FLAME,
                        player.getLocation().add(x, y, z),
                        0, 0, speed, 0, 0.05
                );
            }
            task.cancel();
        }, 0L, 1L);
    }
}
