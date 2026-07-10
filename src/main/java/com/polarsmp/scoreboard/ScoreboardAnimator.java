package com.polarsmp.scoreboard;

import com.polarsmp.core.ConfigManager;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gradient title animation engine for PolarSMP scoreboard.
 *
 * <p>Cycles through a list of MiniMessage frames defined in scoreboard.yml
 * to produce smooth animated title transitions for each player.</p>
 *
 * @author ANSH9BOSS
 * @version 1.0.0
 */
public final class ScoreboardAnimator {

    private final ConfigManager configManager;
    private final List<String> frames = new ArrayList<>();
    private final ConcurrentHashMap<UUID, Integer> playerFrames = new ConcurrentHashMap<>();

    /**
     * Constructs a new ScoreboardAnimator.
     *
     * @param configManager the configuration manager
     */
    public ScoreboardAnimator(final ConfigManager configManager) {
        this.configManager = configManager;
        loadFrames();
    }

    /**
     * Loads the animation frames from scoreboard.yml.
     */
    public void loadFrames() {
        frames.clear();
        FileConfiguration config = configManager.getScoreboardConfig();
        List<String> list = config.getStringList("title-animation-frames");
        if (list.isEmpty()) {
            // Sensible default frames if not configured
            frames.add("<gradient:#FFD700:#FFA500><bold>✦ PolarSMP ✦</bold></gradient>");
            frames.add("<gradient:#FFA500:#FF8C00><bold>✦ PolarSMP ✦</bold></gradient>");
        } else {
            frames.addAll(list);
        }
    }

    /**
     * Retrieves the next animation frame for a player and increments their index.
     *
     * @param uuid the player's UUID
     * @return the MiniMessage color frame
     */
    public String getNextFrame(final UUID uuid) {
        if (frames.isEmpty()) return "";
        int index = playerFrames.compute(uuid, (key, current) -> {
            if (current == null) return 0;
            return (current + 1) % frames.size();
        });
        return frames.get(index);
    }

    /**
     * Resets the frame index tracker for a player on disconnect.
     *
     * @param uuid the player's UUID
     */
    public void resetPlayer(final UUID uuid) {
        playerFrames.remove(uuid);
    }
}
