package com.polarsmp.integrations;

import com.polarsmp.PolarSMP;
import com.polarsmp.bounty.BountyPlayer;
import com.polarsmp.core.PlayerDataCache;
import com.polarsmp.rank.RankManager;
import com.polarsmp.rank.RankPerk;
import com.polarsmp.util.FormatUtil;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

/**
 * PlaceholderAPI expansion for PolarSMP.
 *
 * <p>Provides placeholders for rank, coins, bounty, and PvP stats.</p>
 *
 * @author ANSH9BOSS
 * @version 1.0.0
 */
public final class PlaceholderAPIHook extends PlaceholderExpansion {

    private final PolarSMP plugin;
    private final PlayerDataCache cache;
    private final RankManager rankManager;

    /**
     * Constructs a new PlaceholderAPIHook.
     *
     * @param plugin      the plugin instance
     * @param cache       the player data cache
     * @param rankManager the rank manager
     */
    public PlaceholderAPIHook(final PolarSMP plugin, final PlayerDataCache cache, final RankManager rankManager) {
        this.plugin = plugin;
        this.cache = cache;
        this.rankManager = rankManager;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "polarsmp";
    }

    @Override
    public @NotNull String getAuthor() {
        return "ANSH9BOSS";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(final OfflinePlayer player, @NotNull final String params) {
        if (player == null) return "";

        BountyPlayer p = cache.getPlayer(player.getUniqueId());
        if (p == null) return "";

        switch (params.toLowerCase()) {
            case "rank":
                Integer r = rankManager.getRank(player.getUniqueId());
                return r != null ? String.valueOf(r) : "Unranked";
            case "rank_prefix":
                Integer rankNum = rankManager.getRank(player.getUniqueId());
                if (rankNum == null) {
                    return LegacyComponentSerializer.legacyAmpersand().serialize(
                            PolarSMP.miniMessage().deserialize(
                                    plugin.getConfigManager().getMessagesConfig().getString("rank.check-unranked", "<gray>Unranked</gray>")
                            )
                    );
                }
                RankPerk perk = rankManager.getPerk(rankNum);
                if (perk == null) return "";
                return LegacyComponentSerializer.legacySection().serialize(
                        PolarSMP.miniMessage().deserialize(perk.getPrefixFormat())
                );
            case "coins":
                return FormatUtil.formatNumber(p.getCoins());
            case "bounty":
                return FormatUtil.formatNumber(p.getBounty());
            case "streak":
                return String.valueOf(p.getKillStreak());
            case "highest_streak":
                return String.valueOf(p.getHighestStreak());
            case "kills":
                return String.valueOf(p.getTotalKills());
            case "deaths":
                return String.valueOf(p.getTotalDeaths());
            case "kd":
                return FormatUtil.formatKD(p.getTotalKills(), p.getTotalDeaths());
            default:
                return null;
        }
    }
}
