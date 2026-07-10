package com.polarsmp.commands;

import com.polarsmp.PolarSMP;
import com.polarsmp.core.ConfigManager;
import com.polarsmp.integrations.LuckPermsHook;
import com.polarsmp.integrations.VaultHook;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

/**
 * Hub command showing info, developer credit (ANSH9BOSS), and system integrations status.
 *
 * @author ANSH9BOSS
 * @version 1.0.0
 */
public final class PolarSMPCommand implements CommandExecutor {

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
}
