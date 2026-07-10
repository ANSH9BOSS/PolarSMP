package com.polarsmp.commands;

import com.polarsmp.PolarSMP;
import com.polarsmp.bounty.BountyManager;
import com.polarsmp.bounty.BountyPlayer;
import com.polarsmp.core.ConfigManager;
import com.polarsmp.core.DataStore;
import com.polarsmp.core.PlayerDataCache;
import com.polarsmp.gui.GuiManager;
import com.polarsmp.util.FormatUtil;
import com.polarsmp.util.MessageUtil;
import com.polarsmp.util.SoundUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles all player and admin PolarBounty subcommands.
 *
 * @author ANSH9BOSS
 * @version 1.0.0
 */
public final class PolarBountyCommand implements CommandExecutor, TabCompleter {

    private final PolarSMP plugin;
    private final ConfigManager configManager;
    private final BountyManager bountyManager;
    private final PlayerDataCache cache;
    private final GuiManager guiManager;
    private final DataStore dataStore;

    // Track confirmation timestamps for stats resetting: UUID -> Timestamp (ms)
    private final ConcurrentHashMap<UUID, Long> pendingConfirm = new ConcurrentHashMap<>();

    /**
     * Constructs a new PolarBountyCommand.
     */
    public PolarBountyCommand(final PolarSMP plugin, final ConfigManager configManager,
                              final BountyManager bountyManager, final PlayerDataCache cache,
                              final GuiManager guiManager, final DataStore dataStore) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.bountyManager = bountyManager;
        this.cache = cache;
        this.guiManager = guiManager;
        this.dataStore = dataStore;
    }

    @Override
    public boolean onCommand(@NotNull final CommandSender sender, @NotNull final Command command,
                             @NotNull final String label, @NotNull final String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "help":
                if (!sender.hasPermission("polarbounty.player")) {
                    sender.sendMessage(PolarSMP.miniMessage().deserialize(configManager.getMessage("errors.no-permission")));
                    return true;
                }
                sendHelp(sender);
                break;

            case "profile":
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(PolarSMP.miniMessage().deserialize(configManager.getMessage("errors.player-only")));
                    return true;
                }
                if (!player.hasPermission("polarbounty.player")) {
                    player.sendMessage(PolarSMP.miniMessage().deserialize(configManager.getMessage("errors.no-permission")));
                    return true;
                }
                handleProfile(player, args);
                break;

            case "rewards":
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(PolarSMP.miniMessage().deserialize(configManager.getMessage("errors.player-only")));
                    return true;
                }
                if (!player.hasPermission("polarbounty.player")) {
                    player.sendMessage(PolarSMP.miniMessage().deserialize(configManager.getMessage("errors.no-permission")));
                    return true;
                }
                guiManager.openRewardsGui(player);
                break;

            case "shop":
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(PolarSMP.miniMessage().deserialize(configManager.getMessage("errors.player-only")));
                    return true;
                }
                if (!player.hasPermission("polarbounty.player")) {
                    player.sendMessage(PolarSMP.miniMessage().deserialize(configManager.getMessage("errors.no-permission")));
                    return true;
                }
                guiManager.openShopGui(player);
                break;

            case "top":
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(PolarSMP.miniMessage().deserialize(configManager.getMessage("errors.player-only")));
                    return true;
                }
                if (!player.hasPermission("polarbounty.player")) {
                    player.sendMessage(PolarSMP.miniMessage().deserialize(configManager.getMessage("errors.no-permission")));
                    return true;
                }
                guiManager.openBountyLeaderboardGui(player);
                break;

            case "contract":
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(PolarSMP.miniMessage().deserialize(configManager.getMessage("errors.player-only")));
                    return true;
                }
                if (!player.hasPermission("polarbounty.player")) {
                    player.sendMessage(PolarSMP.miniMessage().deserialize(configManager.getMessage("errors.no-permission")));
                    return true;
                }
                handleContract(player, args);
                break;

            case "balance":
                if (!sender.hasPermission("polarbounty.player")) {
                    sender.sendMessage(PolarSMP.miniMessage().deserialize(configManager.getMessage("errors.no-permission")));
                    return true;
                }
                handleBalance(sender, args);
                break;

            case "give":
                if (!sender.hasPermission("polarbounty.admin")) {
                    sender.sendMessage(PolarSMP.miniMessage().deserialize(configManager.getMessage("errors.no-permission")));
                    return true;
                }
                handleGive(sender, args);
                break;

            case "take":
                if (!sender.hasPermission("polarbounty.admin")) {
                    sender.sendMessage(PolarSMP.miniMessage().deserialize(configManager.getMessage("errors.no-permission")));
                    return true;
                }
                handleTake(sender, args);
                break;

            case "setbounty":
                if (!sender.hasPermission("polarbounty.admin")) {
                    sender.sendMessage(PolarSMP.miniMessage().deserialize(configManager.getMessage("errors.no-permission")));
                    return true;
                }
                handleSetBounty(sender, args);
                break;

            case "resetstats":
                if (!sender.hasPermission("polarbounty.admin")) {
                    sender.sendMessage(PolarSMP.miniMessage().deserialize(configManager.getMessage("errors.no-permission")));
                    return true;
                }
                handleResetStats(sender, args);
                break;

            case "reload":
                if (!sender.hasPermission("polarbounty.admin")) {
                    sender.sendMessage(PolarSMP.miniMessage().deserialize(configManager.getMessage("errors.no-permission")));
                    return true;
                }
                handleReload(sender);
                break;

            default:
                sender.sendMessage(PolarSMP.miniMessage().deserialize(
                        configManager.getMessage("errors.usage").replace("<usage>", "/polarbounty help")));
                break;
        }

        return true;
    }

    private void sendHelp(final CommandSender sender) {
        sender.sendMessage(PolarSMP.miniMessage().deserialize("<gradient:#FFD700:#FFA500>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>"));
        sender.sendMessage(PolarSMP.miniMessage().deserialize("<bold><gradient:#FFD700:#FFA500>       💀 POLARBOUNTY COMMAND HELP 💀</gradient></bold>"));
        sender.sendMessage(PolarSMP.miniMessage().deserialize(""));

        sendHelpLine(sender, "/polarbounty profile [player]", "Open yours or another player's profile stats menu");
        sendHelpLine(sender, "/polarbounty balance [player]", "View current coins and active bounty");
        sendHelpLine(sender, "/polarbounty rewards", "Open the milestone achievements rewards menu");
        sendHelpLine(sender, "/polarbounty shop", "Open the coin equipment shop");
        sendHelpLine(sender, "/polarbounty top", "Open the bounty leaderboards");

        if (sender.hasPermission("polarbounty.admin")) {
            sender.sendMessage(PolarSMP.miniMessage().deserialize("<dark_gray>Admin Commands:</dark_gray>"));
            sendHelpLine(sender, "/polarbounty give <player> <amount>", "Give coins to a player");
            sendHelpLine(sender, "/polarbounty take <player> <amount>", "Take coins from a player");
            sendHelpLine(sender, "/polarbounty setbounty <player> <amount>", "Set a player's active bounty");
            sendHelpLine(sender, "/polarbounty resetstats <player>", "Wipe all stats for a player");
            sendHelpLine(sender, "/polarbounty reload", "Reload the configurations");
        }

        sender.sendMessage(PolarSMP.miniMessage().deserialize("<gradient:#FFD700:#FFA500>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>"));
    }

    private void sendHelpLine(final CommandSender sender, final String cmd, final String description) {
        Component message = PolarSMP.miniMessage().deserialize("<gold>⚡ " + cmd + "</gold> <gray>-</gray> <white>" + description + "</white>")
                .clickEvent(ClickEvent.suggestCommand(cmd))
                .hoverEvent(HoverEvent.showText(PolarSMP.miniMessage().deserialize("<yellow>Click to suggest: " + cmd + "</yellow>")));
        sender.sendMessage(message);
    }

    private void handleProfile(final Player player, final String[] args) {
        if (args.length == 1) {
            guiManager.openProfileGui(player, player);
        } else {
            String targetName = args[1];
            Player target = Bukkit.getPlayer(targetName);
            if (target == null) {
                player.sendMessage(PolarSMP.miniMessage().deserialize(
                        configManager.getMessage("errors.player-not-found").replace("<player>", targetName)));
                return;
            }
            guiManager.openProfileGui(player, target);
        }
    }

    private void handleBalance(final CommandSender sender, final String[] args) {
        if (args.length == 1) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(PolarSMP.miniMessage().deserialize(configManager.getMessage("errors.player-only")));
                return;
            }
            BountyPlayer p = cache.getPlayer(player.getUniqueId());
            if (p == null) return;
            sender.sendMessage(PolarSMP.miniMessage().deserialize(
                    configManager.getMessage("bounty.balance-self")
                            .replace("<coins>", FormatUtil.formatNumber(p.getCoins()))
                            .replace("<bounty>", FormatUtil.formatNumber(p.getBounty()))));
        } else {
            String targetName = args[1];
            Player target = Bukkit.getPlayer(targetName);
            if (target == null) {
                sender.sendMessage(PolarSMP.miniMessage().deserialize(
                        configManager.getMessage("errors.player-not-found").replace("<player>", targetName)));
                return;
            }
            BountyPlayer p = cache.getPlayer(target.getUniqueId());
            if (p == null) return;
            sender.sendMessage(PolarSMP.miniMessage().deserialize(
                    configManager.getMessage("bounty.balance-other")
                            .replace("<player>", target.getName())
                            .replace("<coins>", FormatUtil.formatNumber(p.getCoins()))
                            .replace("<bounty>", FormatUtil.formatNumber(p.getBounty()))));
        }
    }

    private void handleGive(final CommandSender sender, final String[] args) {
        if (args.length < 3) {
            sender.sendMessage(PolarSMP.miniMessage().deserialize(
                    configManager.getMessage("errors.usage").replace("<usage>", "/polarbounty give <player> <amount>")));
            return;
        }

        String targetName = args[1];
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            sender.sendMessage(PolarSMP.miniMessage().deserialize(
                    configManager.getMessage("errors.player-not-found").replace("<player>", targetName)));
            return;
        }

        long amount;
        try {
            amount = Long.parseLong(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(PolarSMP.miniMessage().deserialize(configManager.getMessage("errors.invalid-amount")));
            return;
        }

        if (amount <= 0) {
            sender.sendMessage(PolarSMP.miniMessage().deserialize(configManager.getMessage("errors.invalid-amount")));
            return;
        }

        bountyManager.adminGiveCoins(target, amount);
        sender.sendMessage(PolarSMP.miniMessage().deserialize(
                configManager.getMessage("bounty.admin-give")
                        .replace("<amount>", FormatUtil.formatNumber(amount))
                        .replace("<player>", target.getName())));

        // Update scoreboard
        plugin.getScoreboardManager().updateScoreboard(target);
    }

    private void handleTake(final CommandSender sender, final String[] args) {
        if (args.length < 3) {
            sender.sendMessage(PolarSMP.miniMessage().deserialize(
                    configManager.getMessage("errors.usage").replace("<usage>", "/polarbounty take <player> <amount>")));
            return;
        }

        String targetName = args[1];
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            sender.sendMessage(PolarSMP.miniMessage().deserialize(
                    configManager.getMessage("errors.player-not-found").replace("<player>", targetName)));
            return;
        }

        long amount;
        try {
            amount = Long.parseLong(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(PolarSMP.miniMessage().deserialize(configManager.getMessage("errors.invalid-amount")));
            return;
        }

        if (amount <= 0) {
            sender.sendMessage(PolarSMP.miniMessage().deserialize(configManager.getMessage("errors.invalid-amount")));
            return;
        }

        bountyManager.adminTakeCoins(target, amount);
        sender.sendMessage(PolarSMP.miniMessage().deserialize(
                configManager.getMessage("bounty.admin-take")
                        .replace("<amount>", FormatUtil.formatNumber(amount))
                        .replace("<player>", target.getName())));

        // Update scoreboard
        plugin.getScoreboardManager().updateScoreboard(target);
    }

    private void handleSetBounty(final CommandSender sender, final String[] args) {
        if (args.length < 3) {
            sender.sendMessage(PolarSMP.miniMessage().deserialize(
                    configManager.getMessage("errors.usage").replace("<usage>", "/polarbounty setbounty <player> <amount>")));
            return;
        }

        String targetName = args[1];
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            sender.sendMessage(PolarSMP.miniMessage().deserialize(
                    configManager.getMessage("errors.player-not-found").replace("<player>", targetName)));
            return;
        }

        long amount;
        try {
            amount = Long.parseLong(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(PolarSMP.miniMessage().deserialize(configManager.getMessage("errors.invalid-amount")));
            return;
        }

        if (amount < 0) {
            sender.sendMessage(PolarSMP.miniMessage().deserialize(configManager.getMessage("errors.invalid-amount")));
            return;
        }

        bountyManager.adminSetBounty(target, amount);
        sender.sendMessage(PolarSMP.miniMessage().deserialize(
                configManager.getMessage("bounty.admin-set-bounty")
                        .replace("<amount>", FormatUtil.formatNumber(amount))
                        .replace("<player>", target.getName())));

        // Update scoreboard
        plugin.getScoreboardManager().updateScoreboard(target);
    }

    private void handleResetStats(final CommandSender sender, final String[] args) {
        if (args.length < 2) {
            sender.sendMessage(PolarSMP.miniMessage().deserialize(
                    configManager.getMessage("errors.usage").replace("<usage>", "/polarbounty resetstats <player>")));
            return;
        }

        String targetName = args[1];
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            sender.sendMessage(PolarSMP.miniMessage().deserialize(
                    configManager.getMessage("errors.player-not-found").replace("<player>", targetName)));
            return;
        }

        UUID senderUuid = (sender instanceof Player p) ? p.getUniqueId() : UUID.nameUUIDFromBytes("console".getBytes());
        Long lastClick = pendingConfirm.get(senderUuid);
        long now = System.currentTimeMillis();

        if (lastClick == null || (now - lastClick > 10000)) {
            // First click - ask for confirmation
            pendingConfirm.put(senderUuid, now);
            sender.sendMessage(PolarSMP.miniMessage().deserialize(
                    configManager.getMessage("bounty.reset-confirm").replace("<player>", target.getName())));
        } else {
            // Second click within 10 seconds - execute reset
            pendingConfirm.remove(senderUuid);
            bountyManager.adminResetStats(target);
            sender.sendMessage(PolarSMP.miniMessage().deserialize(
                    configManager.getMessage("bounty.reset-done").replace("<player>", target.getName())));
            plugin.getScoreboardManager().updateScoreboard(target);
        }
    }

    private void handleReload(final CommandSender sender) {
        try {
            configManager.reloadAll();
            plugin.getScoreboardManager().reload();
            sender.sendMessage(PolarSMP.miniMessage().deserialize(configManager.getMessage("reload.success")));
        } catch (Exception e) {
            sender.sendMessage(PolarSMP.miniMessage().deserialize(configManager.getMessage("reload.failure")));
            plugin.getLogger().severe("Failed to reload configs: " + e.getMessage());
        }
    }

    private void handleContract(final Player player, final String[] args) {
        if (args.length < 3) {
            player.sendMessage(PolarSMP.miniMessage().deserialize(
                    configManager.getMessage("errors.usage").replace("<usage>", "/polarbounty contract <player> <amount>")));
            return;
        }

        String targetName = args[1];
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            player.sendMessage(PolarSMP.miniMessage().deserialize(
                    configManager.getMessage("errors.player-not-found").replace("<player>", targetName)));
            return;
        }

        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(PolarSMP.miniMessage().deserialize("<red>✖ You cannot place a bounty contract on yourself!</red>"));
            return;
        }

        long amount;
        try {
            amount = Long.parseLong(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage(PolarSMP.miniMessage().deserialize(configManager.getMessage("errors.invalid-amount")));
            return;
        }

        if (amount <= 0) {
            player.sendMessage(PolarSMP.miniMessage().deserialize(configManager.getMessage("errors.invalid-amount")));
            return;
        }

        boolean success = bountyManager.placeContract(player, target, amount);
        if (!success) {
            player.sendMessage(PolarSMP.miniMessage().deserialize("<red>✖ Insufficient coins! Placing this contract requires coins + tax fee.</red>"));
            return;
        }

        // Update scoreboards for both
        plugin.getScoreboardManager().updateScoreboard(player);
        plugin.getScoreboardManager().updateScoreboard(target);
    }

    @Override
    public List<String> onTabComplete(@NotNull final CommandSender sender, @NotNull final Command command,
                                      @NotNull final String alias, @NotNull final String[] args) {
        if (args.length == 1) {
            List<String> list = new ArrayList<>(List.of("help", "profile", "rewards", "shop", "top", "balance", "contract"));
            if (sender.hasPermission("polarbounty.admin")) {
                list.addAll(List.of("give", "take", "setbounty", "resetstats", "reload"));
            }
            return list.stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("profile") || sub.equals("balance") || sub.equals("give")
                    || sub.equals("take") || sub.equals("setbounty") || sub.equals("resetstats")
                    || sub.equals("contract")) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                        .toList();
            }
        }

        if (args.length == 3) {
            String sub = args[0].toLowerCase();
            if (sub.equals("contract") || sub.equals("give") || sub.equals("take") || sub.equals("setbounty")) {
                return List.of("100", "500", "1000", "5000").stream()
                        .filter(s -> s.startsWith(args[2]))
                        .toList();
            }
        }

        return Collections.emptyList();
    }
}
