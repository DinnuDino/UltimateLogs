package com.ultimatelogs.database;

import com.ultimatelogs.UltimateLogs;
import com.ultimatelogs.config.LogsConfig;
import com.ultimatelogs.database.models.*;
import com.ultimatelogs.serialization.InventorySerializer;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;

public class DatabaseManager {

    private final UltimateLogs plugin;
    private final LogsConfig config;
    private final InventorySerializer serializer;
    private HikariDataSource dataSource;

    private final BlockingQueue<Runnable> writeQueue = new LinkedBlockingQueue<>(30000);
    private final ScheduledExecutorService batchExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "UltimateLogs-DB-Worker");
        t.setDaemon(true);
        return t;
    });

    private final Map<UUID, ActiveInventoryState> activeInventoryMap = new ConcurrentHashMap<>();
    private final Map<UUID, ActivePositionState> activePositionMap = new ConcurrentHashMap<>();
    private final Map<UUID, PendingOfflineModification> pendingModificationsByUuid = new ConcurrentHashMap<>();
    private final Map<String, PendingOfflineModification> pendingModificationsByName = new ConcurrentHashMap<>();

    public DatabaseManager(UltimateLogs plugin, LogsConfig config, InventorySerializer serializer) {
        this.plugin = plugin;
        this.config = config;
        this.serializer = serializer;
    }

    public void initialize() {
        HikariConfig hikari = new HikariConfig();
        if ("MYSQL".equalsIgnoreCase(config.getDatabaseEngine())) {
            hikari.setJdbcUrl("jdbc:mysql://" + config.getMysqlHost() + ":" + config.getMysqlPort() + "/" + config.getMysqlDatabase() + "?useSSL=" + config.isMysqlUseSsl() + "&allowPublicKeyRetrieval=true&characterEncoding=utf8");
            hikari.setUsername(config.getMysqlUsername());
            hikari.setPassword(config.getMysqlPassword());
            hikari.setMaximumPoolSize(config.getMysqlPoolSize());
            hikari.setPoolName("UltimateLogsMySQLPool");
        } else {
            File dbFile = new File(plugin.getDataFolder(), config.getSqliteFile());
            if (dbFile.getParentFile() != null && !dbFile.getParentFile().exists()) {
                dbFile.getParentFile().mkdirs();
            }
            hikari.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
            hikari.setDriverClassName("org.sqlite.JDBC");
            hikari.setConnectionInitSql("PRAGMA journal_mode=WAL; PRAGMA busy_timeout=5000;");
            hikari.setMaximumPoolSize(3);
            hikari.setPoolName("UltimateLogsSQLitePool");
        }

        hikari.setConnectionTimeout(30000L);
        hikari.setIdleTimeout(600000L);
        hikari.setMaxLifetime(1800000L);
        this.dataSource = new HikariDataSource(hikari);

        createTables();
        loadPendingOfflineModifications();

        long flushSecs = config.getFlushIntervalSeconds();
        this.batchExecutor.scheduleWithFixedDelay(this::drainQueue, flushSecs, flushSecs, TimeUnit.SECONDS);
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    private void createTables() {
        boolean isMySQL = "MYSQL".equalsIgnoreCase(config.getDatabaseEngine());
        String autoInc = isMySQL ? "AUTO_INCREMENT" : "AUTOINCREMENT";
        String primaryKey = isMySQL ? "BIGINT AUTO_INCREMENT PRIMARY KEY" : "INTEGER PRIMARY KEY AUTOINCREMENT";
        String longText = isMySQL ? "MEDIUMTEXT" : "TEXT";

        String[] schemas = new String[]{
                // 1. Sessions
                "CREATE TABLE IF NOT EXISTS player_sessions (" +
                        "id " + primaryKey + ", " +
                        "session_id VARCHAR(36) NOT NULL UNIQUE, " +
                        "player_uuid VARCHAR(36) NOT NULL, " +
                        "username VARCHAR(32) NOT NULL, " +
                        "platform VARCHAR(16) NOT NULL DEFAULT 'JAVA', " +
                        "ip_address VARCHAR(64) NULL, " +
                        "joined_at TIMESTAMP NOT NULL, " +
                        "quit_at TIMESTAMP NULL, " +
                        "duration_seconds BIGINT DEFAULT 0, " +
                        "quit_reason VARCHAR(64) NULL" +
                        ");",

                // 2. Positions
                "CREATE TABLE IF NOT EXISTS player_position_snapshots (" +
                        "id " + primaryKey + ", " +
                        "player_uuid VARCHAR(36) NOT NULL, " +
                        "username VARCHAR(32) NOT NULL, " +
                        "world_uuid VARCHAR(36) NOT NULL, " +
                        "world_name VARCHAR(64) NOT NULL, " +
                        "x DOUBLE NOT NULL, " +
                        "y DOUBLE NOT NULL, " +
                        "z DOUBLE NOT NULL, " +
                        "yaw FLOAT NOT NULL, " +
                        "pitch FLOAT NOT NULL, " +
                        "gamemode VARCHAR(16) NOT NULL, " +
                        "health DOUBLE NOT NULL, " +
                        "food_level INT NOT NULL, " +
                        "exp_level INT NOT NULL, " +
                        "snapshot_reason VARCHAR(32) NOT NULL, " +
                        "duration_seconds INT DEFAULT 1, " +
                        "start_timestamp TIMESTAMP NOT NULL, " +
                        "timestamp TIMESTAMP NOT NULL" +
                        ");",

                // 3. Inventories
                "CREATE TABLE IF NOT EXISTS player_inventory_snapshots (" +
                        "id " + primaryKey + ", " +
                        "snapshot_id VARCHAR(48) NOT NULL UNIQUE, " +
                        "player_uuid VARCHAR(36) NOT NULL, " +
                        "username VARCHAR(32) NOT NULL, " +
                        "world_name VARCHAR(64) NOT NULL, " +
                        "x DOUBLE NOT NULL, " +
                        "y DOUBLE NOT NULL, " +
                        "z DOUBLE NOT NULL, " +
                        "gamemode VARCHAR(16) NOT NULL, " +
                        "health DOUBLE NOT NULL, " +
                        "hunger INT NOT NULL, " +
                        "saturation FLOAT NOT NULL, " +
                        "exp_level INT NOT NULL, " +
                        "total_exp INT NOT NULL, " +
                        "selected_slot INT NOT NULL, " +
                        "snapshot_reason VARCHAR(32) NOT NULL, " +
                        "inventory_data " + longText + " NOT NULL, " +
                        "armor_data TEXT NOT NULL, " +
                        "offhand_data TEXT NOT NULL, " +
                        "ender_chest_data " + longText + " NOT NULL, " +
                        "cursor_data TEXT NULL, " +
                        "potion_effects TEXT NULL, " +
                        "correlation_id VARCHAR(36) NULL, " +
                        "duration_seconds INT DEFAULT 1, " +
                        "start_timestamp TIMESTAMP NOT NULL, " +
                        "state_hash VARCHAR(64) NULL, " +
                        "timestamp TIMESTAMP NOT NULL" +
                        ");",

                // 4. Deaths
                "CREATE TABLE IF NOT EXISTS player_death_records (" +
                        "id " + primaryKey + ", " +
                        "death_id VARCHAR(48) NOT NULL UNIQUE, " +
                        "player_uuid VARCHAR(36) NOT NULL, " +
                        "username VARCHAR(32) NOT NULL, " +
                        "world_name VARCHAR(64) NOT NULL, " +
                        "x DOUBLE NOT NULL, " +
                        "y DOUBLE NOT NULL, " +
                        "z DOUBLE NOT NULL, " +
                        "yaw FLOAT NOT NULL, " +
                        "pitch FLOAT NOT NULL, " +
                        "death_message TEXT NOT NULL, " +
                        "death_cause VARCHAR(32) NOT NULL, " +
                        "killer_name VARCHAR(64) NULL, " +
                        "killer_type VARCHAR(32) NULL, " +
                        "exp_level INT NOT NULL, " +
                        "total_exp INT NOT NULL, " +
                        "exp_dropped INT NOT NULL, " +
                        "inventory_snapshot_id VARCHAR(48) NOT NULL, " +
                        "ender_chest_data " + longText + " NOT NULL, " +
                        "item_count INT NOT NULL, " +
                        "drop_count INT NOT NULL, " +
                        "timestamp TIMESTAMP NOT NULL" +
                        ");",

                // 5. EnderChest Events
                "CREATE TABLE IF NOT EXISTS player_enderchest_events (" +
                        "id " + primaryKey + ", " +
                        "event_type VARCHAR(32) NOT NULL, " +
                        "player_uuid VARCHAR(36) NOT NULL, " +
                        "username VARCHAR(32) NOT NULL, " +
                        "material VARCHAR(64) NOT NULL, " +
                        "amount INT NOT NULL, " +
                        "item_display_name VARCHAR(128) NULL, " +
                        "item_serialized TEXT NULL, " +
                        "world_name VARCHAR(64) NOT NULL, " +
                        "x DOUBLE NOT NULL, " +
                        "y DOUBLE NOT NULL, " +
                        "z DOUBLE NOT NULL, " +
                        "correlation_id VARCHAR(36) NOT NULL, " +
                        "details TEXT NULL, " +
                        "timestamp TIMESTAMP NOT NULL" +
                        ");",

                // 6. Item Events
                "CREATE TABLE IF NOT EXISTS player_item_events (" +
                        "id " + primaryKey + ", " +
                        "event_type VARCHAR(32) NOT NULL, " +
                        "actor_uuid VARCHAR(36) NOT NULL, " +
                        "actor_name VARCHAR(32) NOT NULL, " +
                        "affected_player_uuid VARCHAR(36) NOT NULL, " +
                        "affected_player_name VARCHAR(32) NOT NULL, " +
                        "material VARCHAR(64) NOT NULL, " +
                        "amount INT NOT NULL, " +
                        "item_display_name VARCHAR(128) NULL, " +
                        "item_serialized TEXT NULL, " +
                        "world_name VARCHAR(64) NOT NULL, " +
                        "x DOUBLE NOT NULL, " +
                        "y DOUBLE NOT NULL, " +
                        "z DOUBLE NOT NULL, " +
                        "correlation_id VARCHAR(36) NOT NULL, " +
                        "details TEXT NULL, " +
                        "timestamp TIMESTAMP NOT NULL" +
                        ");",

                // 7. Action Logs
                "CREATE TABLE IF NOT EXISTS player_action_logs (" +
                        "id " + primaryKey + ", " +
                        "event_type VARCHAR(32) NOT NULL, " +
                        "player_uuid VARCHAR(36) NOT NULL, " +
                        "username VARCHAR(32) NOT NULL, " +
                        "world_name VARCHAR(64) NOT NULL, " +
                        "x DOUBLE NOT NULL, " +
                        "y DOUBLE NOT NULL, " +
                        "z DOUBLE NOT NULL, " +
                        "target_info VARCHAR(128) NULL, " +
                        "details TEXT NULL, " +
                        "timestamp TIMESTAMP NOT NULL" +
                        ");",

                // 8. Admin Actions
                "CREATE TABLE IF NOT EXISTS audit_admin_actions (" +
                        "id " + primaryKey + ", " +
                        "action_type VARCHAR(32) NOT NULL, " +
                        "operator_uuid VARCHAR(36) NOT NULL, " +
                        "operator_name VARCHAR(32) NOT NULL, " +
                        "target_uuid VARCHAR(36) NULL, " +
                        "target_name VARCHAR(32) NULL, " +
                        "reason TEXT NOT NULL, " +
                        "source_id VARCHAR(64) NULL, " +
                        "items_affected TEXT NULL, " +
                        "result VARCHAR(32) NOT NULL, " +
                        "correlation_id VARCHAR(36) NOT NULL, " +
                        "timestamp TIMESTAMP NOT NULL" +
                        ");",

                // 9. Pending Offline Modifications
                "CREATE TABLE IF NOT EXISTS pending_offline_modifications (" +
                        "player_uuid VARCHAR(36) PRIMARY KEY, " +
                        "username VARCHAR(32) NOT NULL, " +
                        "inventory_data " + longText + " NULL, " +
                        "armor_data TEXT NULL, " +
                        "offhand_data TEXT NULL, " +
                        "ender_chest_data " + longText + " NULL, " +
                        "modified_by VARCHAR(32) NOT NULL, " +
                        "modified_at TIMESTAMP NOT NULL" +
                        ");"
        };

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            for (String sql : schemas) {
                stmt.execute(sql);
            }
            // Indexes for speed
            try { stmt.execute("CREATE INDEX IF NOT EXISTS idx_inv_user_time ON player_inventory_snapshots (username, timestamp);"); } catch (Exception ignored) {}
            try { stmt.execute("CREATE INDEX IF NOT EXISTS idx_pos_user_time ON player_position_snapshots (username, timestamp);"); } catch (Exception ignored) {}
            try { stmt.execute("CREATE INDEX IF NOT EXISTS idx_death_user_time ON player_death_records (username, timestamp);"); } catch (Exception ignored) {}
            try { stmt.execute("CREATE INDEX IF NOT EXISTS idx_item_user_time ON player_item_events (affected_player_name, timestamp);"); } catch (Exception ignored) {}
            try { stmt.execute("CREATE INDEX IF NOT EXISTS idx_act_user_time ON player_action_logs (username, timestamp);"); } catch (Exception ignored) {}
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize UltimateLogs tables", e);
        }
    }

    // ==========================================
    // PENDING OFFLINE MODIFICATIONS
    // ==========================================
    private void loadPendingOfflineModifications() {
        String sql = "SELECT * FROM pending_offline_modifications";
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String uuidStr = rs.getString("player_uuid");
                String username = rs.getString("username");
                String inv = rs.getString("inventory_data");
                String armor = rs.getString("armor_data");
                String offhand = rs.getString("offhand_data");
                String ec = rs.getString("ender_chest_data");
                String modBy = rs.getString("modified_by");
                Timestamp ts = rs.getTimestamp("modified_at");
                Instant modifiedAt = ts != null ? ts.toInstant() : Instant.now();

                PendingOfflineModification mod = new PendingOfflineModification(
                        uuidStr, username, inv, armor, offhand, ec, modBy, modifiedAt
                );
                if (uuidStr != null) {
                    try {
                        pendingModificationsByUuid.put(UUID.fromString(uuidStr), mod);
                    } catch (Exception ignored) {}
                }
                if (username != null) {
                    pendingModificationsByName.put(username.toLowerCase(Locale.ROOT), mod);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Error loading pending offline modifications", e);
        }
    }

    public PendingOfflineModification getPendingOfflineModification(String nameOrUuid) {
        if (nameOrUuid == null || nameOrUuid.isBlank()) return null;
        String lower = nameOrUuid.toLowerCase(Locale.ROOT);
        PendingOfflineModification mod = pendingModificationsByName.get(lower);
        if (mod != null) return mod;

        try {
            UUID u = UUID.fromString(nameOrUuid);
            mod = pendingModificationsByUuid.get(u);
            if (mod != null) return mod;
        } catch (Exception ignored) {}

        try {
            OfflinePlayer op = Bukkit.getOfflinePlayer(nameOrUuid);
            if (op != null && op.getUniqueId() != null) {
                return pendingModificationsByUuid.get(op.getUniqueId());
            }
        } catch (Exception ignored) {}

        return null;
    }

    public void savePendingOfflineInventory(String username, UUID uuid, ItemStack[] storage, ItemStack[] armor, ItemStack offhand, String staffName) {
        String invData = serializer.serializeItemArray(storage);
        String armorData = serializer.serializeItemArray(armor);
        String offhandData = serializer.serializeItem(offhand);
        Instant now = Instant.now();

        UUID effectiveUuid = uuid;
        if (effectiveUuid == null) {
            try {
                OfflinePlayer op = Bukkit.getOfflinePlayer(username);
                if (op != null) effectiveUuid = op.getUniqueId();
            } catch (Exception ignored) {}
        }
        if (effectiveUuid == null) {
            effectiveUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username.toLowerCase(Locale.ROOT)).getBytes(StandardCharsets.UTF_8));
        }

        UUID finalUuid = effectiveUuid;
        PendingOfflineModification existing = getPendingOfflineModification(username);
        String echestData = existing != null ? existing.getEnderChestData() : null;

        PendingOfflineModification mod = new PendingOfflineModification(
                finalUuid.toString(), username, invData, armorData, offhandData, echestData, staffName, now
        );
        pendingModificationsByUuid.put(finalUuid, mod);
        pendingModificationsByName.put(username.toLowerCase(Locale.ROOT), mod);

        queueWrite(() -> {
            boolean isSQLite = !"MYSQL".equalsIgnoreCase(config.getDatabaseEngine());
            String sql;
            if (isSQLite) {
                sql = "INSERT INTO pending_offline_modifications (player_uuid, username, inventory_data, armor_data, offhand_data, ender_chest_data, modified_by, modified_at) " +
                      "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                      "ON CONFLICT(player_uuid) DO UPDATE SET username=excluded.username, inventory_data=excluded.inventory_data, armor_data=excluded.armor_data, offhand_data=excluded.offhand_data, modified_by=excluded.modified_by, modified_at=excluded.modified_at";
            } else {
                sql = "INSERT INTO pending_offline_modifications (player_uuid, username, inventory_data, armor_data, offhand_data, ender_chest_data, modified_by, modified_at) " +
                      "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                      "ON DUPLICATE KEY UPDATE username=VALUES(username), inventory_data=VALUES(inventory_data), armor_data=VALUES(armor_data), offhand_data=VALUES(offhand_data), modified_by=VALUES(modified_by), modified_at=VALUES(modified_at)";
            }
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, finalUuid.toString());
                ps.setString(2, username);
                ps.setString(3, invData);
                ps.setString(4, armorData);
                ps.setString(5, offhandData);
                ps.setString(6, echestData);
                ps.setString(7, staffName);
                ps.setTimestamp(8, Timestamp.from(now));
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Error saving pending offline inventory for " + username, e);
            }
        });
    }

    public void savePendingOfflineEnderChest(String username, UUID uuid, ItemStack[] echest, String staffName) {
        String echestData = serializer.serializeItemArray(echest);
        Instant now = Instant.now();

        UUID effectiveUuid = uuid;
        if (effectiveUuid == null) {
            try {
                OfflinePlayer op = Bukkit.getOfflinePlayer(username);
                if (op != null) effectiveUuid = op.getUniqueId();
            } catch (Exception ignored) {}
        }
        if (effectiveUuid == null) {
            effectiveUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username.toLowerCase(Locale.ROOT)).getBytes(StandardCharsets.UTF_8));
        }

        UUID finalUuid = effectiveUuid;
        PendingOfflineModification existing = getPendingOfflineModification(username);
        String invData = existing != null ? existing.getInventoryData() : null;
        String armorData = existing != null ? existing.getArmorData() : null;
        String offhandData = existing != null ? existing.getOffhandData() : null;

        PendingOfflineModification mod = new PendingOfflineModification(
                finalUuid.toString(), username, invData, armorData, offhandData, echestData, staffName, now
        );
        pendingModificationsByUuid.put(finalUuid, mod);
        pendingModificationsByName.put(username.toLowerCase(Locale.ROOT), mod);

        queueWrite(() -> {
            boolean isSQLite = !"MYSQL".equalsIgnoreCase(config.getDatabaseEngine());
            String sql;
            if (isSQLite) {
                sql = "INSERT INTO pending_offline_modifications (player_uuid, username, inventory_data, armor_data, offhand_data, ender_chest_data, modified_by, modified_at) " +
                      "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                      "ON CONFLICT(player_uuid) DO UPDATE SET username=excluded.username, ender_chest_data=excluded.ender_chest_data, modified_by=excluded.modified_by, modified_at=excluded.modified_at";
            } else {
                sql = "INSERT INTO pending_offline_modifications (player_uuid, username, inventory_data, armor_data, offhand_data, ender_chest_data, modified_by, modified_at) " +
                      "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                      "ON DUPLICATE KEY UPDATE username=VALUES(username), ender_chest_data=VALUES(ender_chest_data), modified_by=VALUES(modified_by), modified_at=VALUES(modified_at)";
            }
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, finalUuid.toString());
                ps.setString(2, username);
                ps.setString(3, invData);
                ps.setString(4, armorData);
                ps.setString(5, offhandData);
                ps.setString(6, echestData);
                ps.setString(7, staffName);
                ps.setTimestamp(8, Timestamp.from(now));
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Error saving pending offline enderchest for " + username, e);
            }
        });
    }

    public void applyPendingOfflineModifications(Player player) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        String name = player.getName().toLowerCase(Locale.ROOT);

        PendingOfflineModification mod = pendingModificationsByUuid.remove(uuid);
        if (mod == null) {
            mod = pendingModificationsByName.remove(name);
        } else {
            pendingModificationsByName.remove(name);
        }

        if (mod == null) return;

        boolean updated = false;
        if (mod.getInventoryData() != null) {
            ItemStack[] storage = serializer.deserializeItemArray(mod.getInventoryData());
            player.getInventory().setStorageContents(storage);
            updated = true;
        }
        if (mod.getArmorData() != null) {
            ItemStack[] armor = serializer.deserializeItemArray(mod.getArmorData());
            player.getInventory().setArmorContents(armor);
            updated = true;
        }
        if (mod.getOffhandData() != null) {
            ItemStack offhand = serializer.deserializeItem(mod.getOffhandData());
            player.getInventory().setItemInOffHand(offhand);
            updated = true;
        }
        if (mod.getEnderChestData() != null) {
            ItemStack[] echest = serializer.deserializeItemArray(mod.getEnderChestData());
            player.getEnderChest().setContents(echest);
            updated = true;
        }

        if (updated) {
            player.updateInventory();
            plugin.getLogger().info("Applied pending offline inventory/EnderChest modifications to " + player.getName() + " (" + uuid + ") modified by " + mod.getModifiedBy());
            queueWrite(() -> {
                String sql = "DELETE FROM pending_offline_modifications WHERE player_uuid = ?";
                try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, uuid.toString());
                    ps.executeUpdate();
                } catch (SQLException ignored) {}
            });
        }
    }

    public void queueWrite(Runnable task) {
        if (!this.writeQueue.offer(task)) {
            plugin.getLogger().warning("[UltimateLogs DB] Queue full, executing synchronously.");
            task.run();
        }
    }

    private void drainQueue() {
        int drained = 0;
        while (!writeQueue.isEmpty() && drained < 1000) {
            Runnable task = writeQueue.poll();
            if (task != null) {
                try {
                    task.run();
                    drained++;
                } catch (Throwable t) {
                    plugin.getLogger().log(Level.WARNING, "[UltimateLogs DB] Error in queued batch task", t);
                }
            }
        }
    }

    public void forceFlush() {
        while (!writeQueue.isEmpty()) {
            Runnable task = writeQueue.poll();
            if (task != null) {
                try { task.run(); } catch (Throwable ignored) {}
            }
        }
    }

    public void shutdown() {
        forceFlush();
        batchExecutor.shutdown();
        try {
            if (!batchExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                batchExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            batchExecutor.shutdownNow();
        }
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    // ==========================================
    // INVENTORY SNAPSHOT RECORDING & INTERVAL
    // ==========================================
    public String recordInventorySnapshot(Player player, String reason, String correlationId) {
        Location loc = player.getLocation();
        ItemStack[] storage = player.getInventory().getStorageContents();
        ItemStack[] armor = player.getInventory().getArmorContents();
        ItemStack offhand = player.getInventory().getItemInOffHand();
        ItemStack[] echest = player.getEnderChest().getContents();
        ItemStack cursor = player.getItemOnCursor();

        String invData = serializer.serializeItemArray(storage);
        String armorData = serializer.serializeItemArray(armor);
        String offhandData = serializer.serializeItem(offhand);
        String echestData = serializer.serializeItemArray(echest);
        String cursorData = serializer.serializeItem(cursor);
        String effects = player.getActivePotionEffects().toString();

        String stateHash = Integer.toHexString(Objects.hash(invData, armorData, offhandData, echestData, cursorData,
                loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), player.getHealth(), player.getFoodLevel()));

        UUID uuid = player.getUniqueId();
        ActiveInventoryState active = activeInventoryMap.get(uuid);
        Instant now = Instant.now();

        // Deduplication interval merging
        if (config.isDeduplicationEnabled() && "interval".equalsIgnoreCase(reason) && active != null && active.stateHash.equals(stateHash)) {
            active.durationSeconds++;
            active.lastActive = now;
            int dur = active.durationSeconds;
            String activeId = active.snapshotId;
            if (dur % 5 == 0) {
                queueWrite(() -> {
                    String updateSql = "UPDATE player_inventory_snapshots SET duration_seconds = ?, timestamp = ? WHERE snapshot_id = ?";
                    try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(updateSql)) {
                        ps.setInt(1, dur);
                        ps.setTimestamp(2, Timestamp.from(now));
                        ps.setString(3, activeId);
                        ps.executeUpdate();
                    } catch (SQLException ignored) {}
                });
            }
            return active.snapshotId;
        }

        String snapshotId = "snap-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        activeInventoryMap.put(uuid, new ActiveInventoryState(snapshotId, stateHash, now, 1, now));

        queueWrite(() -> {
            String insertSql = "INSERT INTO player_inventory_snapshots (snapshot_id, player_uuid, username, world_name, x, y, z, gamemode, health, hunger, saturation, exp_level, total_exp, selected_slot, snapshot_reason, inventory_data, armor_data, offhand_data, ender_chest_data, cursor_data, potion_effects, correlation_id, duration_seconds, start_timestamp, state_hash, timestamp) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setString(1, snapshotId);
                ps.setString(2, player.getUniqueId().toString());
                ps.setString(3, player.getName());
                ps.setString(4, loc.getWorld().getName());
                ps.setDouble(5, loc.getX());
                ps.setDouble(6, loc.getY());
                ps.setDouble(7, loc.getZ());
                ps.setString(8, player.getGameMode().name());
                ps.setDouble(9, player.getHealth());
                ps.setInt(10, player.getFoodLevel());
                ps.setFloat(11, player.getSaturation());
                ps.setInt(12, player.getLevel());
                ps.setInt(13, player.getTotalExperience());
                ps.setInt(14, player.getInventory().getHeldItemSlot());
                ps.setString(15, reason);
                ps.setString(16, invData);
                ps.setString(17, armorData);
                ps.setString(18, offhandData);
                ps.setString(19, echestData);
                ps.setString(20, cursorData);
                ps.setString(21, effects);
                ps.setString(22, correlationId != null ? correlationId : UUID.randomUUID().toString());
                ps.setInt(23, 1);
                ps.setTimestamp(24, Timestamp.from(now));
                ps.setString(25, stateHash);
                ps.setTimestamp(26, Timestamp.from(now));
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save inventory snapshot " + snapshotId, e);
            }
        });
        return snapshotId;
    }

    // ==========================================
    // POSITION SNAPSHOT RECORDING & INTERVAL
    // ==========================================
    public void recordPositionSnapshot(Player player, String reason) {
        if (player == null || !player.isOnline()) return;
        Location loc = player.getLocation();
        if (loc.getWorld() == null) return;

        UUID uuid = player.getUniqueId();
        Instant now = Instant.now();
        int blockX = loc.getBlockX();
        int blockY = loc.getBlockY();
        int blockZ = loc.getBlockZ();
        String worldName = loc.getWorld().getName();

        ActivePositionState active = activePositionMap.get(uuid);
        if (config.isDeduplicationEnabled() && "interval".equalsIgnoreCase(reason) && active != null
                && active.worldName.equals(worldName) && active.blockX == blockX && active.blockY == blockY && active.blockZ == blockZ) {
            active.durationSeconds++;
            active.lastActive = now;
            long rowId = active.rowId;
            int dur = active.durationSeconds;
            if (dur % 5 == 0 && rowId > 0) {
                queueWrite(() -> {
                    String updateSql = "UPDATE player_position_snapshots SET duration_seconds = ?, timestamp = ? WHERE id = ?";
                    try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(updateSql)) {
                        ps.setInt(1, dur);
                        ps.setTimestamp(2, Timestamp.from(now));
                        ps.setLong(3, rowId);
                        ps.executeUpdate();
                    } catch (SQLException ignored) {}
                });
            }
            return;
        }

        ActivePositionState newState = new ActivePositionState(worldName, blockX, blockY, blockZ, 1, now, 0);
        activePositionMap.put(uuid, newState);

        queueWrite(() -> {
            String insertSql = "INSERT INTO player_position_snapshots (player_uuid, username, world_uuid, world_name, x, y, z, yaw, pitch, gamemode, health, food_level, exp_level, snapshot_reason, duration_seconds, start_timestamp, timestamp) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, player.getName());
                ps.setString(3, loc.getWorld().getUID().toString());
                ps.setString(4, worldName);
                ps.setDouble(5, loc.getX());
                ps.setDouble(6, loc.getY());
                ps.setDouble(7, loc.getZ());
                ps.setFloat(8, loc.getYaw());
                ps.setFloat(9, loc.getPitch());
                ps.setString(10, player.getGameMode().name());
                ps.setDouble(11, player.getHealth());
                ps.setInt(12, player.getFoodLevel());
                ps.setInt(13, player.getLevel());
                ps.setString(14, reason);
                ps.setInt(15, 1);
                ps.setTimestamp(16, Timestamp.from(now));
                ps.setTimestamp(17, Timestamp.from(now));
                ps.executeUpdate();

                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        newState.rowId = rs.getLong(1);
                    }
                }
            } catch (SQLException ignored) {}
        });
    }

    // ==========================================
    // DEATH RECORD LOGGING
    // ==========================================
    public void recordDeath(Player player, String deathMessage, String deathCause, String killerName, String killerType, int expDropped, int dropCount) {
        Location loc = player.getLocation();
        Instant now = Instant.now();
        String deathId = "death-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 6);

        // Record immediate inventory snapshot for this death
        String snapId = recordInventorySnapshot(player, "death", deathId);
        recordPositionSnapshot(player, "death");

        String echestData = serializer.serializeItemArray(player.getEnderChest().getContents());

        int totalItems = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && !item.getType().isAir()) totalItems++;
        }

        int finalTotalItems = totalItems;
        queueWrite(() -> {
            String insertSql = "INSERT INTO player_death_records (death_id, player_uuid, username, world_name, x, y, z, yaw, pitch, death_message, death_cause, killer_name, killer_type, exp_level, total_exp, exp_dropped, inventory_snapshot_id, ender_chest_data, item_count, drop_count, timestamp) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setString(1, deathId);
                ps.setString(2, player.getUniqueId().toString());
                ps.setString(3, player.getName());
                ps.setString(4, loc.getWorld().getName());
                ps.setDouble(5, loc.getX());
                ps.setDouble(6, loc.getY());
                ps.setDouble(7, loc.getZ());
                ps.setFloat(8, loc.getYaw());
                ps.setFloat(9, loc.getPitch());
                ps.setString(10, deathMessage != null ? deathMessage : player.getName() + " died");
                ps.setString(11, deathCause != null ? deathCause : "UNKNOWN");
                ps.setString(12, killerName != null ? killerName : "None");
                ps.setString(13, killerType != null ? killerType : "ENVIRONMENT");
                ps.setInt(14, player.getLevel());
                ps.setInt(15, player.getTotalExperience());
                ps.setInt(16, expDropped);
                ps.setString(17, snapId);
                ps.setString(18, echestData);
                ps.setInt(19, finalTotalItems);
                ps.setInt(20, dropCount);
                ps.setTimestamp(21, Timestamp.from(now));
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to record death " + deathId, e);
            }
        });
    }

    // ==========================================
    // OTHER LOGGERS
    // ==========================================
    public void logItemEvent(String eventType, Player actor, Player affected, ItemStack item, String details) {
        if (!config.isLogItemEvents() || item == null) return;
        Location loc = actor.getLocation();
        String serialized = serializer.serializeItem(item);
        String name = item.hasItemMeta() && item.getItemMeta().hasDisplayName() ? item.getItemMeta().getDisplayName() : null;
        Instant now = Instant.now();

        queueWrite(() -> {
            String sql = "INSERT INTO player_item_events (event_type, actor_uuid, actor_name, affected_player_uuid, affected_player_name, material, amount, item_display_name, item_serialized, world_name, x, y, z, correlation_id, details, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, eventType);
                ps.setString(2, actor.getUniqueId().toString());
                ps.setString(3, actor.getName());
                ps.setString(4, affected != null ? affected.getUniqueId().toString() : actor.getUniqueId().toString());
                ps.setString(5, affected != null ? affected.getName() : actor.getName());
                ps.setString(6, item.getType().name());
                ps.setInt(7, item.getAmount());
                ps.setString(8, name);
                ps.setString(9, serialized);
                ps.setString(10, loc.getWorld().getName());
                ps.setDouble(11, loc.getX());
                ps.setDouble(12, loc.getY());
                ps.setDouble(13, loc.getZ());
                ps.setString(14, UUID.randomUUID().toString());
                ps.setString(15, details);
                ps.setTimestamp(16, Timestamp.from(now));
                ps.executeUpdate();
            } catch (SQLException ignored) {}
        });
    }

    public void logEnderChestEvent(String type, Player player, ItemStack item, String details) {
        Location loc = player.getLocation();
        String serialized = item != null ? serializer.serializeItem(item) : "";
        String matName = item != null ? item.getType().name() : "NONE";
        int amt = item != null ? item.getAmount() : 0;
        String name = item != null && item.hasItemMeta() && item.getItemMeta().hasDisplayName() ? item.getItemMeta().getDisplayName() : null;
        Instant now = Instant.now();

        queueWrite(() -> {
            String sql = "INSERT INTO player_enderchest_events (event_type, player_uuid, username, material, amount, item_display_name, item_serialized, world_name, x, y, z, correlation_id, details, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, type);
                ps.setString(2, player.getUniqueId().toString());
                ps.setString(3, player.getName());
                ps.setString(4, matName);
                ps.setInt(5, amt);
                ps.setString(6, name);
                ps.setString(7, serialized);
                ps.setString(8, loc.getWorld().getName());
                ps.setDouble(9, loc.getX());
                ps.setDouble(10, loc.getY());
                ps.setDouble(11, loc.getZ());
                ps.setString(12, UUID.randomUUID().toString());
                ps.setString(13, details);
                ps.setTimestamp(14, Timestamp.from(now));
                ps.executeUpdate();
            } catch (SQLException ignored) {}
        });
    }

    public void logAction(String eventType, Player player, Location loc, String targetInfo, String details) {
        if (!config.isLogBlockActions()) return;
        Instant now = Instant.now();
        queueWrite(() -> {
            String sql = "INSERT INTO player_action_logs (event_type, player_uuid, username, world_name, x, y, z, target_info, details, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, eventType);
                ps.setString(2, player.getUniqueId().toString());
                ps.setString(3, player.getName());
                ps.setString(4, loc.getWorld().getName());
                ps.setDouble(5, loc.getX());
                ps.setDouble(6, loc.getY());
                ps.setDouble(7, loc.getZ());
                ps.setString(8, targetInfo);
                ps.setString(9, details);
                ps.setTimestamp(10, Timestamp.from(now));
                ps.executeUpdate();
            } catch (SQLException ignored) {}
        });
    }

    public void logAdminAction(String actionType, String opUuid, String opName, String targetUuid, String targetName, String reason, String sourceId, String itemsAffected, String result, String correlationId) {
        Instant now = Instant.now();
        queueWrite(() -> {
            String sql = "INSERT INTO audit_admin_actions (action_type, operator_uuid, operator_name, target_uuid, target_name, reason, source_id, items_affected, result, correlation_id, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, actionType);
                ps.setString(2, opUuid != null ? opUuid : "");
                ps.setString(3, opName != null ? opName : "CONSOLE");
                ps.setString(4, targetUuid);
                ps.setString(5, targetName);
                ps.setString(6, reason != null ? reason : "None");
                ps.setString(7, sourceId);
                ps.setString(8, itemsAffected);
                ps.setString(9, result);
                ps.setString(10, correlationId != null ? correlationId : UUID.randomUUID().toString());
                ps.setTimestamp(11, Timestamp.from(now));
                ps.executeUpdate();
            } catch (SQLException ignored) {}
        });
    }

    // ==========================================
    // ASYNC QUERY METHODS FOR GUIS & COMMANDS
    // ==========================================
    private String resolvePlayerUuidString(String username) {
        if (username == null || username.isBlank()) return "";
        try {
            UUID u = UUID.fromString(username);
            return u.toString();
        } catch (Exception ignored) {}
        try {
            OfflinePlayer op = Bukkit.getOfflinePlayer(username);
            if (op != null && op.getUniqueId() != null) {
                return op.getUniqueId().toString();
            }
        } catch (Exception ignored) {}
        return "";
    }

    public CompletableFuture<InventorySnapshotRecord> queryLatestInventorySnapshot(String username) {
        return CompletableFuture.supplyAsync(() -> {
            String uuidStr = resolvePlayerUuidString(username);
            String sql = "SELECT * FROM player_inventory_snapshots WHERE LOWER(username) = LOWER(?) OR player_uuid = ? ORDER BY id DESC LIMIT 1";
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, username);
                ps.setString(2, uuidStr);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return mapInventorySnapshot(rs);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error querying latest snapshot for " + username, e);
            }
            return null;
        });
    }

    public CompletableFuture<InventorySnapshotRecord> queryInventorySnapshotBefore(String username, Instant instant) {
        return CompletableFuture.supplyAsync(() -> {
            String uuidStr = resolvePlayerUuidString(username);
            String sql = "SELECT * FROM player_inventory_snapshots WHERE (LOWER(username) = LOWER(?) OR player_uuid = ?) AND timestamp <= ? ORDER BY id DESC LIMIT 1";
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, username);
                ps.setString(2, uuidStr);
                ps.setTimestamp(3, Timestamp.from(instant));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return mapInventorySnapshot(rs);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error querying snapshot before " + instant, e);
            }
            return null;
        });
    }

    public CompletableFuture<InventorySnapshotRecord> queryInventorySnapshotById(String snapshotId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM player_inventory_snapshots WHERE snapshot_id = ? LIMIT 1";
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, snapshotId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return mapInventorySnapshot(rs);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error querying snapshot by id " + snapshotId, e);
            }
            return null;
        });
    }

    public CompletableFuture<List<InventorySnapshotRecord>> queryInventorySnapshotHistory(String username, int limit, int offset) {
        return CompletableFuture.supplyAsync(() -> {
            List<InventorySnapshotRecord> list = new ArrayList<>();
            String uuidStr = resolvePlayerUuidString(username);
            String sql = "SELECT * FROM player_inventory_snapshots WHERE LOWER(username) = LOWER(?) OR player_uuid = ? ORDER BY id DESC LIMIT ? OFFSET ?";
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, username);
                ps.setString(2, uuidStr);
                ps.setInt(3, limit);
                ps.setInt(4, offset);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) list.add(mapInventorySnapshot(rs));
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error querying snapshot history", e);
            }
            return list;
        });
    }

    public CompletableFuture<List<EnderChestSnapshotRecord>> queryEnderChestSnapshotHistory(String username, int limit, int offset) {
        return CompletableFuture.supplyAsync(() -> {
            List<EnderChestSnapshotRecord> list = new ArrayList<>();
            String uuidStr = resolvePlayerUuidString(username);
            String sql = "SELECT snapshot_id, player_uuid, username, world_name, x, y, z, snapshot_reason, ender_chest_data, duration_seconds, start_timestamp, timestamp FROM player_inventory_snapshots WHERE LOWER(username) = LOWER(?) OR player_uuid = ? ORDER BY id DESC LIMIT ? OFFSET ?";
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, username);
                ps.setString(2, uuidStr);
                ps.setInt(3, limit);
                ps.setInt(4, offset);
                ps.setString(1, username);
                ps.setInt(2, limit);
                ps.setInt(3, offset);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String snapId = rs.getString("snapshot_id");
                        String uuid = rs.getString("player_uuid");
                        String uName = rs.getString("username");
                        String world = rs.getString("world_name");
                        double x = rs.getDouble("x");
                        double y = rs.getDouble("y");
                        double z = rs.getDouble("z");
                        String reason = rs.getString("snapshot_reason");
                        ItemStack[] contents = serializer.deserializeItemArray(rs.getString("ender_chest_data"));
                        int dur = rs.getInt("duration_seconds");
                        Timestamp startTs = rs.getTimestamp("start_timestamp");
                        Timestamp endTs = rs.getTimestamp("timestamp");
                        list.add(new EnderChestSnapshotRecord(snapId, uuid, uName, world, x, y, z, reason, contents, dur,
                                startTs != null ? startTs.toInstant() : endTs.toInstant().minusSeconds(dur), endTs.toInstant()));
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error querying echest history", e);
            }
            return list;
        });
    }

    public CompletableFuture<List<PositionSnapshotRecord>> queryPositionSnapshotHistory(String username, int limit, int offset) {
        return CompletableFuture.supplyAsync(() -> {
            List<PositionSnapshotRecord> list = new ArrayList<>();
            String sql = "SELECT * FROM player_position_snapshots WHERE username = ? ORDER BY id DESC LIMIT ? OFFSET ?";
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, username);
                ps.setInt(2, limit);
                ps.setInt(3, offset);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        long id = rs.getLong("id");
                        String uuid = rs.getString("player_uuid");
                        String uName = rs.getString("username");
                        String wUuid = rs.getString("world_uuid");
                        String wName = rs.getString("world_name");
                        double x = rs.getDouble("x");
                        double y = rs.getDouble("y");
                        double z = rs.getDouble("z");
                        float yaw = rs.getFloat("yaw");
                        float pitch = rs.getFloat("pitch");
                        String gm = rs.getString("gamemode");
                        double hp = rs.getDouble("health");
                        int food = rs.getInt("food_level");
                        int exp = rs.getInt("exp_level");
                        String reason = rs.getString("snapshot_reason");
                        int dur = rs.getInt("duration_seconds");
                        Timestamp startTs = rs.getTimestamp("start_timestamp");
                        Timestamp endTs = rs.getTimestamp("timestamp");
                        list.add(new PositionSnapshotRecord(id, uuid, uName, wUuid, wName, x, y, z, yaw, pitch, gm, hp, food, exp, reason, dur,
                                startTs != null ? startTs.toInstant() : endTs.toInstant().minusSeconds(dur), endTs.toInstant()));
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error querying position history", e);
            }
            return list;
        });
    }

    public CompletableFuture<List<DeathRecord>> queryDeathHistory(String username, int limit, int offset) {
        return CompletableFuture.supplyAsync(() -> {
            List<DeathRecord> list = new ArrayList<>();
            String sql = "SELECT * FROM player_death_records WHERE username = ? ORDER BY id DESC LIMIT ? OFFSET ?";
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, username);
                ps.setInt(2, limit);
                ps.setInt(3, offset);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String deathId = rs.getString("death_id");
                        String uuid = rs.getString("player_uuid");
                        String uName = rs.getString("username");
                        String wName = rs.getString("world_name");
                        double x = rs.getDouble("x");
                        double y = rs.getDouble("y");
                        double z = rs.getDouble("z");
                        float yaw = rs.getFloat("yaw");
                        float pitch = rs.getFloat("pitch");
                        String msg = rs.getString("death_message");
                        String cause = rs.getString("death_cause");
                        String killer = rs.getString("killer_name");
                        String killerType = rs.getString("killer_type");
                        int expLvl = rs.getInt("exp_level");
                        int totalExp = rs.getInt("total_exp");
                        int expDrop = rs.getInt("exp_dropped");
                        String snapId = rs.getString("inventory_snapshot_id");
                        ItemStack[] echest = serializer.deserializeItemArray(rs.getString("ender_chest_data"));
                        int itemCount = rs.getInt("item_count");
                        int dropCount = rs.getInt("drop_count");
                        Timestamp ts = rs.getTimestamp("timestamp");

                        list.add(new DeathRecord(deathId, uuid, uName, wName, x, y, z, yaw, pitch, msg, cause, killer, killerType,
                                expLvl, totalExp, expDrop, snapId, null, null, null, echest, itemCount, dropCount, ts.toInstant()));
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error querying death history for " + username, e);
            }
            return list;
        });
    }

    public CompletableFuture<List<ItemEventRecord>> queryItemEvents(String username, Instant before, int limit, int offset) {
        return CompletableFuture.supplyAsync(() -> {
            List<ItemEventRecord> list = new ArrayList<>();
            boolean hasBefore = before != null;
            String sql = hasBefore
                    ? "SELECT * FROM player_item_events WHERE (actor_name = ? OR affected_player_name = ?) AND timestamp <= ? ORDER BY id DESC LIMIT ? OFFSET ?"
                    : "SELECT * FROM player_item_events WHERE (actor_name = ? OR affected_player_name = ?) ORDER BY id DESC LIMIT ? OFFSET ?";
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, username);
                ps.setString(2, username);
                if (hasBefore) {
                    ps.setTimestamp(3, Timestamp.from(before));
                    ps.setInt(4, limit);
                    ps.setInt(5, offset);
                } else {
                    ps.setInt(3, limit);
                    ps.setInt(4, offset);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(new ItemEventRecord(
                                rs.getLong("id"),
                                rs.getString("event_type"),
                                rs.getString("actor_uuid"),
                                rs.getString("actor_name"),
                                rs.getString("affected_player_uuid"),
                                rs.getString("affected_player_name"),
                                rs.getString("material"),
                                rs.getInt("amount"),
                                rs.getString("item_display_name"),
                                rs.getString("item_serialized"),
                                rs.getString("world_name"),
                                rs.getDouble("x"),
                                rs.getDouble("y"),
                                rs.getDouble("z"),
                                rs.getString("correlation_id"),
                                rs.getString("details"),
                                rs.getTimestamp("timestamp").toInstant()
                        ));
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error querying item events", e);
            }
            return list;
        });
    }

    public CompletableFuture<List<ActionLogRecord>> queryActionLogs(String username, Instant before, int limit, int offset) {
        return CompletableFuture.supplyAsync(() -> {
            List<ActionLogRecord> list = new ArrayList<>();
            boolean hasBefore = before != null;
            String sql = hasBefore
                    ? "SELECT * FROM player_action_logs WHERE username = ? AND timestamp <= ? ORDER BY id DESC LIMIT ? OFFSET ?"
                    : "SELECT * FROM player_action_logs WHERE username = ? ORDER BY id DESC LIMIT ? OFFSET ?";
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, username);
                if (hasBefore) {
                    ps.setTimestamp(2, Timestamp.from(before));
                    ps.setInt(3, limit);
                    ps.setInt(4, offset);
                } else {
                    ps.setInt(2, limit);
                    ps.setInt(3, offset);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(new ActionLogRecord(
                                rs.getLong("id"),
                                rs.getString("event_type"),
                                rs.getString("player_uuid"),
                                rs.getString("username"),
                                rs.getString("world_name"),
                                rs.getDouble("x"),
                                rs.getDouble("y"),
                                rs.getDouble("z"),
                                rs.getString("target_info"),
                                rs.getString("details"),
                                rs.getTimestamp("timestamp").toInstant()
                        ));
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error querying action logs", e);
            }
            return list;
        });
    }

    public CompletableFuture<List<SessionRecord>> querySessionRecords(String username, int limit, int offset) {
        return CompletableFuture.supplyAsync(() -> {
            List<SessionRecord> list = new ArrayList<>();
            String sql = "SELECT * FROM player_sessions WHERE username = ? ORDER BY id DESC LIMIT ? OFFSET ?";
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, username);
                ps.setInt(2, limit);
                ps.setInt(3, offset);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Timestamp quitTs = rs.getTimestamp("quit_at");
                        list.add(new SessionRecord(
                                rs.getLong("id"),
                                rs.getString("session_id"),
                                rs.getString("player_uuid"),
                                rs.getString("username"),
                                rs.getString("platform"),
                                rs.getString("ip_address"),
                                rs.getTimestamp("joined_at").toInstant(),
                                quitTs != null ? quitTs.toInstant() : null,
                                rs.getLong("duration_seconds"),
                                rs.getString("quit_reason")
                        ));
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error querying sessions", e);
            }
            return list;
        });
    }

    private InventorySnapshotRecord mapInventorySnapshot(ResultSet rs) throws SQLException {
        String snapId = rs.getString("snapshot_id");
        String uuid = rs.getString("player_uuid");
        String username = rs.getString("username");
        String world = rs.getString("world_name");
        double x = rs.getDouble("x");
        double y = rs.getDouble("y");
        double z = rs.getDouble("z");
        String gm = rs.getString("gamemode");
        double hp = rs.getDouble("health");
        int hunger = rs.getInt("hunger");
        float sat = rs.getFloat("saturation");
        int expLvl = rs.getInt("exp_level");
        int totalExp = rs.getInt("total_exp");
        int slot = rs.getInt("selected_slot");
        String reason = rs.getString("snapshot_reason");
        ItemStack[] inv = serializer.deserializeItemArray(rs.getString("inventory_data"));
        ItemStack[] armor = serializer.deserializeItemArray(rs.getString("armor_data"));
        ItemStack offhand = serializer.deserializeItem(rs.getString("offhand_data"));
        ItemStack[] echest = serializer.deserializeItemArray(rs.getString("ender_chest_data"));
        ItemStack cursor = serializer.deserializeItem(rs.getString("cursor_data"));
        String effects = rs.getString("potion_effects");
        String corr = rs.getString("correlation_id");
        int dur = rs.getInt("duration_seconds");
        Timestamp startTs = rs.getTimestamp("start_timestamp");
        Timestamp endTs = rs.getTimestamp("timestamp");

        return new InventorySnapshotRecord(snapId, uuid, username, world, x, y, z, gm, hp, hunger, sat, expLvl, totalExp, slot, reason,
                inv, armor, offhand, echest, cursor, effects, corr, dur,
                startTs != null ? startTs.toInstant() : endTs.toInstant().minusSeconds(dur), endTs.toInstant());
    }

    public void clearPlayerTracking(UUID uuid) {
        activeInventoryMap.remove(uuid);
        activePositionMap.remove(uuid);
    }

    private static class ActiveInventoryState {
        final String snapshotId;
        final String stateHash;
        final Instant startTime;
        int durationSeconds;
        Instant lastActive;

        ActiveInventoryState(String snapshotId, String stateHash, Instant startTime, int durationSeconds, Instant lastActive) {
            this.snapshotId = snapshotId;
            this.stateHash = stateHash;
            this.startTime = startTime;
            this.durationSeconds = durationSeconds;
            this.lastActive = lastActive;
        }
    }

    private static class ActivePositionState {
        final String worldName;
        final int blockX;
        final int blockY;
        final int blockZ;
        int durationSeconds;
        Instant lastActive;
        long rowId;

        ActivePositionState(String worldName, int blockX, int blockY, int blockZ, int durationSeconds, Instant lastActive, long rowId) {
            this.worldName = worldName;
            this.blockX = blockX;
            this.blockY = blockY;
            this.blockZ = blockZ;
            this.durationSeconds = durationSeconds;
            this.lastActive = lastActive;
            this.rowId = rowId;
        }
    }

    public static class PendingOfflineModification {
        private final String playerUuid;
        private final String username;
        private String inventoryData;
        private String armorData;
        private String offhandData;
        private String enderChestData;
        private String modifiedBy;
        private Instant modifiedAt;

        public PendingOfflineModification(String playerUuid, String username, String inventoryData,
                                          String armorData, String offhandData, String enderChestData,
                                          String modifiedBy, Instant modifiedAt) {
            this.playerUuid = playerUuid;
            this.username = username;
            this.inventoryData = inventoryData;
            this.armorData = armorData;
            this.offhandData = offhandData;
            this.enderChestData = enderChestData;
            this.modifiedBy = modifiedBy;
            this.modifiedAt = modifiedAt;
        }

        public String getPlayerUuid() { return playerUuid; }
        public String getUsername() { return username; }
        public String getInventoryData() { return inventoryData; }
        public String getArmorData() { return armorData; }
        public String getOffhandData() { return offhandData; }
        public String getEnderChestData() { return enderChestData; }
        public String getModifiedBy() { return modifiedBy; }
        public Instant getModifiedAt() { return modifiedAt; }

        public void setInventoryData(String inventoryData) { this.inventoryData = inventoryData; }
        public void setArmorData(String armorData) { this.armorData = armorData; }
        public void setOffhandData(String offhandData) { this.offhandData = offhandData; }
        public void setEnderChestData(String enderChestData) { this.enderChestData = enderChestData; }
        public void setModifiedBy(String modifiedBy) { this.modifiedBy = modifiedBy; }
        public void setModifiedAt(Instant modifiedAt) { this.modifiedAt = modifiedAt; }
    }
}
