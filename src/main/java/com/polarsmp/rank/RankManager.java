package com.polarsmp.rank;

import com.polarsmp.PolarSMP;
import com.polarsmp.core.ConfigManager;
import com.polarsmp.core.DataStore;
import com.polarsmp.core.PlayerDataCache;
import com.polarsmp.integrations.LuckPermsHook;
import com.polarsmp.util.EffectUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Central manager for the PolarRank system.
 *
 * <p>Maintains the live rank map (rankNumber → UUID), scoreboard teams
 * for nametag prefixes, and rank perk definitions loaded from ranks.yml.
 * All rank transfer logic is atomic and synchronised to prevent race conditions.</p>
 *
 * <p>Thread safety: the {@code rankMap} and {@code rankHolders} maps use
 * {@link ConcurrentHashMap}. The transfer operation is wrapped in a
 * {@code synchronized} block on {@code rankLock} so no two transfers
 * can interleave.</p>
 *
 * @author ANSH9BOSS
 * @version 1.0.0
 */
public final class RankManager {

    /** Total number of exclusive ranks on the server. */
    public static final int MAX_RANKS = 10;

    private final PolarSMP plugin;
    private final ConfigManager configManager;
    private final DataStore dataStore;
    private final PlayerDataCache playerDataCache;
    private final LuckPermsHook luckPermsHook;

    /** Maps rank number (1–10) → UUID of current holder. */
    private final ConcurrentHashMap<Integer, UUID> rankMap = new ConcurrentHashMap<>();

    /** Reverse map: UUID → rank number for O(1) lookup. */
    private final ConcurrentHashMap<UUID, Integer> rankHolders = new ConcurrentHashMap<>();

    /** Maps rank number (1–10) → timestamp when last assigned. */
    private final ConcurrentHashMap<Integer, Long> rankTimestamps = new ConcurrentHashMap<>();

    /** Rank perk definitions loaded from ranks.yml. */
    private final Map<Integer, RankPerk> rankPerks = new HashMap<>();

    /** Lock object for atomic rank transfers. */
    private final Object rankLock = new Object();

    /** Whether a season is currently active. */
    private volatile boolean seasonActive = false;
    private volatile long seasonStartTime = 0L;

    /** Scoreboard used exclusively for team nametag prefixes. */
    private Scoreboard nametabScoreboard;

    /**
     * Constructs a new RankManager.
     *
     * @param plugin          the PolarSMP plugin instance
     * @param configManager   the configuration manager
     * @param dataStore       the data store for persistence
     * @param playerDataCache the in-memory player cache
     * @param luckPermsHook   the optional LuckPerms hook
     */
    public RankManager(final PolarSMP plugin, final ConfigManager configManager,
                       final DataStore dataStore, final PlayerDataCache playerDataCache,
                       final LuckPermsHook luckPermsHook) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.dataStore = dataStore;
        this.playerDataCache = playerDataCache;
        this.luckPermsHook = luckPermsHook;

        loadRankPerks();
        setupNametabTeams();
        loadRanksFromDatabase();
    }

    // ─── Initialisation ───────────────────────────────────────────

    /**
     * Loads all rank perk definitions from ranks.yml.
     */
    private void loadRankPerks() {
        rankPerks.clear();
        ConfigurationSection ranksSection = configManager.getRanksConfig().getConfigurationSection("ranks");
        if (ranksSection == null) {
            plugin.getLogger().warning("ranks.yml is missing the 'ranks' section!");
            return;
        }

        for (String key : ranksSection.getKeys(false)) {
            try {
                int rankNum = Integer.parseInt(key);
                ConfigurationSection rs = ranksSection.getConfigurationSection(key);
                if (rs == null) continue;

                int extraHearts = rs.getInt("extra-hearts", 0);
                String color = rs.getString("color", "<gold>");
                String prefix = rs.getString("prefix-format", "<gold>[#" + rankNum + "]</gold>");
                int cmdData = rs.getInt("resource-pack-custom-model-data", 0);

                List<RankPerk.PerkEffect> effects = new ArrayList<>();
                List<Map<?, ?>> effectList = rs.getMapList("effects");
                for (Map<?, ?> effectMap : effectList) {
                    String effectName = (String) effectMap.get("effect");
                    int amplifier = effectMap.containsKey("amplifier")
                            ? Integer.parseInt(effectMap.get("amplifier").toString()) : 0;
                    try {
                        var pet = org.bukkit.potion.PotionEffectType.getByName(effectName);
                        if (pet != null) {
                            effects.add(new RankPerk.PerkEffect(pet, amplifier));
                        } else {
                            plugin.getLogger().warning("Unknown effect '" + effectName + "' for rank " + rankNum);
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("Invalid effect '" + effectName + "' for rank " + rankNum);
                    }
                }

                rankPerks.put(rankNum, new RankPerk(rankNum, extraHearts, effects, color, prefix, cmdData));
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("Invalid rank key '" + key + "' in ranks.yml");
            }
        }
        plugin.getLogger().info("Loaded " + rankPerks.size() + " rank perk definitions.");
    }

    /**
     * Creates the 11 scoreboard teams used for nametag prefix display.
     * Teams are: PolarRank_1 through PolarRank_10 and PolarRank_U (unranked).
     */
    private void setupNametabTeams() {
        nametabScoreboard = Objects.requireNonNull(
                Bukkit.getScoreboardManager()).getMainScoreboard();

        // Create/reset ranked teams
        for (int i = 1; i <= MAX_RANKS; i++) {
            String teamName = "PolarRank_" + i;
            Team existing = nametabScoreboard.getTeam(teamName);
            if (existing != null) existing.unregister();

            Team team = nametabScoreboard.registerNewTeam(teamName);
            RankPerk perk = rankPerks.get(i);
            String prefixMM = (perk != null) ? perk.getPrefixFormat() : "<gold>[#" + i + "]</gold>";
            Component prefixComponent = MiniMessage.miniMessage().deserialize(prefixMM + " ");
            team.prefix(prefixComponent);
            team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);
        }

        // Create/reset unranked team
        Team existingU = nametabScoreboard.getTeam("PolarRank_U");
        if (existingU != null) existingU.unregister();
        Team unrankedTeam = nametabScoreboard.registerNewTeam("PolarRank_U");
        unrankedTeam.prefix(MiniMessage.miniMessage().deserialize("<gray>[Unranked] </gray>"));
        unrankedTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);

        plugin.getLogger().info("Scoreboard nametag teams registered.");
    }

    /**
     * Loads all rank assignments from the database on plugin startup.
     */
    private void loadRanksFromDatabase() {
        dataStore.loadAllRanks().thenAccept(ranks -> {
            // Run on main thread to be safe with Bukkit state
            Bukkit.getScheduler().runTask(plugin, () -> {
                rankMap.clear();
                rankHolders.clear();
                for (RankPlayer rp : ranks) {
                    rankMap.put(rp.rankNumber(), rp.uuid());
                    rankHolders.put(rp.uuid(), rp.rankNumber());
                    rankTimestamps.put(rp.rankNumber(), rp.timestampGained());
                }
                // Season is active if any ranks are loaded
                if (!rankMap.isEmpty()) {
                    seasonActive = true;
                    plugin.getLogger().info("Loaded " + ranks.size() + " rank assignments. Season is active.");
                }
                // Apply nametag teams for all online players
                for (Player p : Bukkit.getOnlinePlayers()) {
                    assignTeam(p);
                    applyPerks(p);
                }
            });
        }).exceptionally(ex -> {
            plugin.getLogger().log(Level.SEVERE, "Failed to load ranks from database!", ex);
            return null;
        });
    }

    // ─── Season management ────────────────────────────────────────

    /**
     * Starts a new season by randomly assigning ranks to online players.
     * If fewer than 10 players are online, only available players get ranks.
     *
     * @return the number of ranks assigned
     */
    public int startSeason() {
        synchronized (rankLock) {
            if (seasonActive) return -1; // Already active

            List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
            Collections.shuffle(online);

            seasonActive = true;
            seasonStartTime = System.currentTimeMillis();

            int assigned = 0;
            for (int rank = 1; rank <= MAX_RANKS && assigned < online.size(); rank++) {
                Player p = online.get(assigned);
                rankMap.put(rank, p.getUniqueId());
                rankHolders.put(p.getUniqueId(), rank);
                rankTimestamps.put(rank, seasonStartTime);
                dataStore.saveRank(p.getUniqueId(), rank);
                assignTeam(p);
                applyPerks(p);
                assigned++;
            }
            return assigned;
        }
    }

    /**
     * Ends the current season, stripping all ranks and perks.
     */
    public void stopSeason() {
        synchronized (rankLock) {
            // Strip all ranks
            for (Map.Entry<UUID, Integer> entry : new HashMap<>(rankHolders).entrySet()) {
                UUID uuid = entry.getKey();
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) {
                    EffectUtil.removeRankPerks(p);
                    assignUnrankedTeam(p);
                }
                dataStore.saveRank(uuid, null);
            }
            rankMap.clear();
            rankHolders.clear();
            rankTimestamps.clear();
            seasonActive = false;
            seasonStartTime = 0L;
        }
    }

    // ─── Rank transfer ────────────────────────────────────────────

    /**
     * Processes an atomic rank transfer between killer and victim after a PvP kill.
     *
     * <p>Logic:</p>
     * <ul>
     *   <li>Unranked kills ranked → killer gets rank, victim loses rank</li>
     *   <li>Ranked kills ranked → full swap</li>
     *   <li>Ranked kills unranked → no rank change, only coins/bounty logic</li>
     * </ul>
     *
     * <p>Must be called on the main thread.</p>
     *
     * @param killer the killing player
     * @param victim the killed player
     * @return the RankTransferResult describing what happened
     */
    public RankTransferResult processKill(final Player killer, final Player victim) {
        synchronized (rankLock) {
            Integer killerRank = rankHolders.get(killer.getUniqueId());
            Integer victimRank = rankHolders.get(victim.getUniqueId());

            if (victimRank == null) {
                // Ranked or unranked kills unranked – no rank transfer
                return new RankTransferResult(killerRank, null, null, false);
            }

            if (killerRank == null) {
                // Unranked kills ranked – killer takes the rank
                transferRank(victim, null, killer, victimRank);
                return new RankTransferResult(victimRank, victimRank, null, true);
            } else {
                // Ranked kills ranked – full swap
                transferRank(victim, victimRank, killer, killerRank);
                int old = killerRank;
                return new RankTransferResult(victimRank, victimRank, old, true);
            }
        }
    }

    /**
     * Performs the actual rank data swap between two players.
     * Must be called while holding {@code rankLock}.
     *
     * @param loser       player losing their rank
     * @param loserNewRank the rank number the loser gets (null = unranked)
     * @param gainer      player gaining a rank
     * @param gainerNewRank the rank number the gainer gets
     */
    private void transferRank(final Player loser, final Integer loserNewRank,
                              final Player gainer, final int gainerNewRank) {
        long now = System.currentTimeMillis();

        // ── Update in-memory maps ─────────────────────────────────
        if (loserNewRank != null) {
            rankMap.put(loserNewRank, loser.getUniqueId());
            rankHolders.put(loser.getUniqueId(), loserNewRank);
            rankTimestamps.put(loserNewRank, now);
        } else {
            rankMap.remove(gainerNewRank);
            rankHolders.remove(loser.getUniqueId());
        }

        rankMap.put(gainerNewRank, gainer.getUniqueId());
        rankHolders.put(gainer.getUniqueId(), gainerNewRank);
        rankTimestamps.put(gainerNewRank, now);

        // ── Apply perks ───────────────────────────────────────────
        EffectUtil.removeRankPerks(loser);
        EffectUtil.removeRankPerks(gainer);

        if (loserNewRank != null) {
            applyPerksForRank(loser, loserNewRank);
        }
        applyPerksForRank(gainer, gainerNewRank);

        // ── Update nametag teams ──────────────────────────────────
        assignTeam(loser);
        assignTeam(gainer);

        // ── Persist asynchronously ────────────────────────────────
        dataStore.saveRank(loser.getUniqueId(), loserNewRank);
        dataStore.saveRank(gainer.getUniqueId(), gainerNewRank);

        // ── LuckPerms rank 1 sync ─────────────────────────────────
        if (gainerNewRank == 1) {
            luckPermsHook.addRank1Permission(gainer.getUniqueId());
        }
        // If loser had rank 1 and now doesn't, revoke
        if (loserNewRank == null || loserNewRank != 1) {
            Integer prevLoserRank = loserNewRank != null ? loserNewRank : null;
            if (prevLoserRank == null || prevLoserRank != 1) {
                // Check if loser previously had rank 1
                luckPermsHook.removeRank1Permission(loser.getUniqueId());
            }
        }
    }

    // ─── Admin operations ─────────────────────────────────────────

    /**
     * Manually assigns a specific rank to a player.
     * If the rank is currently held, the current holder is displaced to unranked.
     *
     * @param target     the player to assign the rank to
     * @param rankNumber the rank number (1–10)
     */
    public void adminSetRank(final Player target, final int rankNumber) {
        synchronized (rankLock) {
            long now = System.currentTimeMillis();

            // Displace current holder if any
            UUID currentHolder = rankMap.get(rankNumber);
            if (currentHolder != null && !currentHolder.equals(target.getUniqueId())) {
                rankHolders.remove(currentHolder);
                dataStore.saveRank(currentHolder, null);
                Player currentOnline = Bukkit.getPlayer(currentHolder);
                if (currentOnline != null) {
                    EffectUtil.removeRankPerks(currentOnline);
                    assignUnrankedTeam(currentOnline);
                }
            }

            // Remove target's old rank if any
            Integer oldRank = rankHolders.get(target.getUniqueId());
            if (oldRank != null) {
                rankMap.remove(oldRank);
            }

            // Assign new rank
            rankMap.put(rankNumber, target.getUniqueId());
            rankHolders.put(target.getUniqueId(), rankNumber);
            rankTimestamps.put(rankNumber, now);

            EffectUtil.removeRankPerks(target);
            applyPerksForRank(target, rankNumber);
            assignTeam(target);

            dataStore.saveRank(target.getUniqueId(), rankNumber);

            if (rankNumber == 1) luckPermsHook.addRank1Permission(target.getUniqueId());
        }
    }

    /**
     * Strips the rank from a player, making them unranked.
     *
     * @param target the player to strip the rank from
     * @return the rank number that was removed, or null if they were already unranked
     */
    public Integer adminClearRank(final Player target) {
        synchronized (rankLock) {
            Integer rank = rankHolders.remove(target.getUniqueId());
            if (rank != null) {
                rankMap.remove(rank);
                rankTimestamps.remove(rank);
                EffectUtil.removeRankPerks(target);
                assignUnrankedTeam(target);
                dataStore.saveRank(target.getUniqueId(), null);
                if (rank == 1) luckPermsHook.removeRank1Permission(target.getUniqueId());
            }
            return rank;
        }
    }

    // ─── Perk application ─────────────────────────────────────────

    /**
     * Applies rank perks to a player based on their current rank.
     *
     * @param player the player to apply perks to
     */
    public void applyPerks(final Player player) {
        Integer rank = rankHolders.get(player.getUniqueId());
        if (rank != null) {
            applyPerksForRank(player, rank);
        }
    }

    /**
     * Applies rank perks for a specific rank number to a player.
     *
     * @param player     the player to apply perks to
     * @param rankNumber the rank number to apply perks for
     */
    public void applyPerksForRank(final Player player, final int rankNumber) {
        RankPerk perk = rankPerks.get(rankNumber);
        if (perk == null) return;
        EffectUtil.applyRankPerks(player, perk);
    }

    // ─── Team management ─────────────────────────────────────────

    /**
     * Assigns a player to their correct scoreboard nametag team.
     *
     * @param player the player to assign
     */
    public void assignTeam(final Player player) {
        Integer rank = rankHolders.get(player.getUniqueId());
        if (rank != null) {
            Team team = nametabScoreboard.getTeam("PolarRank_" + rank);
            if (team != null) {
                // Remove from all other teams first
                removeFromAllTeams(player);
                team.addPlayer(player);
            }
        } else {
            assignUnrankedTeam(player);
        }
    }

    /**
     * Assigns a player to the unranked team.
     *
     * @param player the player to assign
     */
    public void assignUnrankedTeam(final Player player) {
        removeFromAllTeams(player);
        Team team = nametabScoreboard.getTeam("PolarRank_U");
        if (team != null) team.addPlayer(player);
    }

    /**
     * Removes a player from all PolarRank teams.
     *
     * @param player the player to remove
     */
    private void removeFromAllTeams(final Player player) {
        for (int i = 1; i <= MAX_RANKS; i++) {
            Team t = nametabScoreboard.getTeam("PolarRank_" + i);
            if (t != null && t.hasPlayer(player)) t.removePlayer(player);
        }
        Team u = nametabScoreboard.getTeam("PolarRank_U");
        if (u != null && u.hasPlayer(player)) u.removePlayer(player);
    }

    // ─── Join/Quit handling ───────────────────────────────────────

    /**
     * Called when a player joins; applies their loaded rank state.
     *
     * @param player the joining player
     */
    public void onPlayerJoin(final Player player) {
        assignTeam(player);
        applyPerks(player);
    }

    /**
     * Called when a player quits; cleans up their rank team membership.
     *
     * @param player the quitting player
     */
    public void onPlayerQuit(final Player player) {
        removeFromAllTeams(player);
    }

    // ─── Persistence ──────────────────────────────────────────────

    /**
     * Saves all current rank assignments to the database synchronously.
     * Used on plugin shutdown.
     *
     * @param dataStore the DataStore to save to
     */
    public void saveAllRanksSync(final DataStore dataStore) {
        rankHolders.forEach((uuid, rank) -> {
            try {
                dataStore.saveRank(uuid, rank).get();
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save rank for " + uuid + " on shutdown", e);
            }
        });
    }

    // ─── Query methods ────────────────────────────────────────────

    /**
     * Returns the rank number for a player, or null if unranked.
     *
     * @param uuid the player's UUID
     * @return the rank number (1–10) or null
     */
    public Integer getRank(final UUID uuid) {
        return rankHolders.get(uuid);
    }

    /**
     * Returns the UUID of the player holding a specific rank, or null if vacant.
     *
     * @param rankNumber the rank number to look up
     * @return the UUID of the holder, or null
     */
    public UUID getRankHolder(final int rankNumber) {
        return rankMap.get(rankNumber);
    }

    /**
     * Returns an unmodifiable snapshot of the current rank map.
     *
     * @return map of rank number → UUID
     */
    public Map<Integer, UUID> getRankMap() {
        return Collections.unmodifiableMap(new HashMap<>(rankMap));
    }

    /**
     * Returns the timestamp when a rank was last assigned.
     *
     * @param rankNumber the rank number
     * @return timestamp in milliseconds, or 0 if not set
     */
    public long getRankTimestamp(final int rankNumber) {
        return rankTimestamps.getOrDefault(rankNumber, 0L);
    }

    /**
     * Returns the perk definition for a specific rank.
     *
     * @param rankNumber the rank number (1–10)
     * @return the RankPerk, or null if not configured
     */
    public RankPerk getPerk(final int rankNumber) {
        return rankPerks.get(rankNumber);
    }

    /**
     * Returns all rank perk definitions.
     *
     * @return unmodifiable map of rank number → RankPerk
     */
    public Map<Integer, RankPerk> getAllPerks() {
        return Collections.unmodifiableMap(rankPerks);
    }

    /** @return true if a season is currently active */
    public boolean isSeasonActive() { return seasonActive; }

    /** @return the timestamp when the current season started, or 0 */
    public long getSeasonStartTime() { return seasonStartTime; }

    /**
     * Result of a rank transfer operation.
     *
     * @param victimOldRank  the rank the victim previously held
     * @param rankTransferred the rank that changed hands (null if no change)
     * @param killerOldRank  the rank the killer previously held (null if unranked)
     * @param transferred    whether any rank transfer occurred
     */
    public record RankTransferResult(Integer victimOldRank, Integer rankTransferred,
                                     Integer killerOldRank, boolean transferred) {}
}
