package com.ultimatelogs.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.time.ZoneId;

public class LogsConfig {

    private ZoneId zoneId;
    private String dateFormat;
    private String timeFormat;

    private String databaseEngine;
    private String sqliteFile;
    private String mysqlHost;
    private int mysqlPort;
    private String mysqlDatabase;
    private String mysqlUsername;
    private String mysqlPassword;
    private boolean mysqlUseSsl;
    private int mysqlPoolSize;

    private int positionSnapshotIntervalSeconds;
    private int inventorySnapshotIntervalSeconds;
    private boolean deduplicationEnabled;
    private int flushIntervalSeconds;

    private boolean logContainerTransactions;
    private boolean logItemEvents;
    private boolean logBlockActions;
    private boolean logSessions;
    private boolean trackIpAddresses;
    private boolean logDeaths;

    private int liveSyncTicks;
    private boolean allowTakingItemsLive;
    private boolean allowTakingItemsHistorical;
    private boolean blockPlacingItems;

    private String prefix;
    private String noPermissionMessage;
    private String playerNotFoundMessage;
    private String offlineLoadedMessage;

    public void load(FileConfiguration config) {
        String tzStr = config.getString("settings.timezone", "Asia/Kolkata");
        try {
            if ("SYSTEM".equalsIgnoreCase(tzStr)) {
                this.zoneId = ZoneId.systemDefault();
            } else {
                this.zoneId = ZoneId.of(tzStr);
            }
        } catch (Exception e) {
            this.zoneId = ZoneId.systemDefault();
        }

        this.dateFormat = config.getString("settings.date-format", "dd/MM/yyyy");
        this.timeFormat = config.getString("settings.time-format", "HH:mm:ss");

        this.databaseEngine = config.getString("database.engine", "SQLITE").toUpperCase();
        this.sqliteFile = config.getString("database.sqlite-file", "ultimate_logs.db");
        this.mysqlHost = config.getString("database.mysql.host", "localhost");
        this.mysqlPort = config.getInt("database.mysql.port", 3306);
        this.mysqlDatabase = config.getString("database.mysql.database", "ultimate_logs");
        this.mysqlUsername = config.getString("database.mysql.username", "root");
        this.mysqlPassword = config.getString("database.mysql.password", "");
        this.mysqlUseSsl = config.getBoolean("database.mysql.use-ssl", false);
        this.mysqlPoolSize = config.getInt("database.mysql.pool-size", 10);

        this.positionSnapshotIntervalSeconds = Math.max(1, config.getInt("logging.position-snapshot-interval-seconds", 1));
        this.inventorySnapshotIntervalSeconds = Math.max(1, config.getInt("logging.inventory-snapshot-interval-seconds", 1));
        this.deduplicationEnabled = config.getBoolean("logging.deduplication-enabled", true);
        this.flushIntervalSeconds = Math.max(1, config.getInt("logging.flush-interval-seconds", 5));

        this.logContainerTransactions = config.getBoolean("logging.log-container-transactions", true);
        this.logItemEvents = config.getBoolean("logging.log-item-events", true);
        this.logBlockActions = config.getBoolean("logging.log-block-actions", true);
        this.logSessions = config.getBoolean("logging.log-sessions", true);
        this.trackIpAddresses = config.getBoolean("logging.track-ip-addresses", true);
        this.logDeaths = config.getBoolean("logging.log-deaths", true);

        this.liveSyncTicks = Math.max(2, config.getInt("inspection.live-sync-ticks", 10));
        this.allowTakingItemsLive = config.getBoolean("inspection.allow-taking-items-live", true);
        this.allowTakingItemsHistorical = config.getBoolean("inspection.allow-taking-items-historical", true);
        this.blockPlacingItems = config.getBoolean("inspection.block-placing-items", true);

        this.prefix = config.getString("messages.prefix", "<gradient:#c084fc:#f472b6><bold>▶ ULTIMATE LOGS</bold></gradient> ");
        this.noPermissionMessage = config.getString("messages.no-permission", "<red>Access denied: You must be staff or OP to use Ultimate Logs.</red>");
        this.playerNotFoundMessage = config.getString("messages.player-not-found", "<red>Player <white>{target}</white> could not be found.</red>");
        this.offlineLoadedMessage = config.getString("messages.offline-loaded", "<green>Player is offline. Loaded most recent recorded snapshot from <yellow>{interval}</yellow>.</green>");
    }

    public ZoneId getZoneId() { return zoneId; }
    public String getDateFormat() { return dateFormat; }
    public String getTimeFormat() { return timeFormat; }
    public String getDatabaseEngine() { return databaseEngine; }
    public String getSqliteFile() { return sqliteFile; }
    public String getMysqlHost() { return mysqlHost; }
    public int getMysqlPort() { return mysqlPort; }
    public String getMysqlDatabase() { return mysqlDatabase; }
    public String getMysqlUsername() { return mysqlUsername; }
    public String getMysqlPassword() { return mysqlPassword; }
    public boolean isMysqlUseSsl() { return mysqlUseSsl; }
    public int getMysqlPoolSize() { return mysqlPoolSize; }

    public int getPositionSnapshotIntervalSeconds() { return positionSnapshotIntervalSeconds; }
    public int getInventorySnapshotIntervalSeconds() { return inventorySnapshotIntervalSeconds; }
    public boolean isDeduplicationEnabled() { return deduplicationEnabled; }
    public int getFlushIntervalSeconds() { return flushIntervalSeconds; }

    public boolean isLogContainerTransactions() { return logContainerTransactions; }
    public boolean isLogItemEvents() { return logItemEvents; }
    public boolean isLogBlockActions() { return logBlockActions; }
    public boolean isLogSessions() { return logSessions; }
    public boolean isTrackIpAddresses() { return trackIpAddresses; }
    public boolean isLogDeaths() { return logDeaths; }

    public int getLiveSyncTicks() { return liveSyncTicks; }
    public boolean isAllowTakingItemsLive() { return allowTakingItemsLive; }
    public boolean isAllowTakingItemsHistorical() { return allowTakingItemsHistorical; }
    public boolean isBlockPlacingItems() { return blockPlacingItems; }

    public String getPrefix() { return prefix; }
    public String getNoPermissionMessage() { return noPermissionMessage; }
    public String getPlayerNotFoundMessage() { return playerNotFoundMessage; }
    public String getOfflineLoadedMessage() { return offlineLoadedMessage; }
}
