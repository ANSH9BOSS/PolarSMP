package com.polarsmp.core;

import com.polarsmp.PolarSMP;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.logging.Level;

/**
 * Creates and manages the HikariCP connection pool.
 *
 * <p>Reads the database type from config.yml and constructs either a
 * {@link SQLiteDataStore} or {@link MySQLDataStore} accordingly.</p>
 *
 * @author ANSH9BOSS
 * @version 1.0.0
 */
public final class DatabaseManager {

    private final PolarSMP plugin;
    private final ConfigManager configManager;
    private HikariDataSource dataSource;
    private DataStore dataStore;

    /**
     * Constructs a new DatabaseManager.
     *
     * @param plugin        the PolarSMP plugin instance
     * @param configManager the config manager for reading database settings
     */
    public DatabaseManager(final PolarSMP plugin, final ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    /**
     * Initialises the connection pool and creates the DataStore implementation.
     *
     * @return the ready-to-use DataStore instance
     * @throws Exception if the connection pool cannot be established
     */
    public DataStore initialize() throws Exception {
        FileConfiguration config = configManager.getMainConfig();
        String type = config.getString("database.type", "sqlite").toLowerCase();

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setPoolName("PolarSMP-HikariPool");

        if (type.equals("mysql")) {
            String host = config.getString("database.mysql.host", "localhost");
            int port = config.getInt("database.mysql.port", 3306);
            String database = config.getString("database.mysql.database", "polarsmp");
            String username = config.getString("database.mysql.username", "root");
            String password = config.getString("database.mysql.password", "");
            int poolSize = config.getInt("database.mysql.pool-size", 10);

            hikariConfig.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database);
            hikariConfig.setUsername(username);
            hikariConfig.setPassword(password);
            hikariConfig.setMaximumPoolSize(poolSize);
            hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");

            // Recommended MySQL settings for Minecraft servers
            hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
            hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
            hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            hikariConfig.addDataSourceProperty("useServerPrepStmts", "true");
            hikariConfig.addDataSourceProperty("useLocalSessionState", "true");
            hikariConfig.addDataSourceProperty("rewriteBatchedStatements", "true");
            hikariConfig.addDataSourceProperty("cacheResultSetMetadata", "true");
            hikariConfig.addDataSourceProperty("cacheServerConfiguration", "true");
            hikariConfig.addDataSourceProperty("elideSetAutoCommits", "true");
            hikariConfig.addDataSourceProperty("maintainTimeStats", "false");

            // Connection pool timings
            hikariConfig.setConnectionTimeout(30000);
            hikariConfig.setIdleTimeout(600000);
            hikariConfig.setMaxLifetime(1800000);
            hikariConfig.setKeepaliveTime(60000);

            dataSource = new HikariDataSource(hikariConfig);
            dataStore = new MySQLDataStore(plugin, dataSource);
            plugin.getLogger().info("Database: MySQL connected to " + host + ":" + port + "/" + database);

        } else {
            // Default to SQLite
            java.io.File dbFile = new java.io.File(plugin.getDataFolder(), "polarsmp.db");
            hikariConfig.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
            hikariConfig.setDriverClassName("org.sqlite.JDBC");
            hikariConfig.setMaximumPoolSize(1); // SQLite only supports one writer
            hikariConfig.setConnectionTimeout(30000);

            // SQLite performance pragmas
            hikariConfig.addDataSourceProperty("journal_mode", "WAL");
            hikariConfig.addDataSourceProperty("synchronous", "NORMAL");

            dataSource = new HikariDataSource(hikariConfig);
            dataStore = new SQLiteDataStore(plugin, dataSource);
            plugin.getLogger().info("Database: SQLite at " + dbFile.getAbsolutePath());
        }

        // Initialize tables
        try {
            dataStore.loadPlayer(java.util.UUID.randomUUID()); // Triggers table creation
        } catch (Exception ignored) {}
        if (type.equals("mysql")) {
            ((MySQLDataStore) dataStore).createTables();
        } else {
            ((SQLiteDataStore) dataStore).createTables();
        }

        return dataStore;
    }

    /**
     * Closes the HikariCP connection pool, releasing all database connections.
     */
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("HikariCP pool closed.");
        }
    }
}
