package com.polarsmp.core;

import com.polarsmp.PolarSMP;
import com.polarsmp.bounty.BountyPlayer;
import com.polarsmp.rank.RankPlayer;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * MySQL implementation of {@link DataStore}.
 *
 * <p>Uses MySQL-specific UPSERT syntax (INSERT ... ON DUPLICATE KEY UPDATE)
 * instead of SQLite's ON CONFLICT syntax.</p>
 *
 * @author ANSH9BOSS
 * @version 1.0.0
 */
public final class MySQLDataStore implements DataStore {

    private final PolarSMP plugin;
    private final HikariDataSource dataSource;

    /**
     * Constructs a new MySQLDataStore.
     *
     * @param plugin     the PolarSMP plugin instance
     * @param dataSource the HikariCP connection pool
     */
    public MySQLDataStore(final PolarSMP plugin, final HikariDataSource dataSource) {
        this.plugin = plugin;
        this.dataSource = dataSource;
    }

    /**
     * Creates all required database tables if they don't exist.
     */
    public void createTables() {
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS players (
                    uuid VARCHAR(36) PRIMARY KEY,
                    name VARCHAR(16) NOT NULL,
                    coins BIGINT DEFAULT 0,
                    bounty BIGINT DEFAULT 0,
                    kill_streak INT DEFAULT 0,
                    highest_streak INT DEFAULT 0,
                    total_kills INT DEFAULT 0,
                    total_deaths INT DEFAULT 0,
                    last_seen BIGINT DEFAULT 0
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS ranks (
                    uuid VARCHAR(36) PRIMARY KEY,
                    rank_number INT NOT NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS rewards_claimed (
                    uuid VARCHAR(36) NOT NULL,
                    reward_id VARCHAR(64) NOT NULL,
                    PRIMARY KEY (uuid, reward_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS kill_log (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    killer_uuid VARCHAR(36) NOT NULL,
                    victim_uuid VARCHAR(36) NOT NULL,
                    timestamp BIGINT NOT NULL,
                    INDEX idx_kill_log_lookup (killer_uuid, victim_uuid, timestamp)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

            plugin.getLogger().info("MySQL tables verified/created.");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create MySQL tables!", e);
        }
    }

    @Override
    public CompletableFuture<BountyPlayer> loadPlayer(final UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT * FROM players WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return mapPlayer(rs);
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load player " + uuid, e);
            }
            return null;
        });
    }

    @Override
    public CompletableFuture<Void> savePlayer(final BountyPlayer player) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("""
                     INSERT INTO players (uuid, name, coins, bounty, kill_streak, highest_streak,
                         total_kills, total_deaths, last_seen)
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                     ON DUPLICATE KEY UPDATE
                         name = VALUES(name),
                         coins = VALUES(coins),
                         bounty = VALUES(bounty),
                         kill_streak = VALUES(kill_streak),
                         highest_streak = VALUES(highest_streak),
                         total_kills = VALUES(total_kills),
                         total_deaths = VALUES(total_deaths),
                         last_seen = VALUES(last_seen)
                     """)) {
                ps.setString(1, player.getUuid().toString());
                ps.setString(2, player.getName());
                ps.setLong(3, player.getCoins());
                ps.setLong(4, player.getBounty());
                ps.setInt(5, player.getKillStreak());
                ps.setInt(6, player.getHighestStreak());
                ps.setInt(7, player.getTotalKills());
                ps.setInt(8, player.getTotalDeaths());
                ps.setLong(9, System.currentTimeMillis());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save player " + player.getUuid(), e);
            }
        });
    }

    @Override
    public CompletableFuture<Integer> loadRank(final UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT rank_number FROM ranks WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt("rank_number");
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load rank for " + uuid, e);
            }
            return null;
        });
    }

    @Override
    public CompletableFuture<Void> saveRank(final UUID uuid, final Integer rankNumber) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection()) {
                if (rankNumber == null) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "DELETE FROM ranks WHERE uuid = ?")) {
                        ps.setString(1, uuid.toString());
                        ps.executeUpdate();
                    }
                } else {
                    try (PreparedStatement ps = conn.prepareStatement("""
                            INSERT INTO ranks (uuid, rank_number) VALUES (?, ?)
                            ON DUPLICATE KEY UPDATE rank_number = VALUES(rank_number)
                            """)) {
                        ps.setString(1, uuid.toString());
                        ps.setInt(2, rankNumber);
                        ps.executeUpdate();
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save rank for " + uuid, e);
            }
        });
    }

    @Override
    public CompletableFuture<List<RankPlayer>> loadAllRanks() {
        return CompletableFuture.supplyAsync(() -> {
            List<RankPlayer> list = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT r.uuid, r.rank_number, p.name FROM ranks r " +
                         "LEFT JOIN players p ON r.uuid = p.uuid");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    int rank = rs.getInt("rank_number");
                    String name = rs.getString("name");
                    list.add(new RankPlayer(uuid, rank, name, System.currentTimeMillis()));
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load all ranks!", e);
            }
            return list;
        });
    }

    @Override
    public CompletableFuture<Void> saveRewardClaimed(final UUID uuid, final String rewardId) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT IGNORE INTO rewards_claimed (uuid, reward_id) VALUES (?, ?)")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, rewardId);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save reward claim!", e);
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> hasRewardClaimed(final UUID uuid, final String rewardId) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT 1 FROM rewards_claimed WHERE uuid = ? AND reward_id = ?")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, rewardId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to check reward claim!", e);
            }
            return false;
        });
    }

    @Override
    public CompletableFuture<Void> logKill(final UUID killerUuid, final UUID victimUuid, final long timestamp) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO kill_log (killer_uuid, victim_uuid, timestamp) VALUES (?, ?, ?)")) {
                ps.setString(1, killerUuid.toString());
                ps.setString(2, victimUuid.toString());
                ps.setLong(3, timestamp);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to log kill!", e);
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> wasRecentlyKilled(final UUID killerUuid, final UUID victimUuid,
                                                        final long cutoffTimestamp) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT 1 FROM kill_log WHERE killer_uuid = ? AND victim_uuid = ? " +
                         "AND timestamp >= ? LIMIT 1")) {
                ps.setString(1, killerUuid.toString());
                ps.setString(2, victimUuid.toString());
                ps.setLong(3, cutoffTimestamp);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to check anti-farm cooldown!", e);
            }
            return false;
        });
    }

    @Override
    public CompletableFuture<List<BountyPlayer>> getTopByCoins(final int limit) {
        return topQuery("ORDER BY coins DESC", limit);
    }

    @Override
    public CompletableFuture<List<BountyPlayer>> getTopByBounty(final int limit) {
        return topQuery("ORDER BY bounty DESC", limit);
    }

    @Override
    public CompletableFuture<List<BountyPlayer>> getTopByStreak(final int limit) {
        return topQuery("ORDER BY highest_streak DESC", limit);
    }

    @Override
    public CompletableFuture<List<BountyPlayer>> getTopByKills(final int limit) {
        return topQuery("ORDER BY total_kills DESC", limit);
    }

    private CompletableFuture<List<BountyPlayer>> topQuery(final String orderClause, final int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<BountyPlayer> list = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT * FROM players " + orderClause + " LIMIT ?")) {
                ps.setInt(1, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) list.add(mapPlayer(rs));
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to query leaderboard!", e);
            }
            return list;
        });
    }

    private BountyPlayer mapPlayer(final ResultSet rs) throws SQLException {
        BountyPlayer p = new BountyPlayer(
                UUID.fromString(rs.getString("uuid")),
                rs.getString("name")
        );
        p.setCoins(rs.getLong("coins"));
        p.setBounty(rs.getLong("bounty"));
        p.setKillStreak(rs.getInt("kill_streak"));
        p.setHighestStreak(rs.getInt("highest_streak"));
        p.setTotalKills(rs.getInt("total_kills"));
        p.setTotalDeaths(rs.getInt("total_deaths"));
        return p;
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
