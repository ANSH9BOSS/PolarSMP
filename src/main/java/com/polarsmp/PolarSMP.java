package com.polarsmp;

import com.polarsmp.bounty.BountyManager;
import com.polarsmp.bounty.StreakManager;
import com.polarsmp.commands.PolarBountyCommand;
import com.polarsmp.commands.PolarRankCommand;
import com.polarsmp.commands.PolarSMPCommand;
import com.polarsmp.core.ConfigManager;
import com.polarsmp.core.DatabaseManager;
import com.polarsmp.core.DataStore;
import com.polarsmp.core.PlayerDataCache;
import com.polarsmp.gui.GuiManager;
import com.polarsmp.integrations.LuckPermsHook;
import com.polarsmp.integrations.PlaceholderAPIHook;
import com.polarsmp.integrations.VaultHook;
import com.polarsmp.listeners.InventoryListener;
import com.polarsmp.listeners.PlayerJoinQuitListener;
import com.polarsmp.listeners.PvPListener;
import com.polarsmp.listeners.WorldListener;
import com.polarsmp.rank.RankManager;
import com.polarsmp.scoreboard.ScoreboardManager;
import com.polarsmp.util.AntiFarmTracker;
import com.polarsmp.util.CombatLogTracker;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.logging.Level;

/**
 * Main entry point for the PolarSMP plugin.
 *
 * <p>Handles the full enable/disable lifecycle: instantiates all managers,
 * registers all listeners and commands, then tears everything down cleanly
 * on disable. No static manager singletons – everything is injected.</p>
 *
 * @author ANSH9BOSS
 * @version 1.0.0
 */
public final class PolarSMP extends JavaPlugin {

    // ─── Core systems ─────────────────────────────────────────────
    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private DataStore dataStore;
    private PlayerDataCache playerDataCache;

    // ─── Game systems ─────────────────────────────────────────────
    private RankManager rankManager;
    private BountyManager bountyManager;
    private StreakManager streakManager;
    private GuiManager guiManager;
    private ScoreboardManager scoreboardManager;

    // ─── Utilities ────────────────────────────────────────────────
    private AntiFarmTracker antiFarmTracker;
    private CombatLogTracker combatLogTracker;
    private com.polarsmp.listeners.WorldListener worldListener;

    // ─── Integrations ─────────────────────────────────────────────
    private VaultHook vaultHook;
    private PlaceholderAPIHook placeholderAPIHook;
    private LuckPermsHook luckPermsHook;

    @Override
    public void onEnable() {
        // Print the ASCII art banner to console
        printBanner();

        // ── Step 1: Load all configuration files ──────────────────
        configManager = new ConfigManager(this);
        configManager.loadAll();

        // ── Step 2: Initialize database ───────────────────────────
        databaseManager = new DatabaseManager(this, configManager);
        try {
            dataStore = databaseManager.initialize();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to initialize database! Disabling PolarSMP.", e);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // ── Step 3: Initialize cache ───────────────────────────────
        playerDataCache = new PlayerDataCache(this);

        // ── Step 4: Initialize integrations ───────────────────────
        vaultHook = new VaultHook(this, configManager);
        luckPermsHook = new LuckPermsHook(this, configManager);

        // ── Step 5: Initialize utilities ──────────────────────────
        antiFarmTracker = new AntiFarmTracker(this, configManager, dataStore);
        combatLogTracker = new CombatLogTracker(this, configManager);

        // ── Step 6: Initialize game systems ───────────────────────
        rankManager = new RankManager(this, configManager, dataStore, playerDataCache, luckPermsHook);
        bountyManager = new BountyManager(this, configManager, dataStore, playerDataCache, rankManager, vaultHook);
        streakManager = new StreakManager(this, configManager, bountyManager);
        guiManager = new GuiManager(this, configManager, playerDataCache, rankManager, bountyManager, dataStore);
        scoreboardManager = new ScoreboardManager(this, configManager, playerDataCache, rankManager);

        // ── Step 7: Register listeners ────────────────────────────
        var pm = Bukkit.getPluginManager();
        worldListener = new com.polarsmp.listeners.WorldListener(this);
        pm.registerEvents(new PvPListener(this, configManager, rankManager, bountyManager, streakManager,
                antiFarmTracker, combatLogTracker, playerDataCache, dataStore), this);
        pm.registerEvents(new PlayerJoinQuitListener(this, configManager, dataStore, playerDataCache,
                rankManager, scoreboardManager, combatLogTracker, vaultHook), this);
        pm.registerEvents(new InventoryListener(this, guiManager), this);
        pm.registerEvents(worldListener, this);

        // ── Step 8: Register commands ─────────────────────────────
        Objects.requireNonNull(getCommand("polarrank"))
                .setExecutor(new PolarRankCommand(this, configManager, rankManager, playerDataCache, guiManager, dataStore));
        Objects.requireNonNull(getCommand("polarrank"))
                .setTabCompleter(new PolarRankCommand(this, configManager, rankManager, playerDataCache, guiManager, dataStore));

        Objects.requireNonNull(getCommand("polarbounty"))
                .setExecutor(new PolarBountyCommand(this, configManager, bountyManager, playerDataCache, guiManager, dataStore));
        Objects.requireNonNull(getCommand("polarbounty"))
                .setTabCompleter(new PolarBountyCommand(this, configManager, bountyManager, playerDataCache, guiManager, dataStore));

        Objects.requireNonNull(getCommand("polarsmp"))
                .setExecutor(new PolarSMPCommand(this, configManager, rankManager, bountyManager, vaultHook, luckPermsHook));

        // ── Step 9: Register PlaceholderAPI expansion ─────────────
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            placeholderAPIHook = new PlaceholderAPIHook(this, playerDataCache, rankManager);
            placeholderAPIHook.register();
            getLogger().info("PlaceholderAPI expansion registered.");
        } else {
            getLogger().info("PlaceholderAPI not found – expansion not registered.");
        }

        // ── Step 10: Start recurring tasks ────────────────────────
        bountyManager.startPassiveBountyTask();
        scoreboardManager.startAnimationTask();
        startAutoSaveTask();

        // ── Step 11: Load online players (reload scenario) ────────
        for (var player : Bukkit.getOnlinePlayers()) {
            playerDataCache.loadPlayer(player.getUniqueId(), player.getName(), dataStore);
            rankManager.onPlayerJoin(player);
            scoreboardManager.createScoreboard(player);
        }

        getLogger().info("PolarSMP v" + getDescription().getVersion() + " by ANSH9BOSS enabled successfully!");
    }

    @Override
    public void onDisable() {
        getLogger().info("PolarSMP disabling – saving data and cleaning up...");

        // ── Stop tasks ────────────────────────────────────────────
        Bukkit.getScheduler().cancelTasks(this);

        // ── Remove all scoreboards ────────────────────────────────
        if (scoreboardManager != null) {
            for (var player : Bukkit.getOnlinePlayers()) {
                scoreboardManager.removeScoreboard(player);
            }
        }

        // ── Remove all boss bars ──────────────────────────────────
        if (streakManager != null) {
            streakManager.removeAllBossBars();
        }

        // ── Close all GUIs ────────────────────────────────────────
        if (guiManager != null) {
            guiManager.closeAll();
        }

        // ── Synchronous save of all dirty cache entries ────────────
        if (playerDataCache != null && dataStore != null) {
            playerDataCache.saveAllSync(dataStore);
        }

        // ── Save all ranks ────────────────────────────────────────
        if (rankManager != null) {
            rankManager.saveAllRanksSync(dataStore);
        }

        // ── Close database connection pool ────────────────────────
        if (databaseManager != null) {
            databaseManager.close();
        }

        // ── Unregister PlaceholderAPI expansion ───────────────────
        if (placeholderAPIHook != null) {
            placeholderAPIHook.unregister();
        }

        getLogger().info("PolarSMP disabled cleanly. Goodbye!");
    }

    /**
     * Starts the auto-save repeating task that persists online player data
     * to the database at a configurable interval.
     */
    private void startAutoSaveTask() {
        long intervalMinutes = configManager.getMainConfig().getLong("auto-save-interval-minutes", 5);
        long intervalTicks = intervalMinutes * 60L * 20L;
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            int saved = playerDataCache.saveAllDirtyAsync(dataStore);
            if (configManager.isDebug()) {
                getLogger().info("[AutoSave] Saved " + saved + " dirty player records.");
            }
        }, intervalTicks, intervalTicks);
    }

    /**
     * Prints the PolarSMP ASCII art banner to the server console.
     */
    private void printBanner() {
        getLogger().info("    ____        __          _____ __  _______  ");
        getLogger().info("   / __ \\____  / /___ _____/ ___//  |/  / __ \\ ");
        getLogger().info("  / /_/ / __ \\/ / __ `/ __/\\__ \\/ /|_/ / /_/ /");
        getLogger().info(" / ____/ /_/ / / /_/ / /  ___/ / /  / / ____/ ");
        getLogger().info("/_/    \\____/_/\\__,_/_/  /____/_/  /_/_/       ");
        getLogger().info("Developer: ANSH9BOSS | Version: 1.0.0 | Paper 1.21+");
        getLogger().info("─────────────────────────────────────────────────");
    }

    // ─── Getters ──────────────────────────────────────────────────

    /** @return the singleton MiniMessage instance for component parsing */
    public static MiniMessage miniMessage() {
        return MiniMessage.miniMessage();
    }

    /** @return the ConfigManager instance */
    public ConfigManager getConfigManager() { return configManager; }

    /** @return the DataStore instance */
    public DataStore getDataStore() { return dataStore; }

    /** @return the PlayerDataCache instance */
    public PlayerDataCache getPlayerDataCache() { return playerDataCache; }

    /** @return the RankManager instance */
    public RankManager getRankManager() { return rankManager; }

    /** @return the BountyManager instance */
    public BountyManager getBountyManager() { return bountyManager; }

    /** @return the StreakManager instance */
    public StreakManager getStreakManager() { return streakManager; }

    /** @return the GuiManager instance */
    public GuiManager getGuiManager() { return guiManager; }

    /** @return the ScoreboardManager instance */
    public ScoreboardManager getScoreboardManager() { return scoreboardManager; }

    /** @return the VaultHook instance */
    public VaultHook getVaultHook() { return vaultHook; }

    /** @return the LuckPermsHook instance */
    public LuckPermsHook getLuckPermsHook() { return luckPermsHook; }

    /** @return the AntiFarmTracker instance */
    public AntiFarmTracker getAntiFarmTracker() { return antiFarmTracker; }

    /** @return the CombatLogTracker instance */
    public CombatLogTracker getCombatLogTracker() { return combatLogTracker; }

    /** @return the WorldListener instance */
    public com.polarsmp.listeners.WorldListener getWorldListener() { return worldListener; }
}
