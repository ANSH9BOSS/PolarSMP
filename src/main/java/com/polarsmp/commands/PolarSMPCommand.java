package com.polarsmp.commands;

import com.polarsmp.PolarSMP;
import com.polarsmp.core.ConfigManager;
import com.polarsmp.integrations.LuckPermsHook;
import com.polarsmp.integrations.VaultHook;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.List;

/**
 * Hub command showing info, developer credit (ANSH9BOSS), system integrations status,
 * and support for downloading updates from GitHub.
 *
 * @author ANSH9BOSS
 * @version 1.0.0
 */
public final class PolarSMPCommand implements CommandExecutor, TabCompleter {

    private final PolarSMP plugin;
    private final ConfigManager configManager;
    private final com.polarsmp.rank.RankManager rankManager;
    private final com.polarsmp.bounty.BountyManager bountyManager;
    private final VaultHook vaultHook;
    private final LuckPermsHook luckPermsHook;

    /**
     * Constructs a new PolarSMPCommand.
     */
    public PolarSMPCommand(final PolarSMP plugin, final ConfigManager configManager,
                           final com.polarsmp.rank.RankManager rankManager,
                           final com.polarsmp.bounty.BountyManager bountyManager,
                           final VaultHook vaultHook, final LuckPermsHook luckPermsHook) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.rankManager = rankManager;
        this.bountyManager = bountyManager;
        this.vaultHook = vaultHook;
        this.luckPermsHook = luckPermsHook;
    }

    @Override
    public boolean onCommand(@NotNull final CommandSender sender, @NotNull final Command command,
                             @NotNull final String label, @NotNull final String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("update")) {
            if (!sender.hasPermission("polarsmp.admin")) {
                sender.sendMessage(PolarSMP.miniMessage().deserialize(configManager.getMessage("errors.no-permission")));
                return true;
            }

            sender.sendMessage(PolarSMP.miniMessage().deserialize("<yellow>Checking for updates from GitHub...</yellow>"));

            // Async download of the latest release jar from GitHub
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    File jarFile = new File(plugin.getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
                    sender.sendMessage(PolarSMP.miniMessage().deserialize("<yellow>Downloading latest jar update...</yellow>"));

                    URL url = new URL("https://github.com/ANSH9BOSS/PolarSMP/releases/latest/download/PolarSMP.jar");
                    try (InputStream in = url.openStream();
                         FileOutputStream out = new FileOutputStream(jarFile)) {
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = in.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                        }
                    }

                    sender.sendMessage(PolarSMP.miniMessage().deserialize("<green>Update downloaded successfully! Restarting the server...</green>"));

                    // Dispatch restart on main thread
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "restart");
                    });
                } catch (Exception e) {
                    sender.sendMessage(PolarSMP.miniMessage().deserialize("<red>Failed to update: " + e.getMessage() + "</red>"));
                }
            });
            return true;
        }

        // Output styled info card
        String seasonStatus = rankManager.isSeasonActive() ? "<green>Active</green>" : "<red>Inactive</red>";
        String dbType = configManager.getMainConfig().getString("database.type", "sqlite").toUpperCase();
        String vaultStatus = vaultHook.isEnabled() ? "<green>Enabled</green>" : "<red>Disabled</red>";
        String lpStatus = luckPermsHook.isAvailable() ? "<green>Linked</green>" : "<red>Not Found</red>";
        String papiStatus = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI") ? "<green>Linked</green>" : "<red>Not Found</red>";

        sender.sendMessage(PolarSMP.miniMessage().deserialize("<gradient:#FFD700:#FFA500>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>"));
        sender.sendMessage(PolarSMP.miniMessage().deserialize("<bold><gradient:#FFD700:#FFA500>    ✦ PolarSMP v1.0.0 Info card ✦</gradient></bold>"));
        sender.sendMessage(PolarSMP.miniMessage().deserialize(""));
        sender.sendMessage(PolarSMP.miniMessage().deserialize("<gray>  Developer: </gray><gold>ANSH9BOSS</gold>"));
        sender.sendMessage(PolarSMP.miniMessage().deserialize("<gray>  Season Status: </gray>" + seasonStatus));
        sender.sendMessage(PolarSMP.miniMessage().deserialize("<gray>  Database: </gray><aqua>" + dbType + "</aqua>"));
        sender.sendMessage(PolarSMP.miniMessage().deserialize("<gray>  Vault Bridge: </gray>" + vaultStatus));
        sender.sendMessage(PolarSMP.miniMessage().deserialize("<gray>  LuckPerms Integration: </gray>" + lpStatus));
        sender.sendMessage(PolarSMP.miniMessage().deserialize("<gray>  PlaceholderAPI Integration: </gray>" + papiStatus));
        sender.sendMessage(PolarSMP.miniMessage().deserialize("<gray>  Online players: </gray><yellow>" + Bukkit.getOnlinePlayers().size() + "</yellow>"));
        sender.sendMessage(PolarSMP.miniMessage().deserialize("<gradient:#FFD700:#FFA500>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>"));

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull final CommandSender sender, @NotNull final Command command,
                                      @NotNull final String alias, @NotNull final String[] args) {
        if (args.length == 1 && sender.hasPermission("polarsmp.admin")) {
            return List.of("update");
        }
        return Collections.emptyList();
    }
}
