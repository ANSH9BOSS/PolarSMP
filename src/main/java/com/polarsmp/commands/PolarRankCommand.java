package com.polarsmp.commands;

import com.polarsmp.PolarSMP;
import com.polarsmp.core.ConfigManager;
import com.polarsmp.core.DataStore;
import com.polarsmp.core.PlayerDataCache;
import com.polarsmp.gui.GuiManager;
import com.polarsmp.rank.RankManager;
import com.polarsmp.rank.RankPerk;
import com.polarsmp.util.FormatUtil;
import com.polarsmp.util.MessageUtil;
import com.polarsmp.util.SoundUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Handles all player and admin PolarRank subcommands.
 *
 * @author ANSH9BOSS
 * @version 1.0.0
 */
public final class PolarRankCommand implements CommandExecutor, TabCompleter {

    private final PolarSMP plugin;
    private final ConfigManager configManager;
    private final RankManager rankManager;
    private final PlayerDataCache cache;
    private final GuiManager guiManager;
    private final DataStore dataStore;

    /**
     * Constructs a new PolarRankCommand.
     */
    public PolarRankCommand(final PolarSMP plugin, final ConfigManager configManager,
                            final RankManager rankManager, final PlayerDataCache cache,
                            final GuiManager guiManager, final DataStore dataStore) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.rankManager = rankManager;
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
                if (!sender.hasPermission("polarrank.player")) {
                    sender.sendMessage(PolarSMP.miniMessage().deserialize(configManager.getMessage("errors.no-permission")));
                    return true;
                }
                sendHelp(sender);
                break;

            case "top":
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(PolarSMP.miniMessage().deserialize(configManager.getMessage("errors.player-only")));
                    return true;
                }
                if (!player.hasPermission("polarrank.player")) {
                    player.sendMessage(PolarSMP.miniMessage().deserialize(configManager.getMessage("errors.no-permission")));
                    return true;
                }
                guiManager.openRankLeaderboardGui(player);
                break;

            case "check":
                if (!sender.hasPermission("polarrank.player")) {
                    sender.sendMessage(PolarSMP.miniMessage().deserialize(configManager.getMessage("errors.no-permission")));
                    return true;
                }
                handleCheck(sender, args);
                break;

            case "start":
                if (!sender.hasPermission("polarrank.admin")) {
                    sender.sendMessage(PolarSMP.miniMessage().deserialize(configManager.getMessage("errors.no-permission")));
                    return true;
                }
                handleStart(sender);
                break;

            case "stop":
                if (!sender.hasPermission("polarrank.admin")) {
                    sender.sendMessage(PolarSMP.miniMessage().deserialize(configManager.getMessage("errors.no-permission")));
                    return true;
                }
                handleStop(sender);
                break;

            case "set":
                if (!sender.hasPermission("polarrank.admin")) {
                    sender.sendMessage(PolarSMP.miniMessage().deserialize(configManager.getMessage("errors.no-permission")));
                    return true;
                }
                handleSet(sender, args);
                break;

            case "clear":
                if (!sender.hasPermission("polarrank.admin")) {
                    sender.sendMessage(PolarSMP.miniMessage().deserialize(configManager.getMessage("errors.no-permission")));
                    return true;
                }
                handleClear(sender, args);
                break;

            case "info":
                if (!sender.hasPermission("polarrank.admin")) {
                    sender.sendMessage(PolarSMP.miniMessage().deserialize(configManager.getMessage("errors.no-permission")));
                    return true;
                }
                handleInfo(sender);
                break;

            case "reload":
                if (!sender.hasPermission("polarrank.admin")) {
                    sender.sendMessage(PolarSMP.miniMessage().deserialize(configManager.getMessage("errors.no-permission")));
                    return true;
                }
                handleReload(sender);
                break;

            default:
                sender.sendMessage(PolarSMP.miniMessage().deserialize(
                        configManager.getMessage("errors.usage").replace("<usage>", "/polarrank help")));
                break;
        }

        return true;
    }

    private void sendHelp(final CommandSender sender) {
        sender.sendMessage(PolarSMP.miniMessage().deserialize("<gradient:#FFD700:#FFA500>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>"));
        sender.sendMessage(PolarSMP.miniMessage().deserialize("<bold><gradient:#FFD700:#FFA500>       👑 POLARRANK COMMAND HELP 👑</gradient></bold>"));
        sender.sendMessage(PolarSMP.miniMessage().deserialize(""));

        sendHelpLine(sender, "/polarrank top", "Open the rank leaderboard menu");
        sendHelpLine(sender, "/polarrank check [player]", "View current rank status");

        if (sender.hasPermission("polarrank.admin")) {
            sender.sendMessage(PolarSMP.miniMessage().deserialize("<dark_gray>Admin Commands:</dark_gray>"));
            sendHelpLine(sender, "/polarrank start", "Start a new rank season");
            sendHelpLine(sender, "/polarrank stop", "Stop the current season");
            sendHelpLine(sender, "/polarrank set <player> <rank>", "Set a player's rank manually");
            sendHelpLine(sender, "/polarrank clear <player>", "Make a player unranked");
            sendHelpLine(sender, "/polarrank info", "Show season statistics");
            sendHelpLine(sender, "/polarrank reload", "Hot-reload configurations");
        }

        sender.sendMessage(PolarSMP.miniMessage().deserialize("<gradient:#FFD700:#FFA500>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>"));
    }

    private void sendHelpLine(final CommandSender sender, final String cmd, final String description) {
        Component message = PolarSMP.miniMessage().deserialize("<gold>⚡ " + cmd + "</gold> <gray>-</gray> <white>" + description + "</white>")
                .clickEvent(ClickEvent.suggestCommand(cmd))
                .hoverEvent(HoverEvent.showText(PolarSMP.miniMessage().deserialize("<yellow>Click to run: " + cmd + "</yellow>")));
        sender.sendMessage(message);
    }

    private void handleCheck(final CommandSender sender, final String[] args) {
        if (args.length == 1) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(PolarSMP.miniMessage().deserialize(configManager.getMessage("errors.player-only")));
                return;
            }
            Integer rankNum = rankManager.getRank(player.getUniqueId());
            String rankName = rankNum != null ? "Rank #" + rankNum : configManager.getMessage("rank.check-unranked");
            sender.sendMessage(PolarSMP.miniMessage().deserialize(
                    configManager.getMessage("rank.check-self").replace("<rank>", rankName)));
        } else {
            String targetName = args[1];
            Player target = Bukkit.getPlayer(targetName);
            if (target == null) {
                sender.sendMessage(PolarSMP.miniMessage().deserialize(
                        configManager.getMessage("errors.player-not-found").replace("<player>", targetName)));
                return;
            }
            Integer rankNum = rankManager.getRank(target.getUniqueId());
            String rankName = rankNum != null ? "Rank #" + rankNum : configManager.getMessage("rank.check-unranked");
            sender.sendMessage(PolarSMP.miniMessage().deserialize(
                    configManager.getMessage("rank.check-other").replace("<player>", target.getName()).replace("<rank>", rankName)));
        }
    }

    private void handleStart(final CommandSender sender) {
        int assigned = rankManager.startSeason();
        if (assigned == -1) {
            sender.sendMessage(PolarSMP.miniMessage().deserialize(configManager.getMessage("errors.season-already-active")));
            return;
        }

        String announce = configManager.getMessage("rank.season-started");
        MessageUtil.broadcastMessage(PolarSMP.miniMessage().deserialize(announce));
        if (sender instanceof Player p) {
            SoundUtil.playSound(p, configManager.getSound("season-start"));
        }
        sender.sendMessage(PolarSMP.miniMessage().deserialize("<green>✔ Started season with " + assigned + " rank assignments.</green>"));
    }

    private void handleStop(final CommandSender sender) {
        if (!rankManager.isSeasonActive()) {
            sender.sendMessage(PolarSMP.miniMessage().deserialize(configManager.getMessage("errors.no-season-active")));
            return;
        }

        rankManager.stopSeason();
        String announce = configManager.getMessage("rank.season-ended");
        MessageUtil.broadcastMessage(PolarSMP.miniMessage().deserialize(announce));
        if (sender instanceof Player p) {
            SoundUtil.playSound(p, configManager.getSound("season-end"));
        }
        sender.sendMessage(PolarSMP.miniMessage().deserialize("<green>✔ Ended the season. All ranks wiped.</green>"));
    }

    private void handleSet(final CommandSender sender, final String[] args) {
        if (args.length < 3) {
            sender.sendMessage(PolarSMP.miniMessage().deserialize(
                    configManager.getMessage("errors.usage").replace("<usage>", "/polarrank set <player> <1-10>")));
            return;
        }

        if (!rankManager.isSeasonActive()) {
            sender.sendMessage(PolarSMP.miniMessage().deserialize(configManager.getMessage("errors.no-season-active")));
            return;
        }

        String targetName = args[1];
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            sender.sendMessage(PolarSMP.miniMessage().deserialize(
                    configManager.getMessage("errors.player-not-found").replace("<player>", targetName)));
            return;
        }

        int rankNum;
        try {
            rankNum = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(PolarSMP.miniMessage().deserialize(configManager.getMessage("errors.invalid-rank")));
            return;
        }

        if (rankNum < 1 || rankNum > 10) {
            sender.sendMessage(PolarSMP.miniMessage().deserialize(configManager.getMessage("errors.invalid-rank")));
            return;
        }

        rankManager.adminSetRank(target, rankNum);
        sender.sendMessage(PolarSMP.miniMessage().deserialize(
                configManager.getMessage("rank.admin-set")
                        .replace("<rank>", String.valueOf(rankNum))
                        .replace("<player>", target.getName())));
    }

    private void handleClear(final CommandSender sender, final String[] args) {
        if (args.length < 2) {
            sender.sendMessage(PolarSMP.miniMessage().deserialize(
                    configManager.getMessage("errors.usage").replace("<usage>", "/polarrank clear <player>")));
            return;
        }

        String targetName = args[1];
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            sender.sendMessage(PolarSMP.miniMessage().deserialize(
                    configManager.getMessage("errors.player-not-found").replace("<player>", targetName)));
            return;
        }

        Integer cleared = rankManager.adminClearRank(target);
        if (cleared != null) {
            sender.sendMessage(PolarSMP.miniMessage().deserialize(
                    configManager.getMessage("rank.admin-clear").replace("<player>", target.getName())));
        } else {
            sender.sendMessage(PolarSMP.miniMessage().deserialize("<red>" + target.getName() + " is already Unranked.</red>"));
        }
    }

    private void handleInfo(final CommandSender sender) {
        if (!rankManager.isSeasonActive()) {
            sender.sendMessage(PolarSMP.miniMessage().deserialize(configManager.getMessage("rank.no-season")));
            return;
        }

        long elapsed = System.currentTimeMillis() - rankManager.getSeasonStartTime();
        sender.sendMessage(PolarSMP.miniMessage().deserialize("<gradient:#FFD700:#FFA500>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>"));
        sender.sendMessage(PolarSMP.miniMessage().deserialize("<bold><gold>👑 PolarRank Season Info</bold></gold>"));
        sender.sendMessage(PolarSMP.miniMessage().deserialize("<gray>Elapsed Time: </gray><yellow>" + FormatUtil.formatDuration(elapsed) + "</yellow>"));
        sender.sendMessage(PolarSMP.miniMessage().deserialize("<gray>Rank Assignments:</gray>"));

        Map<Integer, UUID> map = rankManager.getRankMap();
        for (int i = 1; i <= 10; i++) {
            UUID holder = map.get(i);
            String name = holder != null ? Bukkit.getOfflinePlayer(holder).getName() : "<gray>VACANT</gray>";
            sender.sendMessage(PolarSMP.miniMessage().deserialize("  <gold>Rank #" + i + ":</gold> <white>" + name + "</white>"));
        }
        sender.sendMessage(PolarSMP.miniMessage().deserialize("<gradient:#FFD700:#FFA500>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>"));
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

    @Override
    public List<String> onTabComplete(@NotNull final CommandSender sender, @NotNull final Command command,
                                      @NotNull final String alias, @NotNull final String[] args) {
        if (args.length == 1) {
            List<String> list = new ArrayList<>(List.of("help", "top", "check"));
            if (sender.hasPermission("polarrank.admin")) {
                list.addAll(List.of("start", "stop", "set", "clear", "info", "reload"));
            }
            return list.stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("check") || sub.equals("set") || sub.equals("clear")) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                        .toList();
            }
        }

        if (args.length == 3) {
            String sub = args[0].toLowerCase();
            if (sub.equals("set")) {
                return List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10");
            }
        }

        return Collections.emptyList();
    }
}
