package com.polarsmp.core;

import com.polarsmp.PolarSMP;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

/**
 * Manages all configuration files for PolarSMP.
 *
 * <p>Handles initial generation from bundled defaults on first run and
 * supports hot-reload of all files without a server restart.</p>
 *
 * <p>Config files managed:</p>
 * <ol>
 *   <li>config.yml – database, anti-farm, combat-log, world-filter, sounds</li>
 *   <li>messages.yml – all plugin messages in MiniMessage format</li>
 *   <li>ranks.yml – rank perk definitions</li>
 *   <li>bounty.yml – economy rates and milestone definitions</li>
 *   <li>shop.yml – shop item definitions</li>
 *   <li>rewards.yml – reward milestone definitions</li>
 *   <li>scoreboard.yml – scoreboard layout and animation frames</li>
 * </ol>
 *
 * @author ANSH9BOSS
 * @version 1.0.0
 */
public final class ConfigManager {

    private final PolarSMP plugin;

    private FileConfiguration mainConfig;
    private FileConfiguration messagesConfig;
    private FileConfiguration ranksConfig;
    private FileConfiguration bountyConfig;
    private FileConfiguration shopConfig;
    private FileConfiguration rewardsConfig;
    private FileConfiguration scoreboardConfig;

    private File messagesFile;
    private File ranksFile;
    private File bountyFile;
    private File shopFile;
    private File rewardsFile;
    private File scoreboardFile;

    /**
     * Constructs a new ConfigManager.
     *
     * @param plugin the PolarSMP plugin instance
     */
    public ConfigManager(final PolarSMP plugin) {
        this.plugin = plugin;
    }

    /**
     * Loads all configuration files from disk, generating defaults if absent.
     */
    public void loadAll() {
        saveDefaultIfAbsent("config.yml");
        saveDefaultIfAbsent("messages.yml");
        saveDefaultIfAbsent("ranks.yml");
        saveDefaultIfAbsent("bounty.yml");
        saveDefaultIfAbsent("shop.yml");
        saveDefaultIfAbsent("rewards.yml");
        saveDefaultIfAbsent("scoreboard.yml");

        mainConfig = plugin.getConfig();
        messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        ranksFile = new File(plugin.getDataFolder(), "ranks.yml");
        bountyFile = new File(plugin.getDataFolder(), "bounty.yml");
        shopFile = new File(plugin.getDataFolder(), "shop.yml");
        rewardsFile = new File(plugin.getDataFolder(), "rewards.yml");
        scoreboardFile = new File(plugin.getDataFolder(), "scoreboard.yml");

        messagesConfig = loadYaml(messagesFile);
        ranksConfig = loadYaml(ranksFile);
        bountyConfig = loadYaml(bountyFile);
        shopConfig = loadYaml(shopFile);
        rewardsConfig = loadYaml(rewardsFile);
        scoreboardConfig = loadYaml(scoreboardFile);

        validateConfigs();
        plugin.getLogger().info("All configuration files loaded.");
    }

    /**
     * Hot-reloads all configuration files from disk.
     * Existing in-memory references are replaced.
     */
    public void reloadAll() {
        plugin.reloadConfig();
        mainConfig = plugin.getConfig();
        messagesConfig = loadYaml(messagesFile);
        ranksConfig = loadYaml(ranksFile);
        bountyConfig = loadYaml(bountyFile);
        shopConfig = loadYaml(shopFile);
        rewardsConfig = loadYaml(rewardsFile);
        scoreboardConfig = loadYaml(scoreboardFile);
        plugin.getLogger().info("Configuration hot-reloaded.");
    }

    /**
     * Copies a resource file from the plugin JAR to the data folder if it does not exist.
     *
     * @param resourceName the resource file name (e.g. "config.yml")
     */
    private void saveDefaultIfAbsent(final String resourceName) {
        File target = new File(plugin.getDataFolder(), resourceName);
        if (!target.exists()) {
            plugin.saveResource(resourceName, false);
        }
    }

    /**
     * Loads a YAML file and merges in any missing keys from the bundled defaults.
     *
     * @param file the file to load
     * @return the loaded FileConfiguration
     */
    private FileConfiguration loadYaml(final File file) {
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        // Merge defaults from bundled resource
        String name = file.getName();
        try (InputStream is = plugin.getResource(name)) {
            if (is != null) {
                FileConfiguration defaults = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(is, StandardCharsets.UTF_8));
                config.setDefaults(defaults);
                config.options().copyDefaults(true);
                try {
                    config.save(file);
                } catch (IOException e) {
                    plugin.getLogger().log(Level.WARNING, "Could not save defaults to " + name, e);
                }
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not read bundled resource " + name, e);
        }
        return config;
    }

    /**
     * Validates critical config keys exist and logs warnings for any that are missing.
     */
    private void validateConfigs() {
        requireKey(mainConfig, "database.type", "config.yml");
        requireKey(mainConfig, "anti-farm.enabled", "config.yml");
        requireKey(mainConfig, "combat-log.enabled", "config.yml");
        requireKey(bountyConfig, "base-kill-coins", "bounty.yml");
        requireKey(scoreboardConfig, "enabled", "scoreboard.yml");
    }

    /**
     * Logs a warning if a required config key is missing.
     *
     * @param config   the configuration to check
     * @param key      the key path to verify
     * @param fileName the file name for logging context
     */
    private void requireKey(final FileConfiguration config, final String key, final String fileName) {
        if (!config.contains(key)) {
            plugin.getLogger().warning("[ConfigManager] Missing key '" + key + "' in " + fileName
                    + " – using default value.");
        }
    }

    // ─── Accessors ────────────────────────────────────────────────

    /**
     * @return the main config.yml configuration
     */
    public FileConfiguration getMainConfig() { return mainConfig; }

    /**
     * @return the messages.yml configuration
     */
    public FileConfiguration getMessagesConfig() { return messagesConfig; }

    /**
     * @return the ranks.yml configuration
     */
    public FileConfiguration getRanksConfig() { return ranksConfig; }

    /**
     * @return the bounty.yml configuration
     */
    public FileConfiguration getBountyConfig() { return bountyConfig; }

    /**
     * @return the shop.yml configuration
     */
    public FileConfiguration getShopConfig() { return shopConfig; }

    /**
     * @return the rewards.yml configuration
     */
    public FileConfiguration getRewardsConfig() { return rewardsConfig; }

    /**
     * @return the scoreboard.yml configuration
     */
    public FileConfiguration getScoreboardConfig() { return scoreboardConfig; }

    /**
     * @return whether debug mode is enabled
     */
    public boolean isDebug() {
        return mainConfig.getBoolean("debug", false);
    }

    /**
     * Gets a message from messages.yml by dot-path key.
     *
     * @param key the message key path
     * @return the MiniMessage string or an empty string if not found
     */
    public String getMessage(final String key) {
        String raw = messagesConfig.getString(key, "");
        String prefix = messagesConfig.getString("prefix", "");
        if (raw.contains("{prefix}")) {
            raw = raw.replace("{prefix}", prefix);
        }
        return raw;
    }

    /**
     * Gets a sound entry from config.yml.
     *
     * @param key the sound event key (e.g. "rank-gained")
     * @return the SoundConfig or null if not found
     */
    public SoundConfig getSound(final String key) {
        String path = "sounds." + key;
        if (!mainConfig.contains(path)) return null;
        return new SoundConfig(
                mainConfig.getString(path + ".sound", "UI_BUTTON_CLICK"),
                (float) mainConfig.getDouble(path + ".volume", 1.0),
                (float) mainConfig.getDouble(path + ".pitch", 1.0)
        );
    }

    /**
     * Simple record representing a sound configuration.
     *
     * @param sound  the Bukkit sound name
     * @param volume the playback volume
     * @param pitch  the playback pitch
     */
    public record SoundConfig(String sound, float volume, float pitch) {}
}
