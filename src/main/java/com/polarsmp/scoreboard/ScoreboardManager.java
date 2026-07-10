package com.polarsmp.scoreboard;

import com.polarsmp.PolarSMP;
import com.polarsmp.bounty.BountyPlayer;
import com.polarsmp.core.ConfigManager;
import com.polarsmp.core.PlayerDataCache;
import com.polarsmp.rank.RankManager;
import com.polarsmp.util.FormatUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages animated, per-player sidebars.
 *
 * <p>Never shares Scoreboard instances between players. Uses team-based lines
 * to allow flicker-free updates.</p>
 *
 * @author ANSH9BOSS
 * @version 1.0.0
 */
public final class ScoreboardManager {

    private final PolarSMP plugin;
    private final ConfigManager configManager;
    private final PlayerDataCache playerDataCache;
    private final RankManager rankManager;
    private final ScoreboardAnimator animator;

    private final ConcurrentHashMap<UUID, Scoreboard> scoreboards = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Objective> objectives = new ConcurrentHashMap<>();
    private org.bukkit.scheduler.BukkitTask animationTask;

    /**
     * Constructs a new ScoreboardManager.
     *
     * @param plugin          the PolarSMP plugin instance
     * @param configManager   the config manager
     * @param playerDataCache the player cache
     * @param rankManager     the rank manager
     */
    public ScoreboardManager(final PolarSMP plugin, final ConfigManager configManager,
                             final PlayerDataCache playerDataCache, final RankManager rankManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.playerDataCache = playerDataCache;
        this.rankManager = rankManager;
        this.animator = new ScoreboardAnimator(configManager);
    }

    /**
     * Creates a fresh sidebar scoreboard for a player.
     *
     * @param player the player
     */
    public void createScoreboard(final Player player) {
        if (!configManager.getScoreboardConfig().getBoolean("enabled", true)) return;

        UUID uuid = player.getUniqueId();
        Scoreboard sb = Objects.requireNonNull(Bukkit.getScoreboardManager()).getNewScoreboard();

        // Unique objective name using first 8 characters of UUID (ignoring dashes)
        String objName = "psmp_" + uuid.toString().replace("-", "").substring(0, 8);
        Objective obj = sb.registerNewObjective(objName, Criteria.DUMMY, Component.text("PolarSMP"));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        // Load configured lines
        List<String> rawLines = configManager.getScoreboardConfig().getStringList("lines");
        int score = rawLines.size();

        for (int i = 0; i < rawLines.size(); i++) {
            // Create a team to control this line's text
            String teamName = "psmp_line_" + i;
            Team team = sb.registerNewTeam(teamName);

            // Dummy entry representing the line's position (e.g. §1, §2, etc.)
            String dummyEntry = getDummyEntry(i);
            team.addEntry(dummyEntry);

            // Set content as prefix/suffix
            team.prefix(Component.empty());
            team.suffix(Component.empty());

            // Set the score to set the index
            obj.getScore(dummyEntry).setScore(score - i);
        }

        player.setScoreboard(sb);
        scoreboards.put(uuid, sb);
        objectives.put(uuid, obj);

        // Perform initial line update
        updateScoreboard(player);
    }

    /**
     * Removes the sidebar scoreboard from a player and returns them to the main scoreboard.
     *
     * @param player the player
     */
    public void removeScoreboard(final Player player) {
        UUID uuid = player.getUniqueId();
        scoreboards.remove(uuid);
        objectives.remove(uuid);
        animator.resetPlayer(uuid);
        try {
            player.setScoreboard(Objects.requireNonNull(Bukkit.getScoreboardManager()).getMainScoreboard());
        } catch (Exception ignored) {}
    }

    /**
     * Updates the scoreboard values (lines and animated title) for a player.
     *
     * @param player the player
     */
    public void updateScoreboard(final Player player) {
        UUID uuid = player.getUniqueId();
        Scoreboard sb = scoreboards.get(uuid);
        Objective obj = objectives.get(uuid);
        BountyPlayer p = playerDataCache.getPlayer(uuid);

        if (sb == null || obj == null || p == null) return;

        // 1. Update animated title
        String titleMM = animator.getNextFrame(uuid);
        obj.displayName(PolarSMP.miniMessage().deserialize(titleMM));

        // 2. Update lines
        List<String> rawLines = configManager.getScoreboardConfig().getStringList("lines");
        for (int i = 0; i < rawLines.size(); i++) {
            Team team = sb.getTeam("psmp_line_" + i);
            if (team == null) continue;

            String rawLine = rawLines.get(i);
            if (rawLine.trim().isEmpty()) {
                team.prefix(Component.empty());
                continue;
            }

            // Replace placeholders
            Integer rankNum = rankManager.getRank(uuid);
            String rankStr = rankNum != null ? String.valueOf(rankNum)
                    : configManager.getMessagesConfig().getString("rank.check-unranked", "Unranked");

            String processed = rawLine
                    .replace("{rank}", rankStr)
                    .replace("{coins}", FormatUtil.formatNumber(p.getCoins()))
                    .replace("{bounty}", FormatUtil.formatNumber(p.getBounty()))
                    .replace("{streak}", String.valueOf(p.getKillStreak()))
                    .replace("{kills}", String.valueOf(p.getTotalKills()))
                    .replace("{deaths}", String.valueOf(p.getTotalDeaths()))
                    .replace("{kd}", FormatUtil.formatKD(p.getTotalKills(), p.getTotalDeaths()));

            // Deserialize with MiniMessage and apply
            Component component = PolarSMP.miniMessage().deserialize(processed);
            team.prefix(component);
        }
    }

    /**
     * Starts the repeating task that updates all online player scoreboards.
     */
    public void startAnimationTask() {
        long updateInterval = configManager.getScoreboardConfig().getLong("update-interval-ticks", 20);
        animationTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (scoreboards.containsKey(player.getUniqueId())) {
                    updateScoreboard(player);
                }
            }
        }, 20L, updateInterval);
    }

    /**
     * Helper to get a unique color code combination for team entry lines.
     *
     * @param index line index
     * @return dummy color code string
     */
    private String getDummyEntry(final int index) {
        char code = Integer.toHexString(index).charAt(0);
        return "§" + code + "§r";
    }

    /**
     * Reloads the animator configuration.
     */
    public void reload() {
        animator.loadFrames();
    }
}
